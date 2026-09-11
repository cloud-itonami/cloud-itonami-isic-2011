(ns basicchem.sim
  "Demo driver -- `clojure -M:dev:run`. Walks a clean batch through
  log -> human approval -> commit, then release -> human approval ->
  commit (attaching an OPTIONAL `:handoff` to the downstream refrigerant-
  compressor manufacturer cloud-itonami-isic-2813, ADR-2800000500),
  then shows four HARD holds (a jurisdiction with no spec-basis, an
  out-of-window reaction temperature, an unresolved process-safety
  concern blocking release, and a double release of an already-
  released batch) that never reach a human at all, then a routine
  maintenance schedule that AUTO-COMMITS (the one op in this domain
  with no direct capital/product-safety risk), and prints the audit
  ledger + the draft batch-log/batch-release/maintenance-schedule
  records."
  (:require [langgraph.graph :as g]
            [basicchem.store :as store]
            [basicchem.operation :as op]))

(def operator {:actor-id "op-1" :actor-role :chemical-safety-engineer :phase 3})

(defn- exec! [actor tid request context]
  (g/run* actor {:request request :context context} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}} {:thread-id tid :resume? true}))

(defn -main [& _]
  (let [db (store/seed-db)
        actor (op/build db)]
    (println "== log-production-batch batch-1 (JPN, clean; reaction temp within spec) ==")
    (println (exec! actor "t1" {:op :log-production-batch :subject "batch-1"} operator))
    (println "-- human chemical-safety engineer approves --")
    (println (approve! actor "t1"))

    (println "== release-batch batch-1 (always escalates -- release-batch; attaches optional :handoff to cloud-itonami-isic-2813) ==")
    (let [handoff {:handoff/id "ho-2011-1"
                   :handoff/source-actor "cloud-itonami-isic-2011"
                   :handoff/batch-id "batch-1"
                   :handoff/product-type-id :product/r448a-refrigerant-blend
                   :handoff/quantity-kg 500.0
                   :handoff/unspsc-code "12352101"
                   :handoff/gtin "0212011000010"
                   :handoff/dispatched-at-iso "2026-07-18T00:00:00Z"}
          r (exec! actor "t2" {:op :release-batch :subject "batch-1" :handoff handoff} operator)]
      (println r)
      (println "-- human chemical-safety engineer approves --")
      (println (approve! actor "t2")))

    (println "== log-production-batch batch-2 (no spec-basis -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t3" {:op :log-production-batch :subject "batch-2" :no-spec? true} operator))

    (println "== log-production-batch batch-3 (JPN, escalates -- human approves; sets up the out-of-window release) ==")
    (println (exec! actor "t4" {:op :log-production-batch :subject "batch-3"} operator))
    (println (approve! actor "t4"))

    (println "== release-batch batch-3 (85.0C outside [40.0,60.0] reaction-temp window -> HARD hold) ==")
    (println (exec! actor "t5" {:op :release-batch :subject "batch-3"} operator))

    (println "== log-production-batch batch-4 (JPN, escalates -- human approves) ==")
    (println (exec! actor "t6" {:op :log-production-batch :subject "batch-4"} operator))
    (println (approve! actor "t6"))

    (println "== flag-safety-concern batch-4 (ALWAYS escalates, even though unresolved) ==")
    (println (exec! actor "t7" {:op :flag-safety-concern :subject "batch-4"
                                :description "反応温度異常上昇の兆候を検知" :severity :high :verdict :unresolved} operator))
    (println "-- human chemical-safety engineer approves the FLAG (not the underlying concern) --")
    (println (approve! actor "t7"))

    (println "== release-batch batch-4 (unresolved process-safety concern on file -> HARD hold, never reaches a human) ==")
    (println (exec! actor "t8" {:op :release-batch :subject "batch-4"} operator))

    (println "== release-batch batch-1 AGAIN (double-release -> HARD hold) ==")
    (println (exec! actor "t9" {:op :release-batch :subject "batch-1"} operator))

    (println "== schedule-maintenance reactor-1 (routine, no capital risk -> AUTO-COMMITS, no human needed) ==")
    (println (exec! actor "t10" {:op :schedule-maintenance :subject "mnt-1"
                                 :equipment-id "reactor-1" :scheduled-at-iso "2026-08-01T09:00:00Z"
                                 :description "定期反応器内部検査"} operator))

    (println "== flag-safety-concern with missing fields -> HARD hold ==")
    (println (exec! actor "t11" {:op :flag-safety-concern :subject "batch-1"} operator))

    (println "== audit ledger ==")
    (doseq [f (store/ledger db)] (println f))

    (println "== draft production-batch-log records ==")
    (doseq [r (store/log-history db)] (println r))

    (println "== draft batch-release records ==")
    (doseq [r (store/release-history db)] (println r))

    (println "== draft maintenance-schedule records ==")
    (doseq [r (store/maintenance-history db)] (println r))))
