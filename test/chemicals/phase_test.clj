(ns chemicals.phase-test
  (:require [clojure.test :refer [deftest is]]
            [chemicals.phase :as phase]))

(deftest phase-definitions-exist
  "Phase definitions should be defined."
  (is (pos? (count phase/phases)) "Phase table should exist"))

(deftest idle-phase-transitions
  "Idle phase should allow feedstock intake proposals."
  (is (phase/valid-phase-transition? :idle :proposal/intake-feedstock)
    "Should allow feedstock intake from idle"))

(deftest active-phase-transitions
  "Active phase should allow reaction scheduling."
  (is (phase/valid-phase-transition? :active :actuation/schedule-reaction)
    "Should allow reaction scheduling in active phase"))

(deftest invalid-phase-transition
  "Invalid transitions should be rejected."
  (is (not (phase/valid-phase-transition? :idle :actuation/schedule-reaction))
    "Should not allow reaction scheduling from idle"))

(deftest next-phase-calculation
  "Next phase should be correctly calculated."
  (let [next (phase/next-phase :idle)]
    (is (= :active next) "Idle should transition to active")))
