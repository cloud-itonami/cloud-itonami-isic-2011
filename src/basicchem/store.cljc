(ns basicchem.store
  "SSoT for the basic-chemicals-manufacturing actor, behind a `Store`
  protocol so the backend is a swap, not a rewrite -- the same seam
  every prior `cloud-itonami-isic-*` actor in this fleet uses
  (`pressureequip.store` in cloud-itonami-isic-2813 is the direct
  precedent this ns mirrors):

    - `MemStore`     -- atom of EDN. The deterministic default for
                        dev/tests/demo (no deps).
    - `DatomicStore` -- backed by `langchain.db`, a Datomic-API-compatible
                        EAV store (datalog q / pull / upsert). Pure `.cljc`,
                        so it runs offline AND can be pointed at a real
                        Datomic Local or a kotoba-server pod by swapping
                        `langchain.db`'s `:db-api` (see langchain.kotoba-db).

  Both implement the same protocol and pass the same contract
  (test/basicchem/store_contract_test.clj), which is the whole point:
  the actor, the Chemical Safety Governor and the audit ledger never
  know which SSoT they run on.

  This actor has TWO actuation-like events (logging a production
  batch, releasing a batch to market) acting on the SAME entity (a
  batch), each with its OWN history collection, jurisdiction-scoped
  sequence counter and dedicated double-actuation-guard boolean
  (`:batch-logged?`/`:batch-released?`, never a `:status` value) --
  the same discipline every prior sibling governor's guards establish
  (informed by cloud-itonami-isic-6492's status-lifecycle bug,
  ADR-2607071320). `:schedule-maintenance`'s own history is NOT
  jurisdiction-scoped (routine equipment upkeep carries no
  jurisdiction-specific legal-filing basis) -- its 'next sequence' is
  simply the count of maintenance records logged so far, a global
  monotonic counter needing no separate stored counter entity.

  The ledger stays append-only on every backend: 'which batch was
  screened for an unresolved process-safety concern, which batch was
  logged, which batch was released to market, on what jurisdictional
  basis, approved by whom' is always a query over an immutable log --
  the audit trail a community trusting a chemical manufacturer needs,
  and the evidence a manufacturer needs if a release decision is later
  disputed."
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [basicchem.registry :as registry]
            [langchain.db :as d]))

(defprotocol Store
  (batch [s id])
  (all-batches [s])
  (safety-screen-of [s batch-id] "committed process-safety-concern screening verdict for a batch, or nil")
  (ledger [s])
  (log-history [s] "the append-only production-batch-log history (basicchem.registry drafts)")
  (release-history [s] "the append-only batch-release history (basicchem.registry drafts)")
  (maintenance-history [s] "the append-only maintenance-schedule history (basicchem.registry drafts)")
  (next-log-sequence [s jurisdiction] "next log-number sequence for a jurisdiction")
  (next-release-sequence [s jurisdiction] "next release-number sequence for a jurisdiction")
  (next-maintenance-sequence [s] "next maintenance-number sequence -- global, not jurisdiction-scoped")
  (batch-already-logged? [s batch-id] "has this batch already been logged as produced?")
  (batch-already-released? [s batch-id] "has this batch already been released to market?")
  (commit-record! [s record] "apply a committed op's record to the SSoT")
  (append-ledger! [s fact]   "append one immutable decision fact")
  (with-batches [s batches] "replace/seed the batch directory (map id->batch)"))

;; ----------------------------- demo data -----------------------------

(defn demo-data
  "A small, self-contained batch set covering both actuation-like
  lifecycles (logging a batch, releasing a batch to market) so the
  actor + tests run offline. Reaction-temperature fields are
  ILLUSTRATIVE placeholders (a plausible exothermic blending/charging
  process window for a refrigerant-blend batch), not a real vendor
  process specification -- the same fidelity/scope disclaimer
  `pressureequip.scene` applies to its own parametric dimensions."
  []
  {:batches
   {"batch-1" {:id "batch-1" :batch-name "R-448A ブレンドバッチ #1 (JPN, 清浄)"
               :product-type-id :product/r448a-refrigerant-blend
               :reaction-temp-actual-c 52.0 :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0
               :batch-logged? false :batch-released? false
               :jurisdiction "JPN" :status :intake}
    "batch-2" {:id "batch-2" :batch-name "汎用溶剤バッチ #2 (未登録法域)"
               :reaction-temp-actual-c 50.0 :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0
               :batch-logged? false :batch-released? false
               :jurisdiction "ATL" :status :intake}
    "batch-3" {:id "batch-3" :batch-name "R-448A ブレンドバッチ #3 (JPN, 反応温度異常)"
               :product-type-id :product/r448a-refrigerant-blend
               :reaction-temp-actual-c 85.0 :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0
               :batch-logged? false :batch-released? false
               :jurisdiction "JPN" :status :intake}
    "batch-4" {:id "batch-4" :batch-name "R-448A ブレンドバッチ #4 (JPN, 安全懸念デモ用)"
               :product-type-id :product/r448a-refrigerant-blend
               :reaction-temp-actual-c 55.0 :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0
               :batch-logged? false :batch-released? false
               :jurisdiction "JPN" :status :intake}}})

;; ----------------------------- shared commit logic -----------------------------

(defn- log-batch!
  "Backend-agnostic draft-construction for `:batch/log` -- looks up
  the batch (AFTER the caller has already merged any upsert fields)
  via the protocol and drafts the production-batch-log record.
  Returns {:result .. :batch-patch ..} for the caller to persist."
  [s batch-id]
  (let [a (batch s batch-id)
        seq-n (next-log-sequence s (:jurisdiction a))
        result (registry/register-batch-log batch-id (:jurisdiction a) seq-n)]
    {:result result
     :batch-patch {:batch-logged? true :log-number (get result "log_number")}}))

(defn- release-batch!
  "Backend-agnostic draft-construction for `:batch/release` -- looks
  up the batch via the protocol and drafts the batch-release record
  (optionally embedding `handoff`, the superproject `:handoff` shared
  shape). Returns {:result .. :batch-patch ..} for the caller to
  persist."
  [s batch-id handoff]
  (let [a (batch s batch-id)
        seq-n (next-release-sequence s (:jurisdiction a))
        result (registry/register-batch-release batch-id (:jurisdiction a) seq-n handoff)]
    {:result result
     :batch-patch {:batch-released? true :release-number (get result "release_number")}}))

(defn- schedule-maintenance!
  "Backend-agnostic draft-construction for `:maintenance/schedule` --
  purely additive, no batch lookup (`maintenance-id` doubles as the
  new schedule record's own id, the same 'subject IS the new entity id'
  convention `pressureequip.governor`'s `:register-equipment-asset`/
  `:register-part-receipt` establish)."
  [s maintenance-id equipment-id scheduled-at-iso description]
  (let [seq-n (next-maintenance-sequence s)]
    {:result (registry/register-maintenance-schedule maintenance-id equipment-id scheduled-at-iso description seq-n)}))

;; ----------------------------- MemStore (default) -----------------------------

(defrecord MemStore [a]
  Store
  (batch [_ id] (get-in @a [:batches id]))
  (all-batches [_] (sort-by :id (vals (:batches @a))))
  (safety-screen-of [_ id] (get-in @a [:safety-screens id]))
  (ledger [_] (:ledger @a))
  (log-history [_] (:logs @a))
  (release-history [_] (:releases @a))
  (maintenance-history [_] (:maintenances @a))
  (next-log-sequence [_ jurisdiction] (get-in @a [:log-sequences jurisdiction] 0))
  (next-release-sequence [_ jurisdiction] (get-in @a [:release-sequences jurisdiction] 0))
  (next-maintenance-sequence [_] (count (:maintenances @a)))
  (batch-already-logged? [_ batch-id] (boolean (get-in @a [:batches batch-id :batch-logged?])))
  (batch-already-released? [_ batch-id] (boolean (get-in @a [:batches batch-id :batch-released?])))
  (commit-record! [s {:keys [effect path value]}]
    (case effect
      :batch/log
      (let [batch-id (first path)]
        (swap! a update-in [:batches batch-id] merge (assoc (dissoc value :id) :id batch-id))
        (let [{:keys [result batch-patch]} (log-batch! s batch-id)
              jurisdiction (:jurisdiction (batch s batch-id))]
          (swap! a (fn [state]
                     (-> state
                         (update-in [:log-sequences jurisdiction] (fnil inc 0))
                         (update-in [:batches batch-id] merge batch-patch)
                         (update :logs registry/append result))))
          result))

      :batch/release
      (let [batch-id (first path)
            {:keys [result batch-patch]} (release-batch! s batch-id (:handoff value))
            jurisdiction (:jurisdiction (batch s batch-id))]
        (swap! a (fn [state]
                   (-> state
                       (update-in [:release-sequences jurisdiction] (fnil inc 0))
                       (update-in [:batches batch-id] merge batch-patch)
                       (update :releases registry/append result))))
        result)

      :safety-concern/flag
      (swap! a assoc-in [:safety-screens (first path)] value)

      :maintenance/schedule
      (let [maintenance-id (first path)
            {:keys [result]} (schedule-maintenance! s maintenance-id
                                                     (:maintenance/equipment-id value)
                                                     (:maintenance/scheduled-at-iso value)
                                                     (:maintenance/description value))]
        (swap! a update :maintenances registry/append result)
        result)
      nil)
    s)
  (append-ledger! [_ fact] (swap! a update :ledger conj fact) fact)
  (with-batches [s batches] (when (seq batches) (swap! a assoc :batches batches)) s))

(defn seed-db
  "A MemStore seeded with the demo batch set. The deterministic
  default."
  []
  (->MemStore (atom (assoc (demo-data)
                           :safety-screens {} :ledger [] :log-sequences {}
                           :logs [] :release-sequences {} :releases []
                           :maintenances []))))

;; ----------------------------- DatomicStore (langchain.db) -----------------------------

(def ^:private schema
  "DataScript/Datomic-style schema: only constraint attrs are declared.
  Map/compound values (safety-screen payloads, ledger facts, log/
  release records) are stored as EDN strings so `langchain.db` doesn't
  expand them into sub-entities -- the same convention every sibling
  actor's store uses."
  {:batch/id                          {:db/unique :db.unique/identity}
   :safety-screen/batch-id            {:db/unique :db.unique/identity}
   :ledger/seq                        {:db/unique :db.unique/identity}
   :log/seq                           {:db/unique :db.unique/identity}
   :release/seq                       {:db/unique :db.unique/identity}
   :maintenance/seq                   {:db/unique :db.unique/identity}
   :log-sequence/jurisdiction         {:db/unique :db.unique/identity}
   :release-sequence/jurisdiction     {:db/unique :db.unique/identity}})

(defn- enc [v] (pr-str v))
(defn- dec* [s] (when s (edn/read-string s)))

(defn- batch->tx [{:keys [id batch-name product-type-id
                          reaction-temp-actual-c reaction-temp-min-c reaction-temp-max-c
                          batch-logged? batch-released?
                          jurisdiction status log-number release-number
                          evidence-checklist spec-basis legal-basis]}]
  (cond-> {:batch/id id}
    batch-name                              (assoc :batch/batch-name batch-name)
    product-type-id                         (assoc :batch/product-type-id product-type-id)
    reaction-temp-actual-c                  (assoc :batch/reaction-temp-actual-c reaction-temp-actual-c)
    reaction-temp-min-c                     (assoc :batch/reaction-temp-min-c reaction-temp-min-c)
    reaction-temp-max-c                     (assoc :batch/reaction-temp-max-c reaction-temp-max-c)
    (some? batch-logged?)                   (assoc :batch/batch-logged? batch-logged?)
    (some? batch-released?)                 (assoc :batch/batch-released? batch-released?)
    jurisdiction                            (assoc :batch/jurisdiction jurisdiction)
    status                                  (assoc :batch/status status)
    log-number                              (assoc :batch/log-number log-number)
    release-number                          (assoc :batch/release-number release-number)
    evidence-checklist                      (assoc :batch/evidence-checklist (enc evidence-checklist))
    spec-basis                              (assoc :batch/spec-basis spec-basis)
    legal-basis                             (assoc :batch/legal-basis legal-basis)))

(def ^:private batch-pull
  [:batch/id :batch/batch-name :batch/product-type-id
   :batch/reaction-temp-actual-c :batch/reaction-temp-min-c :batch/reaction-temp-max-c
   :batch/batch-logged? :batch/batch-released?
   :batch/jurisdiction :batch/status :batch/log-number :batch/release-number
   :batch/evidence-checklist :batch/spec-basis :batch/legal-basis])

(defn- pull->batch [m]
  (when (:batch/id m)
    {:id (:batch/id m) :batch-name (:batch/batch-name m)
     :product-type-id (:batch/product-type-id m)
     :reaction-temp-actual-c (:batch/reaction-temp-actual-c m)
     :reaction-temp-min-c (:batch/reaction-temp-min-c m)
     :reaction-temp-max-c (:batch/reaction-temp-max-c m)
     :batch-logged? (boolean (:batch/batch-logged? m))
     :batch-released? (boolean (:batch/batch-released? m))
     :jurisdiction (:batch/jurisdiction m) :status (:batch/status m)
     :log-number (:batch/log-number m) :release-number (:batch/release-number m)
     :evidence-checklist (dec* (:batch/evidence-checklist m))
     :spec-basis (:batch/spec-basis m) :legal-basis (:batch/legal-basis m)}))

(defrecord DatomicStore [conn]
  Store
  (batch [_ id]
    (pull->batch (d/pull (d/db conn) batch-pull [:batch/id id])))
  (all-batches [_]
    (->> (d/q '[:find [?id ...] :where [?e :batch/id ?id]] (d/db conn))
         (map #(pull->batch (d/pull (d/db conn) batch-pull [:batch/id %])))
         (sort-by :id)))
  (safety-screen-of [_ id]
    (dec* (d/q '[:find ?p . :in $ ?bid
                :where [?k :safety-screen/batch-id ?bid] [?k :safety-screen/payload ?p]]
              (d/db conn) id)))
  (ledger [_]
    (->> (d/q '[:find ?s ?f :where [?e :ledger/seq ?s] [?e :ledger/fact ?f]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (log-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :log/seq ?s] [?e :log/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (release-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :release/seq ?s] [?e :release/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (maintenance-history [_]
    (->> (d/q '[:find ?s ?r :where [?e :maintenance/seq ?s] [?e :maintenance/record ?r]] (d/db conn))
         (sort-by first)
         (mapv (comp dec* second))))
  (next-log-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :log-sequence/jurisdiction ?j] [?e :log-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-release-sequence [_ jurisdiction]
    (or (d/q '[:find ?n . :in $ ?j
              :where [?e :release-sequence/jurisdiction ?j] [?e :release-sequence/next ?n]]
            (d/db conn) jurisdiction)
        0))
  (next-maintenance-sequence [s] (count (maintenance-history s)))
  (batch-already-logged? [s batch-id]
    (boolean (:batch-logged? (batch s batch-id))))
  (batch-already-released? [s batch-id]
    (boolean (:batch-released? (batch s batch-id))))
  (commit-record! [s {:keys [effect path value]}]
    (case effect
      :batch/log
      (let [batch-id (first path)]
        (d/transact! conn [(batch->tx (assoc (dissoc value :id) :id batch-id))])
        (let [{:keys [result batch-patch]} (log-batch! s batch-id)
              jurisdiction (:jurisdiction (batch s batch-id))
              next-n (inc (next-log-sequence s jurisdiction))]
          (d/transact! conn
                       [(batch->tx (assoc batch-patch :id batch-id))
                        {:log-sequence/jurisdiction jurisdiction :log-sequence/next next-n}
                        {:log/seq (count (log-history s)) :log/record (enc (get result "record"))}])
          result))

      :batch/release
      (let [batch-id (first path)
            {:keys [result batch-patch]} (release-batch! s batch-id (:handoff value))
            jurisdiction (:jurisdiction (batch s batch-id))
            next-n (inc (next-release-sequence s jurisdiction))]
        (d/transact! conn
                     [(batch->tx (assoc batch-patch :id batch-id))
                      {:release-sequence/jurisdiction jurisdiction :release-sequence/next next-n}
                      {:release/seq (count (release-history s)) :release/record (enc (get result "record"))}])
        result)

      :safety-concern/flag
      (d/transact! conn [{:safety-screen/batch-id (first path) :safety-screen/payload (enc value)}])

      :maintenance/schedule
      (let [maintenance-id (first path)
            {:keys [result]} (schedule-maintenance! s maintenance-id
                                                     (:maintenance/equipment-id value)
                                                     (:maintenance/scheduled-at-iso value)
                                                     (:maintenance/description value))]
        (d/transact! conn
                     [{:maintenance/seq (count (maintenance-history s)) :maintenance/record (enc (get result "record"))}])
        result)
      nil)
    s)
  (append-ledger! [s fact]
    (d/transact! conn [{:ledger/seq (count (ledger s)) :ledger/fact (enc fact)}])
    fact)
  (with-batches [s batches]
    (when (seq batches) (d/transact! conn (mapv batch->tx (vals batches)))) s))

(defn datomic-store
  "A DatomicStore (langchain.db backend) seeded from `data`
  ({:batches ..}); empty when omitted."
  ([] (datomic-store {}))
  ([{:keys [batches]}]
   (let [s (->DatomicStore (d/create-conn schema))]
     (with-batches s batches))))

(defn datomic-seed-db
  "A DatomicStore seeded with the demo batch set -- the Datomic-backed
  analog of `seed-db`, used to prove protocol parity."
  []
  (datomic-store (demo-data)))
