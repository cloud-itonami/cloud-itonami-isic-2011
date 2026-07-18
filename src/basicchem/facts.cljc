(ns basicchem.facts
  "Per-jurisdiction basic-chemicals-manufacturing regulatory evidence
  catalog -- the G2-style spec-basis table the Chemical Safety
  Governor checks every `:log-production-batch`/`:release-batch`
  proposal against.

  Coverage is reported HONESTLY: a jurisdiction not in this table has
  NO spec-basis. Seed values cite official chemical-safety/
  environmental-release authorities: Japan's 化審法 (Act on the
  Evaluation of Chemical Substances and Regulation of Their
  Manufacture, etc.) + PRTR法 (化学物質排出把握管理促進法, administered
  by 経済産業省/METI + 環境省/MOE), the US EPA's TSCA (Toxic Substances
  Control Act) Chemical Data Reporting + premanufacture-notice regime,
  the EU/Germany's REACH Regulation (EC) No 1907/2006 (registration,
  evaluation, authorisation and restriction of chemicals, administered
  via ECHA) plus the EU F-gas Regulation (EU) 2024/573 (fluorinated
  greenhouse gases, directly relevant to a hydrofluorocarbon/
  hydrofluoroolefin refrigerant blend), and the UK's retained UK REACH
  (HSE-administered, via The REACH etc. (Amendment etc.) (EU Exit)
  Regulations 2019) plus its own retained GB F-gas regime -- this is a
  starting catalog, not a survey of every market.")

(def catalog
  {"JPN" {:name "Japan"
          :owner-authority "経済産業省 (METI) / 環境省 (MOE)"
          :legal-basis "化学物質の審査及び製造等の規制に関する法律(化審法) / 特定化学物質の環境への排出量の把握等及び管理の改善の促進に関する法律(化学物質排出把握管理促進法・PRTR法)"
          :national-spec "新規化学物質の事前審査・製造/輸入量の届出、第一種指定化学物質の環境排出量算定・届出、安全データシート(SDS)提供義務"
          :provenance "https://www.meti.go.jp/policy/chemical_management/kasinhou/index.html"
          :required-evidence ["化審法新規化学物質事前審査記録 (chemical-substance-notification-record)"
                              "PRTR排出量算定・届出記録 (prtr-emission-report)"
                              "製造・輸入実績記録 (manufacture-import-volume-record)"
                              "安全データシート(SDS)提供記録 (sds-provision-record)"]}
   "USA" {:name "United States"
          :owner-authority "EPA (Environmental Protection Agency), Office of Pollution Prevention and Toxics"
          :legal-basis "Toxic Substances Control Act (TSCA), 15 U.S.C. §2601 et seq. (premanufacture notice review / Chemical Data Reporting)"
          :national-spec "premanufacture notice (PMN) review prior to manufacture of a new chemical substance, periodic Chemical Data Reporting (CDR) of production/import volume, GHS-aligned SDS (29 CFR 1910.1200) provision"
          :provenance "https://www.epa.gov/assessing-and-managing-chemicals-under-tsca"
          :required-evidence ["premanufacture-notice-record"
                              "chemical-data-reporting-record"
                              "manufacture-import-volume-record"
                              "sds-provision-record"]}
   "DEU" {:name "Germany"
          :owner-authority "ECHA (European Chemicals Agency) / REACH-Verordnung EU-weiter Vollzug"
          :legal-basis "REACH-Verordnung (EG) Nr. 1907/2006 (Registration, Evaluation, Authorisation and Restriction of Chemicals) / F-Gas-Verordnung (EU) 2024/573 (fluorierte Treibhausgase)"
          :national-spec "Registrierung(ab 1 Tonne/Jahr)・Bewertung・Zulassung・Beschränkung von Chemikalien, SDB(Sicherheitsdatenblatt)-Bereitstellungspflicht, F-Gas-Quoten-/Meldepflicht für fluorierte Kältemittel"
          :provenance "https://echa.europa.eu/regulations/reach"
          :required-evidence ["REACH登録記録 (reach-registration-record)"
                              "安全データシート(SDB/SDS)提供記録 (sds-provision-record)"
                              "F-gas規則(EU)2024/573対象物質報告記録 (f-gas-reporting-record)"
                              "製造・輸入実績記録 (manufacture-import-volume-record)"]}
   "GBR" {:name "United Kingdom"
          :owner-authority "HSE (Health and Safety Executive) -- UK REACHのUK化学物質庁"
          :legal-basis "UK REACH (The REACH etc. (Amendment etc.) (EU Exit) Regulations 2019による、EU REACH規則(EC) No 1907/2006のGB国内法化) / GB F-gas Regulations(EU F-gas規則の英国retained版)"
          :national-spec "UK域内での化学物質の登録・評価・認可・制限、SDS提供義務、フッ素系温室効果ガス(F-gas)のGB上市クオータ・報告義務"
          :provenance "https://www.hse.gov.uk/reach/"
          :required-evidence ["UK REACH登録記録 (uk-reach-registration-record)"
                              "安全データシート(SDS)提供記録 (sds-provision-record)"
                              "GB F-gas上市クオータ報告記録 (f-gas-reporting-record)"
                              "製造・輸入実績記録 (manufacture-import-volume-record)"]}})

(defn spec-basis [iso3] (get catalog iso3))

(def product-types
  "Catalog of concrete manufactured PRODUCT MODELS this actor's batches
  can declare a `:product-type-id` reference to (a SEPARATE catalog
  from `catalog` above, which is per-JURISDICTION regulatory evidence,
  not per-PRODUCT spec data -- this catalog does not replace or alter
  `catalog`).

  Two classification fields on each entry come from EXTERNAL,
  independently-verifiable authorities and are reported HONESTLY per
  this fleet's anti-fabrication discipline (see ns docstring / the
  UNSPSC/GTIN linkage precedent this ns follows,
  `pressureequip.facts/unit-types` in cloud-itonami-isic-2813):

    `:unspsc-code` -- an 8-digit UNSPSC (United Nations Standard
    Products and Services Code) COMMODITY code. UNSPSC has NO
    dedicated 'refrigerant blend' commodity: R-448A is a multi-
    component hydrofluorocarbon/hydrofluoroolefin (HFC-32/HFC-125/
    HFC-134a + HFO-1234yf/1234ze-family) blend with no single CAS
    number, so no exact match exists. The closest real UNSPSC
    commodity, confirmed via independent public commodity-code
    references (the O*NET/Oklahoma Open Data UNSPSC full codeset CSV,
    cross-checked against usa.databasesets.com's own 'UNSPSC Codes
    Class: 12352100' page), is `12352101` ('Organic halogenated
    compounds', under class 123521 'Organic derivatives and
    substituted compounds', family 1235 'Compounds and mixtures',
    segment 12 'Chemicals including Bio Chemicals and Gas Materials')
    -- a real, published commodity for fluorine/halogen-substituted
    organic chemicals, which R-448A's own components are. This
    namespace does NOT fabricate a more specific 8-digit code UNSPSC
    itself does not publish (the SAME honesty discipline
    `pressureequip.facts/unit-types` established for
    `40101704`/'Condensing units').

    `:gtin` -- a GTIN (Global Trade Item Number) is NOT a
    classification taxonomy code at all -- it is an identifier GS1
    issues per REGISTERED PHYSICAL PRODUCT, only after a real company
    enrolls with GS1 and assigns it. This catalog is a products-catalog
    seed, not a GS1 registration record, so `:gtin` is a SYNTACTICALLY
    VALID but NEVER-ISSUED placeholder: built on GS1's own officially-
    documented 'Restricted Circulation Number' (RCN) prefix range
    '020'-'029' (GS1 GSCN-23-006-RCN / gs1.org 'GS1 Company Prefix'
    docs) -- a prefix range GS1 itself reserves for company-internal/
    restricted use -- with a correctly computed Modulo-10 GTIN-13
    check digit (same algorithm `pressureequip.facts/unit-types`
    verified against the standard EAN-13 worked example
    400638133393->1; this catalog's own `0212011000010` payload
    `021201100001` independently verified to check-digit 0). The
    sibling key `:gtin/status :unissued-blueprint-placeholder` makes
    the non-issuance explicit and machine-checkable; treat `:gtin`
    here as an EXAMPLE VALUE ONLY, never as a real, GS1-issued
    identifier for an actual batch/product."
  {:product/r448a-refrigerant-blend
   {:id :product/r448a-refrigerant-blend
    :name "R-448A低GWP混合冷媒 (業務用冷凍・冷蔵設備向けHFC/HFOブレンド)"
    :composition-note "単一CAS番号を持たない多成分ブレンド(ASHRAE命名 R-448A、HFC-32/HFC-125/HFC-134a + HFO-1234yf/1234ze系成分)"
    :unspsc-code "12352101"
    :gtin "0212011000010"
    :gtin/status :unissued-blueprint-placeholder}})

(defn product-type-by-id [id]
  (get product-types id))

(defn coverage
  ([] (coverage (keys catalog)))
  ([iso3s]
   (let [have (filter catalog iso3s)
         missing (remove catalog iso3s)]
     {:requested (count iso3s)
      :covered (count have)
      :covered-jurisdictions (vec (sort have))
      :missing-jurisdictions (vec (sort missing))
      :note (str "cloud-itonami-isic-2011 R0: " (count catalog)
                 " jurisdictions seeded. Extend `basicchem.facts/catalog`, "
                 "never fabricate a jurisdiction's requirements.")})))

(defn required-evidence-satisfied?
  [iso3 submitted]
  (when-let [{:keys [required-evidence]} (spec-basis iso3)]
    (let [need (count required-evidence)
          have (count (filter (set submitted) required-evidence))]
      (= need have))))

(defn evidence-checklist [iso3]
  (:required-evidence (spec-basis iso3) []))
