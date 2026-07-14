(ns chemicals.governor-contract-test
  (:require [clojure.test :refer [deftest is]]
            [chemicals.store :as store]
            [chemicals.advisor :as advisor]
            [chemicals.governor :as governor]
            [chemicals.registry :as registry]))

(deftest spec-basis-hard-gate
  "Spec-basis is a HARD gate: never allow proposals without official citations."
  (let [st (store/mem-store)
        proposal {:op :actuation/schedule-reaction
                  :subject "reaction-001"
                  :effect :propose
                  :value {:evidence {:feedstock-verified true}
                          :confidence 0.9}
                  :cites []}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Proposal with empty cites should hold")
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :no-spec-basis) (:hard-violations eval))))))

(deftest process-control-block
  "HARD BLOCK: Proposals mentioning reactor control, temperature, pressure control,
  or process parameters are immediately rejected. Those remain engineer exclusive authority."
  (let [st (store/mem-store)
        proposal {:op :actuation/schedule-reaction
                  :subject "reaction-001"
                  :effect :propose
                  :cites ["some-spec"]
                  :value {:evidence {:feedstock-verified true}
                          :confidence 0.9
                          :detail "Please set reactor temperature to 500K and increase flow-rate now"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Process-control proposal should hold")
      (is (some #(= (:rule %) :process-control-forbidden) (:hard-violations eval))
        "Should have process-control-forbidden violation"))))

(deftest emissions-threshold-exceedance-escalation
  "Emissions reports with threshold exceedances ALWAYS escalate to human.
  Never silently log a threshold exceedance."
  (let [st (store/mem-store)
        proposal {:op :actuation/log-emissions-report
                  :subject "report-001"
                  :effect :propose
                  :cites ["Air Pollution Control Law §3"]
                  :value {:evidence {:monitoring-data true}
                          :confidence 0.95
                          :threshold-exceeded? true
                          :detail "VOC exceeds threshold"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:holds? eval) "Threshold exceedance should hold")
      (is (some #(= (:rule %) :emissions-threshold-exceedance) (:hard-violations eval))
        "Should have emissions-threshold-exceedance violation"))))

(deftest actuation-requires-escalation
  "Both reaction scheduling and emissions reporting require human sign-off,
  even when all other checks are clean."
  (let [st (store/mem-store)
        adv (advisor/mock-advisor)
        reaction-proposal (advisor/reaction-proposal adv "reaction-001")]
    (let [eval (governor/evaluate reaction-proposal st)]
      (is (seq (:soft-violations eval)) "Should have soft violations for actuation")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should escalate high-stakes actuation"))))

(deftest supplier-not-verified-blocks-intake
  "Feedstock intake from unverified supplier is blocked."
  (let [st (store/mem-store)
        proposal (registry/intake-draft "supplier-unknown"
                   ["High Pressure Gas Safety Law §2"]
                   {:supplier-license true}
                   0.85
                   "Feedstock from unknown supplier")]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :supplier-not-verified) (:hard-violations eval))
        "Should block unverified supplier"))))

(deftest feedstock-not-verified-blocks-reaction
  "Reaction scheduling with unverified feedstock is blocked."
  (let [st (store/mem-store)
        ;; Create a reaction schedule with an unverified feedstock
        _ (swap! (-> st :data) assoc-in [:reaction-schedules "reaction-002" :feedstock] "feedstock-unknown")
        proposal (registry/reaction-draft "reaction-002"
                   ["Industrial Safety and Health Act §57の3"]
                   {:process-safety-plan true}
                   0.88
                   "Schedule reaction")]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:hard-violations eval)) "Should have hard violations")
      (is (some #(= (:rule %) :feedstock-not-verified) (:hard-violations eval))
        "Should block unverified feedstock"))))

(deftest low-confidence-escalates
  "Low confidence proposals escalate to human, even if otherwise clean."
  (let [st (store/mem-store)
        proposal {:op :actuation/log-emissions-report
                  :subject "report-001"
                  :effect :propose
                  :cites ["Air Pollution Control Law §3"]
                  :value {:evidence {:monitoring-data true}
                          :confidence 0.45
                          :threshold-exceeded? false
                          :detail "Emissions within range"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (seq (:soft-violations eval)) "Should have soft violations")
      (is (some #(= (:rule %) :escalate) (:soft-violations eval))
        "Should escalate low-confidence"))))

(deftest clean-proposal
  "A proposal with all evidence, valid spec-basis, high confidence,
  and no high-stakes actuation or process-control is clean."
  (let [st (store/mem-store)
        proposal {:op :proposal/coordinate-byproduct-disposal
                  :subject "disposal-001"
                  :effect :propose
                  :cites ["Waste Disposal and Public Cleaning Law §12"]
                  :value {:evidence {:waste-management-plan true :disposal-facility-cert true}
                          :confidence 0.9
                          :detail "Chemical waste disposal to licensed facility"}}]
    (let [eval (governor/evaluate proposal st)]
      (is (:clean? eval) "Should be clean")
      (is (empty? (:hard-violations eval)) "Should have no hard violations"))))
