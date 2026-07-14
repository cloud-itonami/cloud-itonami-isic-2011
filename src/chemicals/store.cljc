(ns chemicals.store
  "In-memory store for basic chemicals plant operations state.
  This is a reference implementation; production systems would use Datomic
  or similar persistent event store for audit and replay.")

;; ----------------------------- store initialization -----------------------------

(defn mem-store
  "Create an in-memory store with reference data for basic chemicals manufacturing.
  Includes feedstock suppliers, chemical batches, reaction schedules, and emissions reports."
  []
  {:data (atom {
           :suppliers {
             "supplier-001" {:name "Industrial Chemicals Feed Ltd"
                            :license-verified? true
                            :jurisdiction :JPN}
             "supplier-002" {:name "Chemical Corp USA"
                            :license-verified? true
                            :jurisdiction :USA}}
           :feedstock {
             "feedstock-batch-001" {:supplier "supplier-001"
                                    :chemical-type "ammonia-feed"
                                    :quality-grade "industrial"
                                    :verified? true}}
           :reaction-schedules {
             "reaction-001" {:status :scheduled
                            :feedstock "feedstock-batch-001"
                            :target-product "ammonia"
                            :scheduled-start "2026-07-15T09:00:00Z"}}
           :emissions-reports {
             "report-001" {:date "2026-07-14"
                          :voc-ppm 0.5
                          :nox-ppm 0.8
                          :threshold-voc 1.0
                          :threshold-nox 1.5
                          :compliant? true}}})})

;; ----------------------------- accessors -----------------------------

(defn supplier
  "Get supplier record by ID."
  [st supplier-id]
  (get-in @(:data st) [:suppliers supplier-id]))

(defn feedstock
  "Get feedstock (chemical batch) record by ID."
  [st feedstock-id]
  (get-in @(:data st) [:feedstock feedstock-id]))

(defn reaction-schedule
  "Get reaction schedule record by ID."
  [st schedule-id]
  (get-in @(:data st) [:reaction-schedules schedule-id]))

(defn emissions-report
  "Get emissions report by ID."
  [st report-id]
  (get-in @(:data st) [:emissions-reports report-id]))

;; ----------------------------- guards -----------------------------

(defn supplier-verified?
  "Check if supplier license is verified."
  [st supplier-id]
  (let [s (supplier st supplier-id)]
    (:license-verified? s false)))

(defn feedstock-verified?
  "Check if feedstock batch is verified."
  [st feedstock-id]
  (let [f (feedstock st feedstock-id)]
    (:verified? f false)))

(defn emissions-exceeds-threshold?
  "Check if emissions report shows any threshold exceedance."
  [st report-id]
  (let [r (emissions-report st report-id)]
    (or (> (:voc-ppm r 0) (:threshold-voc r 1.0))
        (> (:nox-ppm r 0) (:threshold-nox r 1.5)))))
