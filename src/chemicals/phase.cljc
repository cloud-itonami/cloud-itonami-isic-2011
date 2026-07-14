(ns chemicals.phase
  "State machine phase definitions for basic chemicals manufacturing operations.
  Defines transitions and constraints for the operational lifecycle.")

;; ----------------------------- phase table -----------------------------

(def phases
  "State machine phases for chemical operations actor.
  Each phase defines:
    :actions -- allowed proposal types in this phase
    :requires -- prerequisites that must be satisfied
    :next -- target phase after action execution"
  {
   :idle
   {:actions #{:proposal/intake-feedstock :proposal/coordinate-byproduct-disposal}
    :requires []
    :next :active}

   :active
   {:actions #{:actuation/schedule-reaction :actuation/log-emissions-report}
    :requires [:feedstock-verified :process-safety-plan]
    :next :running}

   :running
   {:actions #{:actuation/log-emissions-report :proposal/coordinate-byproduct-disposal}
    :requires [:reaction-active]
    :next :complete}

   :complete
   {:actions #{:proposal/intake-feedstock}
    :requires []
    :next :idle}})

;; ----------------------------- phase transitions -----------------------------

(defn valid-phase-transition?
  "Check if a phase transition is valid for the given action."
  [current-phase action]
  (let [phase-def (get phases current-phase)]
    (contains? (:actions phase-def) action)))

(defn next-phase
  "Get the next phase after an action in the current phase."
  [current-phase]
  (get-in phases [current-phase :next]))

(defn phase-requirements
  "Get the prerequisites for a phase."
  [phase]
  (get-in phases [phase :requires]))
