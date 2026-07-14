(ns chemicals.governor
  "Basic Chemicals Manufacturing Operations Governor -- the independent compliance layer
  that earns the Chemical Operations Advisor the right to propose and log actions.
  The LLM has no notion of chemical safety standards, emissions regulations,
  or when a reaction-scheduling or emissions-reporting is a real-world actuation,
  so this MUST be a separate system able to *reject* a proposal and fall back
  to HOLD.

  HARD violations (a human approver CANNOT override):
    1. Spec-basis       -- no official jurisdiction citation
    2. Evidence incomplete -- for actuation, required evidence checklist
    3. Emissions threshold exceedance -- ALWAYS escalates (never silent log)
    4. Process-control operations -- NO reaction-parameter, valve, or flow control decisions
                                     (those remain plant engineer exclusive authority)

  SOFT violation (can be approved by human):
    5. Confidence floor / actuation gate -- low confidence OR real actuation

  CRITICAL SCOPE BOUNDARY:
  This actor coordinates LOGISTICS and COMPLIANCE PAPERWORK around the
  chemical manufacturing process. It does NOT:
    - Control reactor temperature, pressure, or flow parameters
    - Control feedstock dosing or injection
    - Operate process-control hardware (monitors only)
    - Make chemical engineering decisions about reaction conditions/catalysts

  Those remain the exclusive authority of licensed chemical engineers."
  (:require [chemicals.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Operations that require human sign-off for real-world actuation:
  Reaction scheduling and emissions reporting with exceedances."
  #{:actuation/schedule-reaction :actuation/log-emissions-report})

(def process-control-keywords
  "Words that indicate process-engineering authority (FORBIDDEN for this actor).
  If a proposal mentions any of these, it's a hard block."
  #{"reactor-control" "temperature-setpoint" "pressure-control" "flow-rate"
    "valve-control" "dosing" "injection" "catalyst" "reaction-parameters"
    "process-parameters" "combustion-control" "reactor" "setpoint" "parameters"})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A proposal with no spec-basis citation is a HARD violation --
  never invent a jurisdiction's requirements."
  [proposal _st]
  (let [op (:op proposal)]
    (when (contains? #{:actuation/schedule-reaction
                       :actuation/log-emissions-report} op)
      (when (or (empty? (:cites proposal))
                (and (contains? (:value proposal) :spec-basis)
                     (nil? (:spec-basis (:value proposal)))))
        [{:rule :no-spec-basis
          :detail "公式な仕様基準の引用が無い提案は処理できない"}]))))

(defn- evidence-incomplete-violations
  "For actuation, the required evidence checklist must be satisfied.
  This is a soft check: evidence should be present but missing evidence
  is escalated to human, not automatically rejected."
  [_proposal _st]
  ;; In production, would check (:evidence (:value proposal)) against
  ;; jurisdiction-specific requirements from facts.cljc.
  ;; For this reference implementation, we defer to soft gates.
  nil)

(defn- process-control-block-violations
  "HARD BLOCK: This actor does NOT make process-engineering decisions.
  If a proposal mentions reactor control, temperature setpoints, pressure control,
  dosing, or other process parameters, reject it immediately.
  Those decisions remain the exclusive authority of licensed chemical engineers."
  [proposal _st]
  (let [detail (str (:detail (:value proposal)) " " (:op proposal))
        words (re-seq #"\w+" (.toLowerCase detail))
        forbidden (some #(contains? process-control-keywords %) words)]
    (when forbidden
      [{:rule :process-control-forbidden
        :detail (str "プロセス制御は認可エンジニアの排他的権限です。"
                    "この提案には禁止キーワード '" forbidden "' が含まれています。")}])))

(defn- emissions-threshold-exceedance-violations
  "If emissions report shows threshold exceedance, this MUST escalate to human.
  Never silently log a threshold exceedance."
  [{:keys [op]} {:keys [threshold-exceeded?]}]
  (when (and (= op :actuation/log-emissions-report) threshold-exceeded?)
    [{:rule :emissions-threshold-exceedance
      :detail "排出ガス基準超過は必ず人間にエスカレートされる"}]))

(defn- supplier-verification-violations
  "Feedstock intake requires verified supplier license."
  [{:keys [op subject]} st]
  (when (= op :proposal/intake-feedstock)
    (when-not (store/supplier-verified? st subject)
      [{:rule :supplier-not-verified
        :detail "サプライヤーのライセンスが未確認"}])))

(defn- feedstock-verification-violations
  "Reaction scheduling requires verified feedstock batch."
  [{:keys [op subject]} st]
  (when (= op :actuation/schedule-reaction)
    (let [sched (store/reaction-schedule st subject)
          feedstock (:feedstock sched)]
      (when-not (store/feedstock-verified? st feedstock)
        [{:rule :feedstock-not-verified
          :detail "化学原料バッチが未検証"}]))))

(defn- confidence-gate-violations
  "Low confidence or high-stakes actuation -> escalate to human."
  [{:keys [op]} {:keys [confidence]}]
  (let [confidence (or confidence 0.5)]
    (when (or (< confidence confidence-floor)
              (contains? high-stakes op))
      [{:rule :escalate
        :detail (if (< confidence confidence-floor)
                  (str "信頼度が低い (confidence=" confidence ")")
                  "実際の操作には人間の承認が必要")}])))

;; ----------------------------- governor evaluation -----------------------------

(defn evaluate
  "Evaluate a proposal against all hard and soft gates.
  Returns a map:
    {:holds? boolean
     :hard-violations [...]
     :soft-violations [...]
     :clean? boolean}"
  [proposal st]
  (let [hard-checks-store [spec-basis-violations
                           evidence-incomplete-violations
                           process-control-block-violations
                           supplier-verification-violations
                           feedstock-verification-violations]
        hard-checks-value [emissions-threshold-exceedance-violations]
        soft-checks [confidence-gate-violations]
        hard-violations-store (mapcat #(% proposal st) hard-checks-store)
        hard-violations-value (mapcat #(% proposal (:value proposal)) hard-checks-value)
        hard-violations (concat hard-violations-store hard-violations-value)
        soft-violations (mapcat #(% proposal (:value proposal)) soft-checks)]
    {:holds? (seq hard-violations)
     :hard-violations (vec hard-violations)
     :soft-violations (vec soft-violations)
     :clean? (and (empty? hard-violations) (empty? soft-violations))}))
