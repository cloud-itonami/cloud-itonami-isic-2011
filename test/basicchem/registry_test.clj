(ns basicchem.registry-test
  (:require [clojure.test :refer [deftest is]]
            [basicchem.registry :as r]))

;; ----------------------------- reaction-temp-out-of-range? -----------------------------

(deftest not-out-of-range-when-within-bounds
  (is (not (r/reaction-temp-out-of-range? {:reaction-temp-actual-c 52.0 :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0})))
  (is (not (r/reaction-temp-out-of-range? {:reaction-temp-actual-c 40.0 :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0})))
  (is (not (r/reaction-temp-out-of-range? {:reaction-temp-actual-c 60.0 :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0}))))

(deftest out-of-range-when-below-minimum-or-above-maximum
  (is (r/reaction-temp-out-of-range? {:reaction-temp-actual-c 30.0 :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0}))
  (is (r/reaction-temp-out-of-range? {:reaction-temp-actual-c 85.0 :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0})))

(deftest out-of-range-is-false-on-missing-fields
  (is (not (r/reaction-temp-out-of-range? {})))
  (is (not (r/reaction-temp-out-of-range? {:reaction-temp-actual-c 85.0}))))

;; ----------------------------- register-batch-log -----------------------------

(deftest log-is-a-draft-not-a-real-log
  (let [result (r/register-batch-log "batch-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest log-assigns-log-number
  (let [result (r/register-batch-log "batch-1" "JPN" 7)]
    (is (= (get result "log_number") "JPN-BCM-000007"))
    (is (= (get-in result ["record" "batch_id"]) "batch-1"))
    (is (= (get-in result ["record" "kind"]) "production-batch-log-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest log-validation-rules
  (is (thrown? Exception (r/register-batch-log "" "JPN" 0)))
  (is (thrown? Exception (r/register-batch-log "batch-1" "" 0)))
  (is (thrown? Exception (r/register-batch-log "batch-1" "JPN" -1))))

;; ----------------------------- register-batch-release -----------------------------

(deftest release-is-a-draft-not-a-real-release
  (let [result (r/register-batch-release "batch-1" "JPN" 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest release-assigns-release-number
  (let [result (r/register-batch-release "batch-1" "JPN" 3)]
    (is (= (get result "release_number") "JPN-REL-000003"))
    (is (= (get-in result ["record" "batch_id"]) "batch-1"))
    (is (= (get-in result ["record" "kind"]) "batch-release-draft"))
    (is (= (get-in result ["record" "immutable"]) true))))

(deftest release-embeds-handoff-when-present
  (let [handoff {:handoff/id "ho-1" :handoff/source-actor "cloud-itonami-isic-2011" :handoff/batch-id "batch-1"}
        result (r/register-batch-release "batch-1" "JPN" 0 handoff)]
    (is (= handoff (get-in result ["record" "handoff"])))))

(deftest release-omits-handoff-when-absent
  (let [result (r/register-batch-release "batch-1" "JPN" 0)]
    (is (not (contains? (get result "record") "handoff")))))

(deftest release-validation-rules
  (is (thrown? Exception (r/register-batch-release "" "JPN" 0)))
  (is (thrown? Exception (r/register-batch-release "batch-1" "" 0)))
  (is (thrown? Exception (r/register-batch-release "batch-1" "JPN" -1))))

;; ----------------------------- register-maintenance-schedule -----------------------------

(deftest maintenance-schedule-is-a-draft-not-a-real-schedule
  (let [result (r/register-maintenance-schedule "mnt-1" "reactor-1" "2026-08-01T09:00:00Z" nil 0)]
    (is (nil? (get-in result ["certificate" "proof"])))
    (is (= (get-in result ["certificate" "issued_by_registry"]) false))
    (is (= (get-in result ["certificate" "status"]) "draft-unsigned"))))

(deftest maintenance-schedule-assigns-maintenance-number-not-jurisdiction-scoped
  (let [result (r/register-maintenance-schedule "mnt-1" "reactor-1" "2026-08-01T09:00:00Z" "定期検査" 5)]
    (is (= (get result "maintenance_number") "MNT-000005"))
    (is (= (get-in result ["record" "equipment_id"]) "reactor-1"))
    (is (= (get-in result ["record" "description"]) "定期検査"))
    (is (= (get-in result ["record" "kind"]) "maintenance-schedule-draft"))))

(deftest maintenance-schedule-validation-rules
  (is (thrown? Exception (r/register-maintenance-schedule "" "reactor-1" "2026-08-01T09:00:00Z" nil 0)))
  (is (thrown? Exception (r/register-maintenance-schedule "mnt-1" "" "2026-08-01T09:00:00Z" nil 0)))
  (is (thrown? Exception (r/register-maintenance-schedule "mnt-1" "reactor-1" "" nil 0)))
  (is (thrown? Exception (r/register-maintenance-schedule "mnt-1" "reactor-1" "2026-08-01T09:00:00Z" nil -1))))

(deftest history-is-append-only
  (let [c1 (r/register-batch-log "batch-1" "JPN" 0)
        hist (r/append [] c1)
        c2 (r/register-batch-log "batch-2" "JPN" 1)
        hist2 (r/append hist c2)]
    (is (= 2 (count hist2)))
    (is (= "JPN-BCM-000000" (get-in hist2 [0 "record_id"])))
    (is (= "JPN-BCM-000001" (get-in hist2 [1 "record_id"])))))

;; ----------------------------- required-field predicates -----------------------------

(deftest maintenance-fields-present-needs-both-required-keys
  (is (r/maintenance-fields-present? {:maintenance/equipment-id "reactor-1" :maintenance/scheduled-at-iso "2026-08-01T00:00:00Z"}))
  (is (not (r/maintenance-fields-present? {:maintenance/equipment-id "reactor-1"})))
  (is (not (r/maintenance-fields-present? {}))))

(deftest safety-concern-fields-present-needs-both-required-keys
  (is (r/safety-concern-fields-present? {:safety-concern/description "leak risk" :safety-concern/severity :high}))
  (is (not (r/safety-concern-fields-present? {:safety-concern/severity :high})))
  (is (not (r/safety-concern-fields-present? {}))))

(deftest handoff-fields-present-needs-all-three-identity-keys
  (is (r/handoff-fields-present? {:handoff/id "ho-1" :handoff/source-actor "cloud-itonami-isic-2011" :handoff/batch-id "batch-1"}))
  (is (not (r/handoff-fields-present? {:handoff/id "ho-1" :handoff/source-actor "cloud-itonami-isic-2011"})))
  (is (not (r/handoff-fields-present? {}))))
