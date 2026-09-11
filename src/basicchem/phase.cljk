(ns basicchem.phase
  "Phase 0->3 staged rollout -- the basic-chemicals-manufacturer analog
  of `pressureequip.phase` (cloud-itonami-isic-2813).

    Phase 0  read-only         -- no writes, still governor-gated.
    Phase 1  assisted-logging  -- production-batch logging allowed,
                                  every write needs human approval.
    Phase 2  assisted-safety   -- adds process-safety-concern flagging
                                  + maintenance scheduling writes,
                                  still approval.
    Phase 3  supervised auto   -- governor-clean, high-confidence
                                  `:schedule-maintenance` (no capital/
                                  product risk) may auto-commit.
                                  `:log-production-batch`/`:release-
                                  batch`/`:flag-safety-concern` NEVER
                                  auto-commit, at any phase.

  `:log-production-batch`/`:release-batch`/`:flag-safety-concern` are
  deliberately ABSENT from every phase's `:auto` set, including phase
  3 -- a permanent structural fact, not a rollout milestone still to
  come. Logging a production batch, releasing a chemical batch to
  market, and flagging a process-safety concern are the three
  real-world acts this actor performs that always warrant a human
  chemical-safety engineer's call. `basicchem.governor`'s `high-
  stakes` gate enforces the same invariant independently -- two layers,
  not one, agree on this. Phase 3's `:auto` set here has only ONE
  member (`:schedule-maintenance`) -- this domain has no separate
  no-capital-risk 'intake' lifecycle distinct from the batch record
  itself; routine equipment maintenance scheduling is the one op with
  no direct capital/product-safety risk."
  )

(def read-ops  #{})
(def write-ops #{:log-production-batch :schedule-maintenance
                 :flag-safety-concern :release-batch})

;; NOTE the invariant: `:log-production-batch`/`:release-batch`/
;; `:flag-safety-concern` are members of `write-ops` (governor-gated
;; like any write) but are NEVER members of any phase's `:auto` set
;; below. Do not add them there.
(def phases
  "phase -> {:label .. :writes <ops allowed to write> :auto <ops allowed to
  auto-commit when governor-clean>}."
  {0 {:label "read-only"        :writes #{}                                                          :auto #{}}
   1 {:label "assisted-logging" :writes #{:log-production-batch}                                     :auto #{}}
   2 {:label "assisted-safety"  :writes #{:log-production-batch :flag-safety-concern :schedule-maintenance} :auto #{}}
   3 {:label "supervised-auto"  :writes write-ops
      :auto #{:schedule-maintenance}}})

(def default-phase 3)

(defn gate
  "Adjust a governor disposition for the rollout phase. Returns
  {:disposition kw :reason kw|nil}.

  - a governor HOLD always stays HOLD (compliance wins).
  - a write op not yet enabled in this phase -> HOLD (:phase-disabled).
  - a write op enabled but not auto-eligible -> ESCALATE (:phase-approval),
    even if the governor was clean.
  - `:log-production-batch`/`:release-batch`/`:flag-safety-concern` are
    never auto-eligible at any phase, so they always escalate once the
    governor clears them (or hold if the governor doesn't)."
  [phase {:keys [op]} governor-disposition]
  (let [{:keys [writes auto]} (get phases phase (get phases default-phase))]
    (cond
      (= :hold governor-disposition)       {:disposition :hold :reason nil}
      (contains? read-ops op)              {:disposition governor-disposition :reason nil}
      (not (contains? writes op))          {:disposition :hold :reason :phase-disabled}
      (and (= :commit governor-disposition)
           (not (contains? auto op)))      {:disposition :escalate :reason :phase-approval}
      :else                                {:disposition governor-disposition :reason nil})))

(defn verdict->disposition
  "Map a Chemical Safety Governor verdict to a base disposition before
  the phase gate."
  [verdict]
  (cond (:hard? verdict) :hold
        (:escalate? verdict) :escalate
        :else :commit))
