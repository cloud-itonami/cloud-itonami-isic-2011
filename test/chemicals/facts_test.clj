(ns chemicals.facts-test
  (:require [clojure.test :refer [deftest is]]
            [chemicals.facts :as facts]))

(deftest catalog-has-entries
  "Catalog should contain jurisdiction entries."
  (is (pos? (count facts/catalog)) "Catalog should not be empty"))

(deftest coverage-report
  "Coverage report should reflect catalog size."
  (let [report (facts/coverage)]
    (is (pos? (:implemented report)) "Should have implemented jurisdictions")
    (is (< 0 (:coverage-pct report) 100) "Coverage should be between 0 and 100 percent")))

(deftest japan-requirements
  "Japan jurisdiction should have all required specifications."
  (let [jpn-reqs (facts/requirement-citations :JPN)]
    (is (pos? (count jpn-reqs)) "Japan should have requirements")
    (is (contains? jpn-reqs :feedstock-verification) "Should have feedstock-verification")
    (is (contains? jpn-reqs :process-safety) "Should have process-safety")
    (is (contains? jpn-reqs :emissions-monitoring) "Should have emissions-monitoring")))

(deftest evidence-satisfaction
  "Evidence satisfaction check should work correctly."
  (let [checklist {:supplier-license true :feedstock-analysis-report true :quality-cert true
                   :process-safety-plan true :hazard-analysis true :design-review true
                   :emissions-baseline true :monitoring-equipment-cert true
                   :waste-management-plan true :disposal-facility-cert true}]
    (is (facts/required-evidence-satisfied? :JPN checklist)
      "Complete evidence should satisfy all requirements")))

(deftest evidence-incompleteness
  "Evidence incompleteness should be detected."
  (let [incomplete-checklist {:supplier-license true}]
    (is (not (facts/required-evidence-satisfied? :JPN incomplete-checklist))
      "Incomplete evidence should not satisfy requirements")))
