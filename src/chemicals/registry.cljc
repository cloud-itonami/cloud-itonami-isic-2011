(ns chemicals.registry
  "Proposal registry and drafting helpers for basic chemicals manufacturing operations.
  Every proposal carries its spec-basis and evidence checklist.")

;; ----------------------------- hard invariants -----------------------------

(defn hard-invariant-violations
  "Hard invariants that CANNOT be overridden:
  - If operation affects process safety or emissions reporting, it must carry spec-basis."
  [op-type value]
  (when (contains? #{:actuation/schedule-reaction :actuation/log-emissions-report} op-type)
    (when (or (empty? (:cites value))
              (and (contains? value :spec-basis) (nil? (:spec-basis value))))
      [{:rule :no-spec-basis
        :detail "公式な仕様基準の引用が無い提案は処理できない"}])))

(defn protected-operation-violations
  "Operations that require human sign-off and can never be autonomous:
  - Reaction scheduling (even proposals)
  - Emissions reports with threshold exceedances"
  [op-type]
  (when (contains? #{:actuation/schedule-reaction :actuation/log-emissions-report} op-type)
    [{:rule :requires-human-approval
      :detail "本製造工程の提案・実行には人間の承認が必須"}]))

;; ----------------------------- proposal drafts -----------------------------

(defn intake-draft
  "Draft a feedstock intake proposal.
  subject: supplier ID
  cites: spec-basis citations
  evidence-checklist: map of verified evidence items"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/intake-feedstock
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn reaction-draft
  "Draft a reaction scheduling proposal.
  subject: reaction schedule ID
  cites: spec-basis citations
  evidence-checklist: map of verified evidence items (feedstock, process safety, etc.)"
  [subject cites evidence-checklist confidence detail]
  {:op :actuation/schedule-reaction
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn byproduct-draft
  "Draft a byproduct (chemical waste, byproducts) disposal coordination proposal.
  subject: disposal ID
  cites: spec-basis citations
  evidence-checklist: map of verified evidence items"
  [subject cites evidence-checklist confidence detail]
  {:op :proposal/coordinate-byproduct-disposal
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :detail detail}})

(defn emissions-report-draft
  "Draft an emissions compliance report.
  subject: report ID
  cites: spec-basis citations
  threshold-exceeded?: boolean -- if true, this ALWAYS escalates to human
  evidence-checklist: map of verified monitoring data
  detail: narrative report"
  [subject cites threshold-exceeded? evidence-checklist confidence detail]
  {:op :actuation/log-emissions-report
   :subject subject
   :effect :propose
   :cites cites
   :value {:evidence evidence-checklist
           :confidence confidence
           :threshold-exceeded? threshold-exceeded?
           :detail detail}})
