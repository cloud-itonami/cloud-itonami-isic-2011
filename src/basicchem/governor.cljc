(ns basicchem.governor
  "Chemical Safety Governor -- the independent compliance layer that
  earns the Basic Chemicals Advisor the right to commit. The LLM has
  no notion of chemical-safety law, whether a batch's own measured
  reaction temperature actually stays within its own recorded spec
  bounds, whether a process-safety concern about a batch has actually
  stayed unresolved, or when an act stops being a draft and becomes a
  real-world market release, so this MUST be a separate system able
  to *reject* a proposal and fall back to HOLD -- the basic-chemicals-
  manufacturer analog of `pressureequip.governor` (cloud-itonami-
  isic-2813).

  Eleven checks below, ALL HARD violations: a human approver CANNOT
  override them (you don't get to approve your way past a fabricated
  jurisdiction spec-basis, incomplete evidence, an out-of-window
  reaction temperature, an unresolved process-safety concern, a
  release before the batch was ever logged, or a double log/release).
  The confidence/high-stakes gate is SOFT: it asks a human to look (low
  confidence / high-stakes), and the human may approve -- but see
  `basicchem.phase`: for `:log-production-batch`/`:release-batch`/
  `:flag-safety-concern` (real safety-critical/market acts) NO phase
  ever allows auto-commit either. Two independent layers agree that
  these three are always a human call; only `:schedule-maintenance`
  (routine, no direct capital/product risk) may auto-commit when
  clean.

  1. Spec-basis                   -- did the batch-log proposal cite an
                                      OFFICIAL source (`basicchem.
                                      facts`), or invent one? Scoped to
                                      `:log-production-batch` only (the
                                      op where jurisdiction citation
                                      actually happens; `:release-batch`
                                      relies on the ALREADY-logged
                                      batch's own evidence-checklist,
                                      checked by #2 below).
  2. Evidence incomplete          -- for `:release-batch`, has the
                                      batch's own recorded evidence-
                                      checklist actually satisfied its
                                      jurisdiction's required-evidence
                                      set (`basicchem.facts/required-
                                      evidence-satisfied?`)? Do not trust
                                      the advisor's self-reported
                                      confidence alone.
  3. Reaction temperature out of
     range                        -- for `:release-batch`, INDEPENDENTLY
                                      recompute whether the batch's own
                                      measured reaction temperature falls
                                      outside its own recorded spec
                                      bounds (`basicchem.registry/
                                      reaction-temp-out-of-range?`) --
                                      needs no proposal inspection or
                                      stored-verdict lookup at all. One
                                      of this fleet's two-sided range
                                      check family (`pressureequip.
                                      registry/unit-test-pressure-out-
                                      of-range?` and its own prior
                                      siblings established the priors).
  4. Process-safety concern
     unresolved                   -- an unresolved process-safety
                                      concern ON FILE for the batch
                                      (`basicchem.store/safety-screen-
                                      of`) HARD-blocks `:log-production-
                                      batch`/`:release-batch` for that
                                      batch, un-overridably. Deliberately
                                      NOT scoped to `:flag-safety-
                                      concern` itself -- see Decision
                                      note below; re-flagging (e.g. to
                                      mark a concern `:resolved` once
                                      fixed) must always remain
                                      possible, or an unresolved concern
                                      would deadlock its own resolution.
  5. Batch not logged before
     release                      -- for `:release-batch`, the SAME
                                      batch must have actually been
                                      `:log-production-batch`-logged by
                                      this actor (`:batch-logged?
                                      true`) first -- you cannot release
                                      a batch that was never produced/
                                      logged.
  6. Product-type unregistered    -- for `:release-batch`, when the
                                      batch declares a `:product-type-
                                      id` (an OPTIONAL reference into
                                      `basicchem.facts/product-types`,
                                      the concrete-product-model catalog
                                      carrying UNSPSC/GTIN classification
                                      data), INDEPENDENTLY verify it
                                      actually resolves. The SAME
                                      anti-fabrication discipline
                                      `pressureequip.governor`'s
                                      `unit-type-unregistered-
                                      violations` established.
  7. Handoff issuance incomplete  -- for `:release-batch`, `:handoff`
                                      (the superproject shared shape,
                                      ADR-2607177600/ADR-2800000500) is
                                      entirely OPTIONAL -- a release with
                                      NO `:handoff` at all is never held.
                                      But a `:handoff` that IS present
                                      and missing its own required
                                      identity fields is a fabricated/
                                      incomplete reference this actor
                                      must never issue downstream --
                                      HARD hold. This is isic-2011's
                                      ISSUE side of the exact shape
                                      `pressureequip.governor`'s
                                      `part-receipt-handoff-incomplete-
                                      violations` checks on the RECEIVE
                                      side.
  8. Already logged               -- refuses to log the SAME batch
                                      twice, off a dedicated `:batch-
                                      logged?` fact (never a `:status`
                                      value) -- the SAME discipline
                                      every prior sibling governor's
                                      double-actuation guards establish.
  9. Already released             -- refuses to release the SAME batch
                                      to market twice, off a dedicated
                                      `:batch-released?` fact.
  10. Schedule-maintenance missing
      fields                      -- for `:schedule-maintenance`, the
                                      proposal's `:value` must carry
                                      both required `:maintenance/*`
                                      fields -- this actor never
                                      schedules maintenance against an
                                      unnamed piece of equipment or with
                                      no scheduled date.
  11. Flag-safety-concern missing
      fields                      -- for `:flag-safety-concern`, the
                                      proposal's `:value` must carry
                                      both required `:safety-concern/*`
                                      fields -- this actor never logs a
                                      vague/fabricated concern with no
                                      description or severity.

  Decision note (deliberate departure from `pressureequip.governor`'s
  `:pressure-test/screen` precedent): that sibling op HARD-holds
  immediately on its OWN finding of an unresolved defect, never
  reaching a human, because its verdict is autonomously re-derived by
  the advisor from ground-truth unit fields. `:flag-safety-concern`
  here is different by design: the observation comes from the
  REQUESTER (a human/engineer reporting a real hazard), so it ALWAYS
  escalates to a human for review regardless of severity (see
  `basicchem.governor/high-stakes` below) -- it is never itself HARD-
  held by check #4. Once a human approves an `:unresolved` flag, THAT
  is what permanently blocks `:log-production-batch`/`:release-batch`
  for the batch going forward, until a later `:flag-safety-concern`
  (also human-approved) records `:resolved`."
  (:require [basicchem.facts :as facts]
            [basicchem.registry :as registry]
            [basicchem.store :as store]))

(def confidence-floor 0.6)

(def high-stakes
  "Stakes grave enough to always require a human, even when clean.
  Logging a production batch, releasing a batch to market, and flagging
  a process-safety concern are the three ops this actor treats as
  always-human -- only `:schedule-maintenance` (routine, no direct
  capital/product risk) is absent from this set."
  #{:log-production-batch :release-batch :flag-safety-concern})

;; ----------------------------- checks -----------------------------

(defn- spec-basis-violations
  "A `:log-production-batch` proposal with no spec-basis citation is a
  HARD violation -- never invent a jurisdiction's chemical-safety
  requirements. Scoped to `:log-production-batch` only -- see ns
  docstring check #1."
  [{:keys [op]} proposal]
  (when (= op :log-production-batch)
    (let [value (:value proposal)]
      (when (or (empty? (:cites proposal))
                (and (contains? value :spec-basis) (nil? (:spec-basis value))))
        [{:rule :no-spec-basis
          :detail "公式spec-basisの引用が無い提案はバッチ記録として扱えない"}]))))

(defn- evidence-incomplete-violations
  "For `:release-batch`, the batch's own recorded evidence-checklist
  must actually satisfy its jurisdiction's required-evidence set --
  do not trust the advisor's self-reported confidence alone."
  [{:keys [op subject]} st]
  (when (= op :release-batch)
    (let [a (store/batch st subject)]
      (when-not (and a (facts/required-evidence-satisfied? (:jurisdiction a) (:evidence-checklist a)))
        [{:rule :evidence-incomplete
          :detail "法域の必要書類(届出/排出量報告/実績記録/SDS提供記録等)が充足していない状態での市場出荷提案"}]))))

(defn- reaction-temp-out-of-range-violations
  "For `:release-batch`, INDEPENDENTLY recompute whether the batch's
  own measured reaction temperature falls outside its own recorded
  spec bounds via `basicchem.registry/reaction-temp-out-of-range?` --
  needs no proposal inspection or stored-verdict lookup at all, since
  its inputs are permanent ground-truth fields already on the batch."
  [{:keys [op subject]} st]
  (when (= op :release-batch)
    (let [a (store/batch st subject)]
      (when (registry/reaction-temp-out-of-range? a)
        [{:rule :reaction-temp-out-of-range
          :detail (str subject " の実測反応温度(" (:reaction-temp-actual-c a)
                      ")が仕様範囲[" (:reaction-temp-min-c a) "," (:reaction-temp-max-c a) "]を逸脱")}]))))

(defn- safety-concern-unresolved-violations
  "An unresolved process-safety concern ON FILE for the batch
  (`basicchem.store/safety-screen-of`) HARD-blocks `:log-production-
  batch`/`:release-batch` -- un-overridable. Deliberately NOT scoped to
  `:flag-safety-concern` itself (see ns docstring Decision note): a
  human must always be able to re-flag/update a concern's own verdict,
  or an unresolved concern would deadlock its own resolution."
  [{:keys [op subject]} st]
  (when (contains? #{:log-production-batch :release-batch} op)
    (when (= :unresolved (:safety-concern/verdict (store/safety-screen-of st subject)))
      [{:rule :safety-concern-unresolved
        :detail (str subject " について未解決の工程安全懸念が記録されている状態でのバッチ記録/出荷提案は進められない")}])))

(defn- batch-not-logged-before-release-violations
  "For `:release-batch`, the SAME batch must have actually been
  `:log-production-batch`-logged (`:batch-logged? true`) first -- you
  cannot release a batch that was never produced/logged."
  [{:keys [op subject]} st]
  (when (= op :release-batch)
    (when-not (:batch-logged? (store/batch st subject))
      [{:rule :batch-not-logged
        :detail (str subject " はまだ :log-production-batch でバッチ記録されていない -- 未記録バッチの出荷提案")}])))

(defn- product-type-unregistered-violations
  "For `:release-batch`, when the batch declares a `:product-type-id`
  (an OPTIONAL reference into `basicchem.facts/product-types`, the
  concrete-product-model catalog carrying UNSPSC/GTIN classification
  data), INDEPENDENTLY verify that reference actually resolves via
  `facts/product-type-by-id` -- the SAME anti-fabrication discipline
  `spec-basis-violations` applies to a jurisdiction citation, applied
  here to a product-type reference. A batch with NO `:product-type-id`
  declared is NOT a violation -- only a PRESENT-but-unresolvable
  reference (fabricated or mistyped) HARD-holds."
  [{:keys [op subject]} st]
  (when (= op :release-batch)
    (let [a (store/batch st subject)
          product-type-id (:product-type-id a)]
      (when (and (some? product-type-id) (nil? (facts/product-type-by-id product-type-id)))
        [{:rule :product-type-unregistered
          :detail (str subject " の :product-type-id (" product-type-id
                      ") が basicchem.facts/product-types に存在しない -- 架空/誤記の製品型式参照")}]))))

(defn- handoff-issuance-incomplete-violations
  "For `:release-batch`, `:handoff` (the superproject `:handoff`
  shared shape, ADR-2607177600/ADR-2800000500, reused as-is) is
  entirely OPTIONAL -- a release with NO `:handoff` at all is NOT a
  violation (this actor may release a batch with no known downstream
  consignee). But a `:handoff` that IS present and missing any of its
  own three identity/correlation fields (`registry/handoff-fields-
  present?`) is a fabricated/incomplete reference this actor must
  never issue downstream -- HARD hold."
  [{:keys [op]} proposal]
  (when (= op :release-batch)
    (when-let [handoff (:handoff (:value proposal))]
      (when-not (registry/handoff-fields-present? handoff)
        [{:rule :handoff-issuance-incomplete
          :detail "handoff参照が付与されているが必須フィールド(:handoff/id・:handoff/source-actor・:handoff/batch-id)が不足 -- 架空/不完全なhandoffは発行できない"}]))))

(defn- already-logged-violations
  "For `:log-production-batch`, refuses to log the SAME batch twice,
  off a dedicated `:batch-logged?` fact (never a `:status` value)."
  [{:keys [op subject]} st]
  (when (= op :log-production-batch)
    (when (store/batch-already-logged? st subject)
      [{:rule :already-logged
        :detail (str subject " は既にバッチ記録済み")}])))

(defn- already-released-violations
  "For `:release-batch`, refuses to release the SAME batch to market
  twice, off a dedicated `:batch-released?` fact."
  [{:keys [op subject]} st]
  (when (= op :release-batch)
    (when (store/batch-already-released? st subject)
      [{:rule :already-released
        :detail (str subject " は既に市場出荷済み")}])))

(defn- schedule-maintenance-missing-fields-violations
  "For `:schedule-maintenance`, the proposal's `:value` must carry
  both required `:maintenance/*` fields (`registry/maintenance-fields-
  present?`) -- this actor never schedules maintenance against an
  unnamed piece of equipment or with no scheduled date."
  [{:keys [op]} proposal]
  (when (= op :schedule-maintenance)
    (when-not (registry/maintenance-fields-present? (:value proposal))
      [{:rule :schedule-maintenance-missing-fields
        :detail "設備保守スケジュール提案に必須フィールド(:maintenance/equipment-id・:maintenance/scheduled-at-iso)が不足"}])))

(defn- flag-safety-concern-missing-fields-violations
  "For `:flag-safety-concern`, the proposal's `:value` must carry both
  required `:safety-concern/*` fields (`registry/safety-concern-
  fields-present?`) -- this actor never logs a vague/fabricated
  process-safety concern with no description or severity."
  [{:keys [op]} proposal]
  (when (= op :flag-safety-concern)
    (when-not (registry/safety-concern-fields-present? (:value proposal))
      [{:rule :flag-safety-concern-missing-fields
        :detail "工程安全懸念フラグ提案に必須フィールド(:safety-concern/description・:safety-concern/severity)が不足"}])))

(defn check
  "Censors a Basic Chemicals Advisor proposal against the governor
  rules. Returns {:ok? bool :violations [..] :confidence c :escalate?
  bool :high-stakes? bool :hard? bool}."
  [request _context proposal st]
  (let [hard (into []
                   (concat (spec-basis-violations request proposal)
                           (evidence-incomplete-violations request st)
                           (reaction-temp-out-of-range-violations request st)
                           (safety-concern-unresolved-violations request st)
                           (batch-not-logged-before-release-violations request st)
                           (product-type-unregistered-violations request st)
                           (handoff-issuance-incomplete-violations request proposal)
                           (already-logged-violations request st)
                           (already-released-violations request st)
                           (schedule-maintenance-missing-fields-violations request proposal)
                           (flag-safety-concern-missing-fields-violations request proposal)))
        conf (:confidence proposal 0.0)
        low? (< conf confidence-floor)
        stakes? (boolean (high-stakes (:stake proposal)))
        hard? (boolean (seq hard))]
    {:ok?          (and (not hard?) (not low?) (not stakes?))
     :violations   hard
     :confidence   conf
     :hard?        hard?
     :escalate?    (and (not hard?) (or low? stakes?))
     :high-stakes? stakes?}))

(defn hold-fact
  "The audit fact written when a proposal is rejected (HOLD)."
  [request context verdict]
  {:t          :governor-hold
   :op         (:op request)
   :actor      (:actor-id context)
   :subject    (:subject request)
   :disposition :hold
   :basis      (mapv :rule (:violations verdict))
   :violations (:violations verdict)
   :confidence (:confidence verdict)})
