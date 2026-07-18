(ns basicchem.registry
  "Pure-function production-batch-log + batch-release + maintenance-
  schedule record construction -- an append-only basic-chemicals-
  manufacturer book-of-record draft.

  Like every sibling actor's registry (e.g. `pressureequip.registry`
  in cloud-itonami-isic-2813), there is no single international
  check-digit standard for a production-batch-log or batch-release
  reference number -- every manufacturer/jurisdiction assigns its own
  reference format. This namespace does NOT invent one; it builds a
  jurisdiction-scoped sequence number and validates the record's
  required fields, the same honest, non-fabricating discipline
  `basicchem.facts` uses.

  `reaction-temp-out-of-range?` continues this fleet's two-sided range
  check family (`pressureequip.registry/unit-test-pressure-out-of-
  range?` and its own prior siblings), applying the SAME lo/hi
  bounds-comparison shape to a chemical production batch's own
  measured reaction temperature against the batch's own recorded spec
  bounds -- a direct analog of the classic chemical-process-safety
  hazard (reactor temperature excursion risking a thermal-runaway
  reaction, e.g. the 1976 Seveso ICMESA batch-reactor incident and the
  2007 T2 Laboratories runaway-reaction explosion are the textbook
  real-world cases this check structurally guards against).

  This namespace is pure data + pure functions -- no I/O, no network
  call to any real plant control system. It builds the RECORD a
  manufacturer would keep, not the act of logging the production batch
  or releasing it to market itself (that is `basicchem.operation`'s
  `:log-production-batch`/`:release-batch`, always human-gated -- see
  README `Actuation`)."
  (:require [clojure.string :as str]))

(defn- unsigned-certificate
  "Every certificate this actor produces is UNSIGNED -- signature is the
  manufacturer's own act, not this actor's. See README `Actuation`."
  [kind subject record-id]
  {"@context" ["https://www.w3.org/ns/credentials/v2"]
   "type" ["VerifiableCredential" kind]
   "credentialSubject" {"id" subject "record" record-id}
   "proof" nil
   "issued_by_registry" false
   "status" "draft-unsigned"})

(defn- zero-pad [n w]
  (let [s (str n)]
    (str (apply str (repeat (max 0 (- w (count s))) "0")) s)))

(defn reaction-temp-out-of-range?
  "Does `batch`'s own `:reaction-temp-actual-c` (measured process
  reaction temperature) fall outside its own `[:reaction-temp-min-c
  :reaction-temp-max-c]` recorded spec-bounds? A pure ground-truth
  check against the batch's own permanent fields -- no upstream
  comparison needed. One of this fleet's two-sided range check family
  (see ns docstring)."
  [{:keys [reaction-temp-actual-c reaction-temp-min-c reaction-temp-max-c]}]
  (and (number? reaction-temp-actual-c) (number? reaction-temp-min-c) (number? reaction-temp-max-c)
       (or (< reaction-temp-actual-c reaction-temp-min-c)
           (> reaction-temp-actual-c reaction-temp-max-c))))

(defn register-batch-log
  "Validate + construct the PRODUCTION-BATCH-LOG registration DRAFT --
  the manufacturer's own act of recording that a batch was produced,
  under a jurisdiction's evidence checklist. Pure function -- does not
  touch any real plant control system; it builds the RECORD a
  manufacturer would keep. `basicchem.governor` independently
  re-verifies evidence completeness and a double-log for the same
  batch before this is ever allowed to commit."
  [batch-id jurisdiction sequence]
  (when-not (and batch-id (not= batch-id ""))
    (throw (ex-info "batch-log: batch_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "batch-log: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "batch-log: sequence must be >= 0" {})))
  (let [log-number (str (str/upper-case jurisdiction) "-BCM-" (zero-pad sequence 6))
        record {"record_id" log-number
                "kind" "production-batch-log-draft"
                "batch_id" batch-id
                "jurisdiction" jurisdiction
                "immutable" true}]
    {"record" record "log_number" log-number
     "certificate" (unsigned-certificate "ProductionBatchLog" log-number log-number)}))

(defn register-batch-release
  "Validate + construct the BATCH-RELEASE registration DRAFT -- the
  manufacturer's own act of releasing a chemical batch to market.
  Pure function -- does not touch any real plant control system; it
  builds the RECORD a manufacturer would keep. `basicchem.governor`
  independently re-verifies the batch's own reaction-parameter
  sufficiency, evidence completeness and a double-release for the same
  batch before this is ever allowed to commit.

  `handoff` (optional, arg 4) is the superproject `:handoff` shared
  shape (ADR-2607177600, isic-1075<->jsic-4721, reused as-is -- see
  ADR-2800000500 for the isic-2011<->isic-2813 refrigerant-supply
  linkage this arg exists for) -- when present, it is embedded verbatim
  on the record so a downstream consumer (e.g. cloud-itonami-isic-2813's
  own `:register-part-receipt`) can read it back off the release
  history. This function does NOT validate `handoff`'s own shape --
  that is `basicchem.governor`'s `handoff-issuance-incomplete-
  violations`, which independently re-verifies before commit."
  [batch-id jurisdiction sequence & [handoff]]
  (when-not (and batch-id (not= batch-id ""))
    (throw (ex-info "batch-release: batch_id required" {})))
  (when-not (and jurisdiction (not= jurisdiction ""))
    (throw (ex-info "batch-release: jurisdiction required" {})))
  (when (< sequence 0)
    (throw (ex-info "batch-release: sequence must be >= 0" {})))
  (let [release-number (str (str/upper-case jurisdiction) "-REL-" (zero-pad sequence 6))
        record (cond-> {"record_id" release-number
                        "kind" "batch-release-draft"
                        "batch_id" batch-id
                        "jurisdiction" jurisdiction
                        "immutable" true}
                 handoff (assoc "handoff" handoff))]
    {"record" record "release_number" release-number
     "certificate" (unsigned-certificate "BatchRelease" release-number release-number)}))

(defn register-maintenance-schedule
  "Validate + construct the MAINTENANCE-SCHEDULE registration DRAFT --
  the manufacturer's own act of scheduling reactor/equipment
  maintenance. Unlike `register-batch-log`/`register-batch-release`,
  this is NOT jurisdiction-scoped (routine equipment upkeep carries no
  jurisdiction-specific legal-filing basis the way a batch log/release
  does) -- `sequence` is a single global counter. Pure function -- does
  not touch any real plant control/CMMS system; it builds the RECORD a
  manufacturer would keep."
  [maintenance-id equipment-id scheduled-at-iso description sequence]
  (when-not (and maintenance-id (not= maintenance-id ""))
    (throw (ex-info "maintenance-schedule: maintenance_id required" {})))
  (when-not (and equipment-id (not= equipment-id ""))
    (throw (ex-info "maintenance-schedule: equipment_id required" {})))
  (when-not (and scheduled-at-iso (not= scheduled-at-iso ""))
    (throw (ex-info "maintenance-schedule: scheduled_at_iso required" {})))
  (when (< sequence 0)
    (throw (ex-info "maintenance-schedule: sequence must be >= 0" {})))
  (let [maintenance-number (str "MNT-" (zero-pad sequence 6))
        record (cond-> {"record_id" maintenance-number
                        "kind" "maintenance-schedule-draft"
                        "maintenance_id" maintenance-id
                        "equipment_id" equipment-id
                        "scheduled_at_iso" scheduled-at-iso
                        "immutable" true}
                 description (assoc "description" description))]
    {"record" record "maintenance_number" maintenance-number
     "certificate" (unsigned-certificate "MaintenanceSchedule" maintenance-number maintenance-number)}))

(defn append [history result]
  (conj (vec history) (get result "record")))

;; ----------------------------- required-field predicates -----------------------------

(defn maintenance-fields-present?
  "True when both REQUIRED `:maintenance/*` fields
  (`:maintenance/equipment-id`/`:maintenance/scheduled-at-iso`) are
  present -- this actor never schedules maintenance against an unnamed
  piece of equipment or with no scheduled date."
  [maintenance]
  (every? some? ((juxt :maintenance/equipment-id :maintenance/scheduled-at-iso) maintenance)))

(defn safety-concern-fields-present?
  "True when both REQUIRED `:safety-concern/*` fields
  (`:safety-concern/description`/`:safety-concern/severity`) are
  present -- this actor never logs a vague/fabricated process-safety
  concern with no description or severity."
  [safety-concern]
  (every? some? ((juxt :safety-concern/description :safety-concern/severity) safety-concern)))

;; ----------------------------- :handoff (additive, ADR-2800000500) -----------------------------
;;
;; isic-2011's ISSUE side of the superproject `:handoff` shared shape
;; (ADR-2607177600, isic-1075<->jsic-4721, reused as-is -- see
;; ADR-2800000500 for this actor pair specifically): an OPTIONAL
;; attachment on `:release-batch`'s proposal `:value`, naming this
;; actor as the R-448A refrigerant-charge supplier of a downstream
;; equipment manufacturer (e.g. cloud-itonami-isic-2813's own
;; `:register-part-receipt` for `part:refrigerant-charge`). `:handoff`
;; itself is entirely OPTIONAL on a batch release: this actor may
;; release a batch with no known downstream consignee at all. But a
;; `:handoff` that IS present and missing its own required identity
;; fields is a fabricated/incomplete reference -- HARD hold (see
;; `basicchem.governor/handoff-issuance-incomplete-violations`).

(defn handoff-fields-present?
  "True when `handoff` carries the three identity/correlation
  `:handoff/*` fields (`:handoff/id`/`:handoff/source-actor`/
  `:handoff/batch-id`) the superproject `:handoff` shared shape
  requires for traceability (ADR-2607177600) -- called ONLY when a
  `:handoff` map is actually present on a batch release (see
  `basicchem.governor/handoff-issuance-incomplete-violations`); a
  release with NO `:handoff` at all never reaches this predicate.
  Domain-specific optional fields on the shared shape
  (`:handoff/product-type-id`/`:handoff/quantity-kg`/`:handoff/cold-
  chain-temp-min-c`/`:handoff/cold-chain-temp-max-c`/`:handoff/
  dispatched-at-iso`/`:handoff/unspsc-code`/`:handoff/gtin`) are NOT
  required here -- the SAME 'optional field absent -> not checked'
  discipline `pressureequip.registry/handoff-fields-present?`
  establishes on the RECEIVE side of this exact shape."
  [handoff]
  (every? some? ((juxt :handoff/id :handoff/source-actor :handoff/batch-id) handoff)))
