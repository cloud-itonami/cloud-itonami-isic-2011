(ns chemicals.registry-test
  (:require [clojure.test :refer [deftest is]]
            [chemicals.registry :as registry]))

(deftest intake-draft-creation
  "Intake draft should have correct structure."
  (let [draft (registry/intake-draft "supplier-001"
                ["High Pressure Gas Safety Law §2"]
                {:supplier-license true}
                0.85
                "Feedstock verification")]
    (is (= :proposal/intake-feedstock (:op draft)) "Should be intake operation")
    (is (= "supplier-001" (:subject draft)) "Should have correct subject")
    (is (= :propose (:effect draft)) "Should propose")
    (is (seq (:cites draft)) "Should have citations")))

(deftest reaction-draft-creation
  "Reaction draft should have correct structure."
  (let [draft (registry/reaction-draft "reaction-001"
                ["Industrial Safety and Health Act §57の3"]
                {:process-safety-plan true}
                0.88
                "Reaction scheduling")]
    (is (= :actuation/schedule-reaction (:op draft)) "Should be reaction scheduling")
    (is (= "reaction-001" (:subject draft)) "Should have correct subject")))

(deftest emissions-report-draft
  "Emissions report draft should handle threshold exceedances."
  (let [draft (registry/emissions-report-draft "report-001"
                ["Air Pollution Control Law §3"]
                true
                {:monitoring-data true}
                0.95
                "VOC exceeds threshold")]
    (is (= :actuation/log-emissions-report (:op draft)) "Should be emissions report")
    (is (true? (get-in draft [:value :threshold-exceeded?])) "Should mark threshold exceedance")))

(deftest byproduct-draft-creation
  "Byproduct draft should have correct structure."
  (let [draft (registry/byproduct-draft "disposal-001"
                ["Waste Disposal and Public Cleaning Law §12"]
                {:waste-management-plan true}
                0.85
                "Chemical waste disposal")]
    (is (= :proposal/coordinate-byproduct-disposal (:op draft)) "Should be byproduct disposal")))
