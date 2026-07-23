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
  hydrofluoroolefin refrigerant blend), the UK's retained UK REACH
  (HSE-administered, via The REACH etc. (Amendment etc.) (EU Exit)
  Regulations 2019) plus its own retained GB F-gas regime, and Mexico's
  Ley General del Equilibrio Ecológico y la Protección al Ambiente
  (LGEEPA) -- Capítulo V, Actividades Consideradas como Altamente
  Riesgosas (Artículos 145 a 149 y 147 BIS: SEMARNAT-administered
  classification of highly-hazardous chemical activities, mandatory
  estudio de riesgo ambiental + accident-prevention program before
  operating, and environmental-risk insurance via the Sistema Nacional
  de Seguros de Riesgo Ambiental) plus Artículo 109 BIS (the Registro
  de Emisiones y Transferencia de Contaminantes / RETC
  pollutant-release-and-transfer register) -- both articles confirmed
  directly against the current 'TEXTO VIGENTE' text (última reforma
  DOF 19-01-2026) fetched from diputados.gob.mx this session. Mexico's
  entry intentionally does NOT cite a national SDS-equivalent standard
  or a production/import-volume-reporting duty the way JPN/USA/DEU/GBR
  do above: no source fetched this session confirmed a specific
  Mexican citation for either (a candidate would be an STPS
  hazard-communication NOM, but that was not independently verified),
  so none is fabricated here -- this is a starting catalog, not a
  survey of every market.

  South Korea's entry cites TWO Acts administered by the same ministry,
  mirroring JPN's 化審法+PRTR法 pair: the Act on Registration and
  Evaluation of Chemical Substances (화학물질의 등록 및 평가 등에 관한
  법률, 화평법/K-REACH, Act No. 19964) Article 10 (registration
  threshold: at least 100kg/year of a 'non-phase-in substance' or at
  least 1 ton/year of a 'phase-in substance' must be registered with
  the Minister of Environment before manufacture/import) and Article 2
  (definitions of 'hazardous chemical substance' as the union of toxic
  substances/substances subject to permission/restricted substances/
  prohibited substances), plus the Chemical Substances Control Act
  (화학물질관리법, 화관법/CSCA, Act No. 20231) Article 10 (a statistical
  survey of chemical-substance handling volume every two years) Article
  11 (Pollutant Release and Transfer Registers -- 'the Minister of
  Environment shall implement surveys for the pollutant release and
  transfer registers to track chemical releases from the process of
  handling the relevant chemical substances at the places of business')
  and Article 16 (GHS-style container/package labeling of hazardous
  chemical substances: pictogram, signal word, hazard/precautionary
  phrases, supplier information) -- both fetched and read directly
  from elaw.klri.re.kr (Korea Legislation Research Institute's official
  English statute-translation portal) this session:
  https://elaw.klri.re.kr/eng_service/lawView.do?hseq=68246&lang=ENG
  (K-REACH, the `:provenance` value below) and
  https://elaw.klri.re.kr/eng_service/lawView.do?hseq=64986&lang=ENG
  (CSCA). The administering
  ministry itself was renamed in the interim: the Act text says
  '환경부/Minister of Environment', but me.go.kr now redirects to
  mcee.go.kr whose English homepage title is 'Ministry of Climate,
  Energy and Environment' (기후에너지환경부) -- confirmed directly this
  session, both names refer to the same ministry. K-REACH Article 48
  names the National Institute of Environmental Research (국립환경과학원/
  NIER, nier.go.kr title confirmed this session) and the National
  Institute of Chemical Safety (화학물질안전원/NICS) as the ministry's
  delegates for authority under the Act. Two honest gaps: (1) this
  entry does NOT include a Korean sds-provision-record analog -- the
  K-REACH text itself defers the actual material-safety-data-sheet
  duty to Article 110/111 of the Occupational Safety and Health Act,
  a DIFFERENT act administered by a different ministry (Employment and
  Labor) that was not independently fetched/verified this session, so
  it is not cited here (same non-fabrication discipline as MEX's SDS
  gap above); (2) the CSCA (last English amendment shown: Feb. 6, 2024)
  cross-references K-REACH definitions using newer subparagraph
  numbers/terms ('substance acutely/chronically hazardous to human
  health', 6-2, 6-3) that do NOT appear in the K-REACH English
  translation fetched this session (last amendment shown: Jan. 9,
  2024, still using 'toxic substance' at subparagraph 6) -- this looks
  like an elaw English-translation lag behind a later Korean-only
  K-REACH amendment, not a contradiction; this entry only cites the
  K-REACH wording actually read this session.")

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
                              "製造・輸入実績記録 (manufacture-import-volume-record)"]}
   "MEX" {:name "Mexico"
          :owner-authority "Secretaría de Medio Ambiente y Recursos Naturales (SEMARNAT)"
          :legal-basis "Ley General del Equilibrio Ecológico y la Protección al Ambiente (LGEEPA), Título Cuarto Capítulo V, Actividades Consideradas como Altamente Riesgosas (Artículos 145 a 149 y 147 BIS) / Artículo 109 BIS (Registro de Emisiones y Transferencia de Contaminantes, RETC)"
          :national-spec "clasificación de actividades industriales・comerciales・de servicios como altamente riesgosas en función de sustancias corrosivas・reactivas・explosivas・tóxicas・inflamables・biológico-infecciosas (Art. 146), estudio de riesgo ambiental y programa de prevención de accidentes previo a la operación (Art. 147), seguro de riesgo ambiental mediante el Sistema Nacional de Seguros de Riesgo Ambiental (Art. 147 BIS), integración al Registro de Emisiones y Transferencia de Contaminantes/RETC (Art. 109 BIS)"
          :provenance "https://www.diputados.gob.mx/LeyesBiblio/pdf/LGEEPA.pdf"
          :required-evidence ["estudio de riesgo ambiental (environmental-risk-study-record)"
                              "programa de prevención de accidentes (accident-prevention-program-record)"
                              "seguro de riesgo ambiental / Sistema Nacional de Seguros de Riesgo Ambiental (environmental-risk-insurance-record)"
                              "Registro de Emisiones y Transferencia de Contaminantes / RETC (pollutant-release-transfer-registry-record)"]}
   "KOR" {:name "South Korea"
          :owner-authority "환경부 (Ministry of Environment; 2025년 정부조직 개편 이후 기후에너지환경부/Ministry of Climate, Energy and Environment) -- 국립환경과학원(NIER)・화학물질안전원(NICS)에 권한 위임(화평법 제48조)"
          :legal-basis "화학물질의 등록 및 평가 등에 관한 법률(화평법・K-REACH, 법률 제19964호) / 화학물질관리법(화관법, 법률 제20231호)"
          :national-spec "연간 1톤 이상 기존화학물질(phase-in substance) 또는 연간 100킬로그램 이상 신규화학물질(non-phase-in substance)을 제조・수입하려는 자의 등록 의무(화평법 제10조), 유독물질・허가물질・제한물질・금지물질로 구성되는 유해화학물질의 정의(화평법 제2조), 화학물질 취급현황에 대한 2년 주기 통계조사(화관법 제10조), 화학물질 배출량조사 및 등록(PRTR, 화관법 제11조), 유해화학물질 용기・포장에 대한 그림문자・신호어 등 표시 의무(화관법 제16조)"
          :provenance "https://elaw.klri.re.kr/eng_service/lawView.do?hseq=68246&lang=ENG"
          :required-evidence ["화평법 제10조 신규・기존화학물질 등록 기록 (chemical-substance-registration-record)"
                              "화관법 제10조 화학물질 취급현황 통계조사 기록 (manufacture-import-volume-record)"
                              "화관법 제11조 화학물질 배출량조사・등록 기록 (pollutant-release-transfer-registry-record)"
                              "화관법 제16조 유해화학물질 표시(그림문자・신호어) 기록 (hazardous-chemical-labeling-record)"]}})

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
