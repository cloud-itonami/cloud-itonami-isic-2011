(ns basicchem.facts-test
  (:require [clojure.test :refer [deftest is]]
            [basicchem.facts :as facts]))

(deftest jpn-has-a-spec-basis
  (is (some? (facts/spec-basis "JPN")))
  (is (string? (:provenance (facts/spec-basis "JPN")))))

(deftest mex-has-a-spec-basis
  (is (some? (facts/spec-basis "MEX")))
  (is (string? (:provenance (facts/spec-basis "MEX"))))
  (is (= "Secretaría de Medio Ambiente y Recursos Naturales (SEMARNAT)"
         (:owner-authority (facts/spec-basis "MEX")))))

(deftest unknown-jurisdiction-has-no-fabricated-spec-basis
  (is (nil? (facts/spec-basis "ATL"))))

(deftest coverage-never-reports-a-missing-jurisdiction-as-covered
  (let [report (facts/coverage ["JPN" "ATL" "GBR"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["GBR" "JPN"] (:covered-jurisdictions report)))))

(deftest coverage-includes-mex
  (let [report (facts/coverage ["JPN" "MEX" "ATL"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["JPN" "MEX"] (:covered-jurisdictions report)))))

(deftest required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "JPN")]
    (is (facts/required-evidence-satisfied? "JPN" all))
    (is (not (facts/required-evidence-satisfied? "JPN" (rest all))))
    (is (not (facts/required-evidence-satisfied? "ATL" all)) "no spec-basis -> never satisfied")))

(deftest mex-required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "MEX")]
    (is (= 4 (count all)))
    (is (facts/required-evidence-satisfied? "MEX" all))
    (is (not (facts/required-evidence-satisfied? "MEX" (rest all))))))

(deftest kor-has-a-spec-basis
  (is (some? (facts/spec-basis "KOR")))
  (is (string? (:provenance (facts/spec-basis "KOR"))))
  (is (re-find #"Ministry of Environment" (:owner-authority (facts/spec-basis "KOR")))))

(deftest coverage-includes-kor
  (let [report (facts/coverage ["JPN" "KOR" "ATL"])]
    (is (= 2 (:covered report)))
    (is (= ["ATL"] (:missing-jurisdictions report)))
    (is (= ["JPN" "KOR"] (:covered-jurisdictions report)))))

(deftest kor-required-evidence-satisfied-needs-every-item
  (let [all (facts/evidence-checklist "KOR")]
    (is (= 4 (count all)))
    (is (facts/required-evidence-satisfied? "KOR" all))
    (is (not (facts/required-evidence-satisfied? "KOR" (rest all))))))

;; ──────────────── Product-Model Catalog (UNSPSC/GTIN linkage) ────────────────

(deftest r448a-refrigerant-blend-product-type-has-unspsc-and-gtin
  (let [p (facts/product-type-by-id :product/r448a-refrigerant-blend)]
    (is (some? p))
    (is (= "12352101" (:unspsc-code p)) "UNSPSC commodity code (class 123521, 'Organic halogenated compounds')")
    (is (= "0212011000010" (:gtin p)))
    (is (= :unissued-blueprint-placeholder (:gtin/status p))
        "placeholder GTIN is never presented as a real, GS1-issued identifier")))

(deftest unknown-product-type-has-no-fabricated-entry
  (is (nil? (facts/product-type-by-id :product/does-not-exist))))
