(ns chemicals.advisor
  "Basic Chemicals Manufacturing Operations Advisor -- the LLM-driven suggestion layer.
  Proposes operations to the Governor for approval.")

;; ----------------------------- mock advisor for testing -----------------------------

(defn mock-advisor
  "Create a mock advisor for testing. Real implementation would call an LLM."
  []
  {:type :mock :model "mock-v1"})

(defn intake-proposal
  "Propose a feedstock intake operation."
  [_advisor supplier-id]
  {:op :proposal/intake-feedstock
   :subject supplier-id
   :effect :propose
   :cites ["High Pressure Gas Safety Law (高圧ガス保安法) §2"]
   :value {:evidence {:supplier-license true :feedstock-analysis-report true :quality-cert true}
           :confidence 0.85
           :detail "Feedstock batch verified from approved supplier"}})

(defn reaction-proposal
  "Propose a reaction scheduling operation."
  [_advisor schedule-id]
  {:op :actuation/schedule-reaction
   :subject schedule-id
   :effect :propose
   :cites ["Industrial Safety and Health Act (労働安全衛生法) §57の3"]
   :value {:evidence {:feedstock-verified true :process-safety-plan true :hazard-analysis true}
           :confidence 0.88
           :detail "Reaction ready to schedule with verified feedstock"}})

(defn emissions-proposal
  "Propose an emissions report (may include threshold exceedances)."
  [_advisor report-id threshold-exceeded?]
  {:op :actuation/log-emissions-report
   :subject report-id
   :effect :propose
   :cites ["Air Pollution Control Law (大気汚染防止法) §3"]
   :value {:evidence {:monitoring-data true :calibration-cert true}
           :confidence 0.92
           :threshold-exceeded? threshold-exceeded?
           :detail (if threshold-exceeded?
                    "Emissions monitoring: VOC exceeds threshold, escalation required"
                    "Emissions within compliance range")}})
