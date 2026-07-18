(ns basicchem.basicchemadvisor
  "Basic Chemicals Advisor client -- the *contained intelligence node*
  for the basic-chemicals-manufacturing actor.

  It drafts a per-jurisdiction production-batch-log record (citing the
  jurisdiction's required evidence checklist), drafts the batch-release
  action, drafts a process-safety-concern flag, and drafts a routine
  maintenance schedule. CRITICAL: it is a smart-but-untrusted advisor.
  It returns a *proposal* (with a rationale + the fields it cited),
  never a committed record or a real market release/reactor-parameter
  change. Every output is censored downstream by `basicchem.governor`
  before anything touches the SSoT, and `:log-production-batch`/
  `:release-batch`/`:flag-safety-concern` proposals NEVER auto-commit
  at any phase -- see README `Actuation`.

  Like every sibling actor's advisor, this is a deterministic mock so
  the actor graph runs offline and the governor contract is exercised
  end-to-end. In production this calls a real LLM (kotoba-llm or
  equivalent) with the same proposal shape.

  Proposal shape (all kinds):
    {:summary    str            ; human-facing draft / finding
     :rationale  str            ; why -- SCANNED by the spec-basis gate
     :cites      [kw|str ..]    ; facts/sources the LLM used -- SCANNED too
     :effect     kw             ; how a commit would mutate the SSoT
     :stake      kw|nil         ; :log-production-batch | :release-batch | :flag-safety-concern | nil
     :confidence 0..1}"
  (:require #?(:clj  [clojure.edn :as edn]
               :cljs [cljs.reader :as edn])
            [clojure.string :as str]
            [basicchem.facts :as facts]
            [basicchem.registry :as registry]
            [basicchem.store :as store]
            [langchain.model :as model]))

(defn- propose-log-production-batch
  "Draft a PRODUCTION-BATCH-LOG record -- recording that a batch was
  produced, under a jurisdiction's official evidence checklist. The
  advisor reads the batch's OWN already-recorded `:jurisdiction`
  (unless the request explicitly overrides it, or `:no-spec?` injects
  the failure mode we must defend against: proposing a checklist for a
  jurisdiction with NO official spec-basis in `basicchem.facts` -- the
  Chemical Safety Governor must reject this, never invent a
  jurisdiction's requirements). It does NOT invent the batch's own
  reaction-temperature figures -- those stay whatever the batch record
  already carries (or nil, for a genuinely fresh batch); the log op
  only sets identity/jurisdiction/evidence-checklist fields. ALWAYS
  `:stake :log-production-batch` -- always human sign-off, per README."
  [db {:keys [subject jurisdiction no-spec?]}]
  (let [a (store/batch db subject)
        iso3 (cond no-spec? "ATL" jurisdiction jurisdiction :else (:jurisdiction a))
        sb (facts/spec-basis iso3)]
    (if (nil? sb)
      {:summary    (str subject " バッチ記録: " iso3 " の公式spec-basisが見つかりません")
       :rationale  "basicchem.facts に未登録の法域。要件を推測で作らない。"
       :cites      []
       :effect     :batch/log
       :value      {:jurisdiction iso3 :evidence-checklist [] :spec-basis nil}
       :stake      :log-production-batch
       :confidence 0.9}
      {:summary    (str subject " バッチ記録: " iso3 " (" (:owner-authority sb) ") 向け必要書類 "
                        (count (:required-evidence sb)) " 件を充足として記録")
       :rationale  (str "公式ソース: " (:provenance sb) " / 法的根拠: " (:legal-basis sb))
       :cites      [(:legal-basis sb) (:provenance sb)]
       :effect     :batch/log
       :value      {:jurisdiction iso3
                    :evidence-checklist (:required-evidence sb)
                    :spec-basis (:provenance sb)
                    :legal-basis (:legal-basis sb)}
       :stake      :log-production-batch
       :confidence 0.95})))

(defn- propose-release-batch
  "Draft the actual BATCH-RELEASE action -- releasing a chemical batch
  to market. ALWAYS `:stake :release-batch` -- this is a REAL-WORLD
  safety-critical act, never a draft the actor may auto-run. See
  README `Actuation`: no phase ever adds this op to a phase's `:auto`
  set (`basicchem.phase`); the governor also always escalates on
  `:release-batch`. Two independent layers agree, deliberately.

  `handoff` (optional) is the superproject `:handoff` shared shape
  (ADR-2607177600/ADR-2800000500) -- the advisor only echoes/normalizes
  the request's own `:handoff`, it does not invent a downstream
  consignee. `basicchem.governor` INDEPENDENTLY re-verifies its
  required-field presence before anything commits."
  [db {:keys [subject handoff]}]
  (let [a (store/batch db subject)]
    {:summary    (str subject " 向け市場出荷判定提案"
                      (when a (str " (batch=" (:batch-name a) ")")))
     :rationale  (if a
                   (str "reaction-temp-actual-c=" (:reaction-temp-actual-c a)
                        " spec=[" (:reaction-temp-min-c a) "," (:reaction-temp-max-c a) "]")
                   "バッチ記録が見つかりません")
     :cites      (if a [subject] [])
     :effect     :batch/release
     :value      (cond-> {:batch-id subject} handoff (assoc :handoff handoff))
     :stake      :release-batch
     :confidence (if (and a (not (registry/reaction-temp-out-of-range? a))) 0.9 0.3)}))

(defn- propose-flag-safety-concern
  "Draft a PROCESS-SAFETY-CONCERN flag -- e.g. an abnormal reaction
  temperature/pressure excursion, a toxic-substance leak risk, etc.
  Unlike a sibling actor's own defect-screening op (e.g.
  `pressureequip.pressureequipadvisor/screen-pressure-test-defect`,
  which HARD-holds immediately on its own finding, never reaching a
  human), THIS op ALWAYS escalates regardless of severity -- see
  `basicchem.governor` ns docstring for why: the observation itself
  comes from the requester (a human/engineer reporting a real hazard),
  not from this actor autonomously re-deriving a verdict from ground
  truth, so a human must always review the report before it becomes a
  permanent SSoT fact. The advisor only echoes/normalizes the
  request's own `:description`/`:severity`/`:verdict`, it does not
  invent them. ALWAYS `:stake :flag-safety-concern`."
  [_db {:keys [subject description severity verdict]}]
  (let [present? (every? some? [description severity])]
    {:summary    (str subject " 向け工程安全懸念フラグ提案"
                      (when severity (str " (severity=" severity ")")))
     :rationale  (if present?
                   (str "reported: " description)
                   "必須フィールド(description・severity)が不足")
     :cites      (if present? [subject] [])
     :effect     :safety-concern/flag
     :value      {:safety-concern/batch-id subject
                  :safety-concern/description description
                  :safety-concern/severity severity
                  :safety-concern/verdict (or verdict :unresolved)}
     :stake      :flag-safety-concern
     :confidence (if present? 0.9 0.3)}))

(defn- propose-schedule-maintenance
  "Draft a ROUTINE MAINTENANCE-SCHEDULE action for a reactor/piece of
  equipment. Unlike the other three ops, `:stake` is `nil` -- routine
  maintenance scheduling carries no direct capital/product-safety risk
  (the SAME 'no capital risk' framing `pressureequip.phase` gives
  `:unit/intake`), so this is the one op this actor's phase-3 `:auto`
  set may auto-commit when the governor is clean. `subject` doubles
  as the new schedule record's own id (the SAME 'subject IS the new
  entity id' convention `pressureequip.governor`'s
  `:register-equipment-asset`/`:register-part-receipt` establish)."
  [_db {:keys [subject equipment-id scheduled-at-iso description]}]
  (let [present? (every? some? [equipment-id scheduled-at-iso])]
    {:summary    (str subject " 設備保守スケジュール提案"
                      (when equipment-id (str " (equipment=" equipment-id ")")))
     :rationale  (if present?
                   (str equipment-id " の保守を " scheduled-at-iso " に予定")
                   "必須フィールド(equipment-id・scheduled-at-iso)が不足")
     :cites      (if present? [subject equipment-id] [])
     :effect     :maintenance/schedule
     :value      (cond-> {:maintenance/equipment-id equipment-id :maintenance/scheduled-at-iso scheduled-at-iso}
                   description (assoc :maintenance/description description))
     :stake      nil
     :confidence (if present? 0.95 0.3)}))

(defn infer
  "Route a request to the right proposal generator.
  request: {:op kw :subject id ...op-specific...}"
  [db {:keys [op] :as request}]
  (case op
    :log-production-batch  (propose-log-production-batch db request)
    :release-batch          (propose-release-batch db request)
    :flag-safety-concern     (propose-flag-safety-concern db request)
    :schedule-maintenance    (propose-schedule-maintenance db request)
    {:summary "未対応の操作" :rationale (str op) :cites []
     :effect :noop :stake nil :confidence 0.0}))

;; ----------------------------- Advisor protocol -----------------------------

(defprotocol Advisor
  (-advise [advisor store request] "store + request -> proposal map"))

(defn mock-advisor
  "The deterministic advisor (the `infer` logic above). Default everywhere."
  [] (reify Advisor (-advise [_ st req] (infer st req))))

(def ^:private system-prompt
  (str "あなたは基礎化学品製造工場のバッチ記録・市場出荷判定エージェントの助言者です。"
       "与えられた事実のみに基づき、提案を1つだけEDNマップで返します。説明や前置きは"
       "一切書かず、EDNだけを出力します。\n"
       "キー: :summary(人向けドラフト) :rationale(根拠/必ず事実から) "
       ":cites(使った事実キーのベクタ) "
       ":effect(:batch/log|:batch/release|:safety-concern/flag|:maintenance/schedule) "
       ":stake(:log-production-batch か :release-batch か :flag-safety-concern か nil) "
       ":confidence(0..1)。\n"
       "重要: 登録されていない法域の要件を絶対に創作してはいけません。"
       "spec-basisが無い場合は :cites を空にし confidence を上げないこと。"))

(defn- facts-for [st {:keys [op subject]}]
  (case op
    :log-production-batch  {:batch (store/batch st subject)}
    :release-batch          {:batch (store/batch st subject)}
    :flag-safety-concern     {:batch (store/batch st subject) :safety-screen (store/safety-screen-of st subject)}
    {:batch (store/batch st subject)}))

(defn- parse-proposal
  "Parse the model's EDN proposal defensively. Any parse/shape failure
  yields a safe low-confidence noop so the Chemical Safety Governor
  escalates/holds -- an LLM hiccup can never auto-release a batch or
  auto-change a safety-critical reaction parameter."
  [content]
  (let [p (try (edn/read-string (str/trim (str content)))
               (catch #?(:clj Exception :cljs :default) _ nil))]
    (if (map? p)
      (-> p
          (update :cites #(vec (or % [])))
          (update :confidence #(if (number? %) (double %) 0.0))
          (update :effect #(or % :noop)))
      {:summary "LLM応答を解釈できませんでした" :rationale (str content)
       :cites [] :effect :noop :stake nil :confidence 0.0})))

(defn llm-advisor
  "An advisor backed by a `langchain.model/ChatModel` (real inference)."
  ([chat-model] (llm-advisor chat-model {}))
  ([chat-model gen-opts]
   (reify Advisor
     (-advise [_ st req]
       (let [msgs [{:role :system :content system-prompt}
                   {:role :user :content (str "操作: " (:op req)
                                              "\n対象: " (:subject req)
                                              "\n事実: " (pr-str (facts-for st req)))}]
             resp (model/-generate chat-model msgs gen-opts)]
         (parse-proposal (:content resp)))))))

(defn trace
  "Decision-grounded audit record -- persisted to the :audit channel."
  [request proposal]
  {:t          :basicchemadvisor-proposal
   :op         (:op request)
   :subject    (:subject request)
   :summary    (:summary proposal)
   :rationale  (:rationale proposal)
   :cites      (:cites proposal)
   :confidence (:confidence proposal)})
