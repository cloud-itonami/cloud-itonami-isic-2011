(ns basicchem.store-contract-test
  "The Store contract, run against BOTH backends. Proving MemStore and
  the Datomic-backed (langchain.db) store satisfy the same contract is
  what makes 'swap the SSoT for Datomic / kotoba-server' a
  configuration change, not a rewrite -- see `pressureequip.store-
  contract-test` (cloud-itonami-isic-2813) for the same pattern on the
  sibling actor."
  (:require [clojure.test :refer [deftest is testing]]
            [basicchem.store :as store]))

(defn- backends []
  [["MemStore" (store/seed-db)] ["DatomicStore" (store/datomic-seed-db)]])

(deftest read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= "R-448A ブレンドバッチ #1 (JPN, 清浄)" (:batch-name (store/batch s "batch-1"))))
      (is (= "JPN" (:jurisdiction (store/batch s "batch-1"))))
      (is (= 52.0 (:reaction-temp-actual-c (store/batch s "batch-1"))))
      (is (= 40.0 (:reaction-temp-min-c (store/batch s "batch-1"))))
      (is (= 60.0 (:reaction-temp-max-c (store/batch s "batch-1"))))
      (is (= 85.0 (:reaction-temp-actual-c (store/batch s "batch-3"))))
      (is (false? (:batch-logged? (store/batch s "batch-1"))))
      (is (false? (:batch-released? (store/batch s "batch-1"))))
      (is (= ["batch-1" "batch-2" "batch-3" "batch-4"]
             (mapv :id (store/all-batches s))))
      (is (nil? (store/safety-screen-of s "batch-1")))
      (is (= [] (store/ledger s)))
      (is (= [] (store/log-history s)))
      (is (= [] (store/release-history s)))
      (is (= [] (store/maintenance-history s)))
      (is (zero? (store/next-log-sequence s "JPN")))
      (is (zero? (store/next-release-sequence s "JPN")))
      (is (zero? (store/next-maintenance-sequence s)))
      (is (false? (store/batch-already-logged? s "batch-1")))
      (is (false? (store/batch-already-released? s "batch-1"))))))

(deftest write-and-ledger-parity
  (doseq [[label s] (backends)]
    (testing label
      (testing "batch/log merges evidence-checklist fields, preserving untouched ground-truth fields"
        (store/commit-record! s {:effect :batch/log :path ["batch-1"]
                                 :value {:jurisdiction "JPN" :evidence-checklist ["a" "b"] :spec-basis "src" :legal-basis "law"}})
        (is (= ["a" "b"] (:evidence-checklist (store/batch s "batch-1"))))
        (is (= 52.0 (:reaction-temp-actual-c (store/batch s "batch-1"))) "unrelated ground-truth field preserved")
        (is (true? (:batch-logged? (store/batch s "batch-1")))))
      (testing "batch-log drafts a record and advances the jurisdiction sequence"
        (is (= "JPN-BCM-000000" (get (first (store/log-history s)) "record_id")))
        (is (= "production-batch-log-draft" (get (first (store/log-history s)) "kind")))
        (is (= 1 (count (store/log-history s))))
        (is (= 1 (store/next-log-sequence s "JPN")))
        (is (true? (store/batch-already-logged? s "batch-1")))
        (is (false? (store/batch-already-logged? s "batch-2"))))
      (testing "batch/release drafts a record and advances the jurisdiction sequence, no handoff"
        (store/commit-record! s {:effect :batch/release :path ["batch-1"] :value {}})
        (is (= "JPN-REL-000000" (get (first (store/release-history s)) "record_id")))
        (is (= "batch-release-draft" (get (first (store/release-history s)) "kind")))
        (is (not (contains? (first (store/release-history s)) "handoff")))
        (is (true? (:batch-released? (store/batch s "batch-1"))))
        (is (= 1 (count (store/release-history s))))
        (is (= 1 (store/next-release-sequence s "JPN")))
        (is (true? (store/batch-already-released? s "batch-1")))
        (is (false? (store/batch-already-released? s "batch-2"))))
      (testing "safety-concern/flag commits the screen and reads back"
        (store/commit-record! s {:effect :safety-concern/flag :path ["batch-4"]
                                 :value {:safety-concern/batch-id "batch-4"
                                         :safety-concern/description "leak risk"
                                         :safety-concern/severity :high
                                         :safety-concern/verdict :unresolved}})
        (is (= :unresolved (:safety-concern/verdict (store/safety-screen-of s "batch-4")))))
      (testing "maintenance/schedule drafts a record and advances the GLOBAL (non-jurisdiction) sequence"
        (store/commit-record! s {:effect :maintenance/schedule :path ["mnt-1"]
                                 :value {:maintenance/equipment-id "reactor-1" :maintenance/scheduled-at-iso "2026-08-01T00:00:00Z"}})
        (is (= "MNT-000000" (get (first (store/maintenance-history s)) "record_id")))
        (is (= "maintenance-schedule-draft" (get (first (store/maintenance-history s)) "kind")))
        (is (= 1 (count (store/maintenance-history s))))
        (is (= 1 (store/next-maintenance-sequence s))))
      (testing "ledger is append-only and order-preserving"
        (store/append-ledger! s {:op :a :disposition :commit})
        (store/append-ledger! s {:op :b :disposition :hold})
        (is (= [:commit :hold] (mapv :disposition (store/ledger s))))))))

(deftest release-with-handoff-store-parity
  (testing "batch/release embeds a :handoff, when present, on the drafted release record"
    (doseq [[label s] (backends)]
      (testing label
        (store/commit-record! s {:effect :batch/log :path ["batch-1"]
                                 :value {:jurisdiction "JPN" :evidence-checklist ["a"] :spec-basis "src" :legal-basis "law"}})
        (store/commit-record! s {:effect :batch/release :path ["batch-1"]
                                 :value {:handoff {:handoff/id "ho-1"
                                                    :handoff/source-actor "cloud-itonami-isic-2011"
                                                    :handoff/batch-id "batch-1"}}})
        (is (= {:handoff/id "ho-1" :handoff/source-actor "cloud-itonami-isic-2011" :handoff/batch-id "batch-1"}
               (get (first (store/release-history s)) "handoff")))))))

(deftest product-type-id-read-parity
  (doseq [[label s] (backends)]
    (testing label
      (is (= :product/r448a-refrigerant-blend (:product-type-id (store/batch s "batch-1")))
          "the demo batch's optional product-types catalog reference round-trips on both backends")
      (is (nil? (:product-type-id (store/batch s "batch-2")))
          "a batch with no declared product-type-id stays nil, not fabricated"))))

(deftest datomic-empty-store-is-usable
  (let [s (store/datomic-store)]
    (is (nil? (store/batch s "nope")))
    (is (= [] (store/all-batches s)))
    (is (= [] (store/ledger s)))
    (is (= [] (store/log-history s)))
    (is (= [] (store/release-history s)))
    (is (= [] (store/maintenance-history s)))
    (is (zero? (store/next-log-sequence s "JPN")))
    (is (zero? (store/next-release-sequence s "JPN")))
    (is (zero? (store/next-maintenance-sequence s)))
    (store/with-batches s {"x" {:id "x" :batch-name "n" :reaction-temp-actual-c 52.0
                                :reaction-temp-min-c 40.0 :reaction-temp-max-c 60.0
                                :batch-logged? false :batch-released? false
                                :jurisdiction "JPN" :status :intake}})
    (is (= "n" (:batch-name (store/batch s "x"))))))
