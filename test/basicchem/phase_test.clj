(ns basicchem.phase-test
  "The phase table as executable tests. The invariant this repo cannot
  regress on: `:log-production-batch`/`:release-batch`/`:flag-safety-
  concern` must NEVER be a member of any phase's `:auto` set."
  (:require [clojure.test :refer [deftest is testing]]
            [basicchem.phase :as phase]))

(deftest log-production-batch-never-auto-at-any-phase
  (testing "structural invariant: no phase, now or in the future entries, auto-commits a production-batch log"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :log-production-batch))
          (str "phase " n " must not auto-commit :log-production-batch")))))

(deftest release-batch-never-auto-at-any-phase
  (testing "structural invariant: no phase auto-commits a real market release"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :release-batch))
          (str "phase " n " must not auto-commit :release-batch")))))

(deftest flag-safety-concern-never-auto-at-any-phase
  (testing "a process-safety concern report is never auto-eligible, same posture as the two batch-lifecycle ops"
    (doseq [[n {:keys [auto]}] phase/phases]
      (is (not (contains? auto :flag-safety-concern))
          (str "phase " n " must not auto-commit :flag-safety-concern")))))

(deftest phase-0-is-fully-read-only
  (is (empty? (:writes (get phase/phases 0)))))

(deftest phase-3-auto-commits-only-no-capital-risk-ops
  (testing ":schedule-maintenance carries no direct capital/product risk -- auto-eligible; it is the ONLY auto-eligible op in this domain"
    (is (= #{:schedule-maintenance} (:auto (get phase/phases 3))))))

(deftest gate-hold-always-wins
  (is (= :hold (:disposition (phase/gate 3 {:op :schedule-maintenance} :hold)))))

(deftest gate-escalates-a-clean-non-auto-write
  (is (= :escalate (:disposition (phase/gate 3 {:op :log-production-batch} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :release-batch} :commit))))
  (is (= :escalate (:disposition (phase/gate 3 {:op :flag-safety-concern} :commit)))))

(deftest gate-auto-commits-schedule-maintenance-when-clean
  (is (= :commit (:disposition (phase/gate 3 {:op :schedule-maintenance} :commit)))))

(deftest gate-holds-a-write-disabled-in-this-phase
  (is (= :hold (:disposition (phase/gate 0 {:op :log-production-batch} :commit)))))

(deftest schedule-maintenance-disabled-before-phase-2
  (testing "assisted-logging (phase 1) does not yet enable :schedule-maintenance"
    (is (not (contains? (:writes (get phase/phases 1)) :schedule-maintenance)))
    (is (contains? (:writes (get phase/phases 2)) :schedule-maintenance))
    (is (contains? (:writes (get phase/phases 3)) :schedule-maintenance))))

(deftest release-batch-disabled-before-phase-3
  (testing ":release-batch is only write-ops-eligible from phase 3 (the fullest write set)"
    (is (not (contains? (:writes (get phase/phases 1)) :release-batch)))
    (is (not (contains? (:writes (get phase/phases 2)) :release-batch)))
    (is (contains? (:writes (get phase/phases 3)) :release-batch))))
