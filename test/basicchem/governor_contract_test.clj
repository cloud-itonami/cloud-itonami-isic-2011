(ns basicchem.governor-contract-test
  "The governor contract as executable tests -- the basic-chemicals-
  manufacturer analog of `pressureequip.governor-contract-test`
  (cloud-itonami-isic-2813). The single invariant under test:

    Basic Chemicals Advisor never logs a batch or releases a batch the
    Chemical Safety Governor would reject, `:log-production-batch`/
    `:release-batch`/`:flag-safety-concern` NEVER auto-commit at any
    phase, `:schedule-maintenance` (no capital/product risk) MAY
    auto-commit when clean, and every decision (commit OR hold) leaves
    exactly one ledger fact."
  (:require [clojure.test :refer [deftest is testing]]
            [langgraph.graph :as g]
            [basicchem.store :as store]
            [basicchem.operation :as op]))

(defn- fresh []
  (let [db (store/seed-db)]
    [db (op/build db)]))

(def operator {:actor-id "op-1" :actor-role :chemical-safety-engineer :phase 3})

(defn- exec-op [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn- log!
  "Walks `subject` through log -> approve, leaving it `:batch-logged?
  true` with its evidence-checklist on file. Uses distinct thread-ids
  per call site by suffixing `tid-prefix`."
  [actor tid-prefix subject]
  (exec-op actor (str tid-prefix "-log") {:op :log-production-batch :subject subject} operator)
  (approve! actor (str tid-prefix "-log")))

(deftest clean-log-then-approve-commits
  (let [[db actor] (fresh)
        res (exec-op actor "t1" {:op :log-production-batch :subject "batch-1"} operator)]
    (is (= :interrupted (:status res)) "log-production-batch always needs human approval, even when clean")
    (let [r2 (approve! actor "t1")]
      (is (= :commit (get-in r2 [:state :disposition])))
      (is (true? (:batch-logged? (store/batch db "batch-1"))))
      (is (= 1 (count (store/log-history db)))))))

(deftest fabricated-jurisdiction-is-held
  (testing "a log-production-batch proposal with no official spec-basis -> HOLD, never reaches a human"
    (let [[db actor] (fresh)
          res (exec-op actor "t2" {:op :log-production-batch :subject "batch-2" :no-spec? true} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:no-spec-basis} (-> (store/ledger db) first :basis)))
      (is (false? (boolean (:batch-logged? (store/batch db "batch-2"))))))))

(deftest release-before-log-is-held
  (testing "release-batch before any log-production-batch -> HOLD (batch-not-logged, and evidence incomplete)"
    (let [[db actor] (fresh)
          res (exec-op actor "t3" {:op :release-batch :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:batch-not-logged} (-> (store/ledger db) first :basis)))
      (is (some #{:evidence-incomplete} (-> (store/ledger db) first :basis))))))

(deftest release-with-reaction-temp-out-of-range-is-held
  (testing "a batch whose own reaction temperature falls outside its own spec bounds -> HOLD"
    (let [[db actor] (fresh)
          _ (log! actor "t4pre" "batch-3")
          res (exec-op actor "t4" {:op :release-batch :subject "batch-3"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:reaction-temp-out-of-range} (-> (store/ledger db) last :basis)))
      (is (empty? (store/release-history db))))))

(deftest release-always-escalates-then-human-decides
  (testing "a clean, fully-logged, in-spec batch still ALWAYS interrupts for human approval -- release-batch is never auto"
    (let [[db actor] (fresh)
          _ (log! actor "t5pre" "batch-1")
          r1 (exec-op actor "t5" {:op :release-batch :subject "batch-1"} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval even when governor-clean")
      (testing "approve -> commit, release record drafted"
        (let [r2 (approve! actor "t5")]
          (is (= :commit (get-in r2 [:state :disposition])))
          (is (true? (:batch-released? (store/batch db "batch-1"))))
          (is (= 1 (count (store/release-history db)))))))))

(deftest double-log-is-held
  (testing "logging the same batch twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (log! actor "t6pre" "batch-1")
          res (exec-op actor "t6" {:op :log-production-batch :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-logged} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/log-history db)))))))

(deftest double-release-is-held
  (testing "releasing the same batch to market twice -> HOLD on the second attempt"
    (let [[db actor] (fresh)
          _ (log! actor "t7pre" "batch-1")
          _ (exec-op actor "t7a" {:op :release-batch :subject "batch-1"} operator)
          _ (approve! actor "t7a")
          res (exec-op actor "t7" {:op :release-batch :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:already-released} (-> (store/ledger db) last :basis)))
      (is (= 1 (count (store/release-history db)))))))

(deftest flag-safety-concern-always-escalates-even-when-unresolved
  (testing "unlike a defect-screening op, flag-safety-concern ALWAYS escalates -- it never HARD-holds on its own unresolved finding"
    (let [[db actor] (fresh)
          r1 (exec-op actor "t8" {:op :flag-safety-concern :subject "batch-4"
                                  :description "反応温度異常上昇の兆候" :severity :high :verdict :unresolved} operator)]
      (is (= :interrupted (:status r1)) "pauses for human approval, does not HARD-hold on its own finding")
      (let [r2 (approve! actor "t8")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= :unresolved (:safety-concern/verdict (store/safety-screen-of db "batch-4"))))))))

(deftest flag-safety-concern-with-missing-fields-is-held
  (testing "a flag-safety-concern proposal missing description/severity -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t9" {:op :flag-safety-concern :subject "batch-1"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:flag-safety-concern-missing-fields} (-> (store/ledger db) first :basis))))))

(deftest unresolved-safety-concern-on-file-blocks-release
  (testing "once a human approves an :unresolved flag, release-batch for that batch HARD-holds, never reaching a human"
    (let [[db actor] (fresh)
          _ (log! actor "t10pre" "batch-4")
          _ (exec-op actor "t10flag" {:op :flag-safety-concern :subject "batch-4"
                                      :description "漏洩リスク" :severity :high :verdict :unresolved} operator)
          _ (approve! actor "t10flag")
          res (exec-op actor "t10" {:op :release-batch :subject "batch-4"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:safety-concern-unresolved} (-> (store/ledger db) last :basis)))
      (is (empty? (store/release-history db))))))

(deftest unresolved-safety-concern-does-not-deadlock-its-own-resolution
  (testing "re-flagging the SAME batch with :resolved is NOT itself HARD-held by the unresolved-on-file check -- it still escalates normally"
    (let [[db actor] (fresh)
          _ (exec-op actor "t11flag-a" {:op :flag-safety-concern :subject "batch-4"
                                        :description "漏洩リスク" :severity :high :verdict :unresolved} operator)
          _ (approve! actor "t11flag-a")
          r1 (exec-op actor "t11flag-b" {:op :flag-safety-concern :subject "batch-4"
                                         :description "点検により解消確認" :severity :low :verdict :resolved} operator)]
      (is (= :interrupted (:status r1)) "escalates normally -- the unresolved-on-file check never scopes to :flag-safety-concern itself")
      (let [r2 (approve! actor "t11flag-b")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= :resolved (:safety-concern/verdict (store/safety-screen-of db "batch-4"))))))))

(deftest release-with-unregistered-product-type-is-held
  (testing "release-batch for a batch whose declared :product-type-id does not resolve in basicchem.facts/product-types -> HOLD"
    (let [[db actor] (fresh)]
      (store/with-batches db
        {"batch-9" {:id "batch-9" :batch-name "架空製品型式バッチ"
                        :product-type-id :product/does-not-exist
                        :reaction-temp-actual-c 52.0 :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0
                        :batch-logged? false :batch-released? false
                        :jurisdiction "JPN" :status :intake}})
      (log! actor "t12pre" "batch-9")
      (let [res (exec-op actor "t12" {:op :release-batch :subject "batch-9"} operator)]
        (is (= :hold (get-in res [:state :disposition])))
        (is (some #{:product-type-unregistered} (-> (store/ledger db) last :basis)))
        (is (empty? (store/release-history db)))))))

(deftest release-with-incomplete-handoff-is-held
  (testing "a :handoff present but missing its own required identity fields -> HOLD, even though :handoff itself is optional"
    (let [[db actor] (fresh)
          _ (log! actor "t13pre" "batch-1")
          res (exec-op actor "t13" {:op :release-batch :subject "batch-1"
                                    :handoff {:handoff/source-actor "cloud-itonami-isic-2813"}} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:handoff-issuance-incomplete} (-> (store/ledger db) last :basis))))))

(deftest release-with-complete-handoff-carries-it-through-to-the-release-record
  (testing "a :handoff WITH all required identity fields escalates normally and lands on the committed release record"
    (let [[db actor] (fresh)
          _ (log! actor "t14pre" "batch-1")
          handoff {:handoff/id "ho-2011-1"
                   :handoff/source-actor "cloud-itonami-isic-2011"
                   :handoff/batch-id "batch-1"
                   :handoff/product-type-id :product/r448a-refrigerant-blend
                   :handoff/quantity-kg 500.0}
          r1 (exec-op actor "t14" {:op :release-batch :subject "batch-1" :handoff handoff} operator)]
      (is (= :interrupted (:status r1)) "release-batch is never auto, even with a complete handoff")
      (let [r2 (approve! actor "t14")]
        (is (= :commit (get-in r2 [:state :disposition])))
        (is (= handoff (get (first (store/release-history db)) "handoff")))))))

(deftest schedule-maintenance-clean-auto-commits
  (testing ":schedule-maintenance carries no capital/product risk -- auto-commits without any human approval, unlike the other three ops"
    (let [[db actor] (fresh)
          res (exec-op actor "t15" {:op :schedule-maintenance :subject "mnt-1"
                                    :equipment-id "reactor-1" :scheduled-at-iso "2026-08-01T09:00:00Z"} operator)]
      (is (= :commit (get-in res [:state :disposition])))
      (is (not= :interrupted (:status res)) "no human approval needed")
      (is (= 1 (count (store/maintenance-history db)))))))

(deftest schedule-maintenance-with-missing-fields-is-held
  (testing "a schedule-maintenance proposal missing equipment-id/scheduled-at-iso -> HOLD"
    (let [[db actor] (fresh)
          res (exec-op actor "t16" {:op :schedule-maintenance :subject "mnt-2"} operator)]
      (is (= :hold (get-in res [:state :disposition])))
      (is (some #{:schedule-maintenance-missing-fields} (-> (store/ledger db) first :basis)))
      (is (empty? (store/maintenance-history db))))))

(deftest every-decision-leaves-one-ledger-fact
  (testing "write-only-through-ledger: N operations -> N ledger facts"
    (let [[db actor] (fresh)]
      (exec-op actor "a" {:op :schedule-maintenance :subject "mnt-3"
                          :equipment-id "reactor-2" :scheduled-at-iso "2026-09-01T00:00:00Z"} operator)
      (exec-op actor "b" {:op :log-production-batch :subject "batch-2" :no-spec? true} operator)
      (is (= 2 (count (store/ledger db)))
          "one commit + one hold, both recorded"))))
