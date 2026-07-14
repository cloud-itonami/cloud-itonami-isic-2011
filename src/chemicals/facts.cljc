(ns chemicals.facts
  "Per-jurisdiction basic chemicals manufacturing safety and emissions requirements.
  Every jurisdiction in this catalog is backed by an official spec-basis.
  NEVER invent requirements without an official citation.

  This is deliberately a starting catalog (honest coverage reporting) to
  prove the governor contract end-to-end, not a claim of global coverage.
  Adding a jurisdiction is additive: one map entry citing a real official
  source -- never fabricate a jurisdiction's requirements to make coverage
  look bigger.")

;; ----------------------------- jurisdiction catalog -----------------------------

(def catalog
  "Per-jurisdiction basic chemicals manufacturing requirements with official spec-basis citations."
  {
   :JPN
   {:name "Japan"
    :requirements
    {:feedstock-verification {:description "Chemical feedstock supplier registration and batch verification"
                             :required true
                             :spec-basis "High Pressure Gas Safety Law (高圧ガス保安法) §2"
                             :evidence [:supplier-license :feedstock-analysis-report :quality-cert]}
     :process-safety {:description "Chemical process safety management and hazard analysis"
                     :required true
                     :spec-basis "Industrial Safety and Health Act (労働安全衛生法) §57の3"
                     :evidence [:process-safety-plan :hazard-analysis :design-review]}
     :emissions-monitoring {:description "Continuous emissions monitoring for VOC, NOx, particulates"
                           :required true
                           :spec-basis "Air Pollution Control Law (大気汚染防止法) §3"
                           :evidence [:emissions-baseline :monitoring-equipment-cert]}
     :waste-disposal {:description "Chemical waste disposal and hazardous waste inventory"
                     :required true
                     :spec-basis "Waste Disposal and Public Cleaning Law (廃棄物の処理及び清掃に関する法律) §12"
                     :evidence [:waste-management-plan :disposal-facility-cert]}}}

   :USA
   {:name "United States"
    :requirements
    {:feedstock-verification {:description "Chemical feedstock supplier qualification and material specification"
                             :required true
                             :spec-basis "OSHA Hazard Communication Standard 29 CFR 1910.1200"
                             :evidence [:supplier-cert :material-spec-sheet :sds-cert]}
     :process-safety {:description "Process Safety Management (PSM) under OSHA 1910.119"
                     :required true
                     :spec-basis "OSHA 1910.119 Process Safety Management"
                     :evidence [:pss-plan :process-hazard-analysis :training-records]}
     :emissions-monitoring {:description "EPA emissions monitoring under Clean Air Act Title V"
                           :required true
                           :spec-basis "Clean Air Act Title V (42 USC §7661)"
                           :evidence [:emissions-permit :monitoring-plan]}
     :waste-disposal {:description "EPA hazardous waste management and disposal compliance"
                     :required true
                     :spec-basis "Resource Conservation and Recovery Act (RCRA) 40 CFR Part 262"
                     :evidence [:waste-manifest :disposal-permit]}}}

   :GBR
   {:name "United Kingdom"
    :requirements
    {:feedstock-verification {:description "Chemical feedstock supply chain compliance and quality assurance"
                             :required true
                             :spec-basis "Control of Substances Hazardous to Health (COSHH) Regulations 2002"
                             :evidence [:supplier-audit :quality-record :risk-assessment]}
     :process-safety {:description "Major Accident Hazard (MAH) Regulations for process safety"
                     :required true
                     :spec-basis "Control of Major Accident Hazards (COMAH) Regulations 2015"
                     :evidence [:safety-report :risk-assessment :training-plan]}
     :emissions-monitoring {:description "Emissions monitoring under Environmental Permitting"
                           :required true
                           :spec-basis "Environmental Permitting (England and Wales) Regulations 2016"
                           :evidence [:environmental-permit :monitoring-schedule]}
     :waste-disposal {:description "Hazardous waste disposal under Environmental Protection Act"
                     :required true
                     :spec-basis "Environmental Protection Act 1990 Part II"
                     :evidence [:waste-manifest :disposal-facility-license]}}}})

;; ----------------------------- coverage reporting (honest) -----------------------------

(defn coverage
  "Report what fraction of worldwide jurisdictions have official spec-basis
  in this catalog. Honest about out-of-scope coverage."
  []
  (let [catalog-count (count catalog)
        world-jurisdictions 194]
    {:implemented catalog-count
     :worldwide-jurisdictions world-jurisdictions
     :coverage-pct (* 100.0 (/ catalog-count world-jurisdictions))
     :note "Starting catalog to prove governor contract end-to-end, not global coverage claim"}))

;; ----------------------------- helpers -----------------------------

(defn requirement-citations
  "Get all official citations for a jurisdiction's requirements."
  [jurisdiction]
  (get-in catalog [jurisdiction :requirements]))

(defn required-evidence-satisfied?
  "Check if a checklist satisfies this jurisdiction's evidence requirements."
  [jurisdiction checklist]
  (let [reqs (get-in catalog [jurisdiction :requirements])]
    (every? (fn [[_req-key req-spec]]
              (if (:required req-spec)
                (let [evidence-keys (set (:evidence req-spec))]
                  (every? #(contains? checklist %) evidence-keys))
                true))
            reqs)))
