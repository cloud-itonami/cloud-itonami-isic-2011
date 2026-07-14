(ns chemicals.store-contract-test
  (:require [clojure.test :refer [deftest is]]
            [chemicals.store :as store]))

(deftest store-initialization
  "Store should initialize with reference data."
  (let [st (store/mem-store)]
    (is (map? @(:data st)) "Data should be a map")))

(deftest supplier-access
  "Supplier records should be accessible."
  (let [st (store/mem-store)
        supplier (store/supplier st "supplier-001")]
    (is (map? supplier) "Supplier should be a map")
    (is (true? (:license-verified? supplier)) "Supplier should be verified")))

(deftest feedstock-access
  "Feedstock records should be accessible."
  (let [st (store/mem-store)
        feedstock (store/feedstock st "feedstock-batch-001")]
    (is (map? feedstock) "Feedstock should be a map")
    (is (true? (:verified? feedstock)) "Feedstock should be verified")))

(deftest reaction-schedule-access
  "Reaction schedule records should be accessible."
  (let [st (store/mem-store)
        schedule (store/reaction-schedule st "reaction-001")]
    (is (map? schedule) "Schedule should be a map")
    (is (= :scheduled (:status schedule)) "Schedule should be scheduled")))

(deftest emissions-report-access
  "Emissions report records should be accessible."
  (let [st (store/mem-store)
        report (store/emissions-report st "report-001")]
    (is (map? report) "Report should be a map")
    (is (true? (:compliant? report)) "Report should be compliant")))

(deftest supplier-verification-check
  "Supplier verification check should work."
  (let [st (store/mem-store)]
    (is (true? (store/supplier-verified? st "supplier-001"))
      "Verified supplier should return true")
    (is (false? (store/supplier-verified? st "supplier-unknown"))
      "Unknown supplier should return false")))

(deftest feedstock-verification-check
  "Feedstock verification check should work."
  (let [st (store/mem-store)]
    (is (true? (store/feedstock-verified? st "feedstock-batch-001"))
      "Verified feedstock should return true")
    (is (false? (store/feedstock-verified? st "feedstock-unknown"))
      "Unknown feedstock should return false")))

(deftest emissions-threshold-check
  "Emissions threshold exceedance check should work."
  (let [st (store/mem-store)]
    (is (false? (store/emissions-exceeds-threshold? st "report-001"))
      "Compliant report should not exceed threshold")))
