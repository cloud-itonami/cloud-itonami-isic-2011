(ns basicchem.render-html
  "Build-time HTML renderer for `docs/samples/operator-console.html`.

  Closes flagship checklist item 2 (com-junkawasaki/root ADR-2607189300,
  Wave5 rollout ledger seq 6): this repo previously had NO demo page and
  no generator at all. This namespace drives the REAL actor stack
  (`basicchem.operation` -> `basicchem.governor` -> `basicchem.store`)
  through a scenario adapted from this repo's own `basicchem.sim` demo
  driver (`clojure -M:dev:run`, confirmed by actually RUNNING it before
  this file was written -- its printed audit ledger matches this repo's
  own `store/demo-data` ids exactly (batch-1..4, mnt-1/reactor-1), unlike
  `cloud-itonami-isic-851`'s `schoolops.sim`, which was a latent bug
  copy-pasted from an unrelated actor -- so it was safe to mine directly
  rather than author a scenario from scratch), rendered deterministically
  -- no invented numbers, no timestamps in the page content,
  byte-identical across reruns against the same seed (verified by
  diffing two consecutive runs).

  Usage: `clojure -M:dev:render-html [out-file]`
  (default `docs/samples/operator-console.html`)."
  (:require [clojure.string :as str]
            [basicchem.store :as store]
            [basicchem.operation :as op]
            [langgraph.graph :as g]))

;; ----------------------------- harness -----------------------------

(def ^:private operator
  {:actor-id "op-1" :actor-role :chemical-safety-engineer :phase 3})

(defn- exec! [actor tid request]
  (g/run* actor {:request request :context operator} {:thread-id tid}))

(defn- approve! [actor tid]
  (g/run* actor {:approval {:status :approved :by "op-1"}}
          {:thread-id tid :resume? true}))

(defn run-demo!
  "Runs a fresh seeded store through a scenario mixing every disposition
  this actor can reach, adapted from `basicchem.sim`'s own (verified
  correct) scenario:

    - batch-1 (JPN, clean): logged (ALWAYS escalates -- approved), then
      released to market with an optional `:handoff` to the downstream
      refrigerant-compressor manufacturer cloud-itonami-isic-2813
      (ALWAYS escalates -- approved).
    - batch-2 (`:no-spec?` injected, i.e. an unregistered 'ATL'
      jurisdiction): HARD-holds `:log-production-batch` on
      `:no-spec-basis` -- never reaches a human.
    - batch-3 (JPN, logged clean -- approved) then HARD-holds
      `:release-batch` on `:reaction-temp-out-of-range` (measured
      85.0C outside its own recorded [40.0,60.0] spec window) --
      never reaches a human.
    - batch-4 (JPN, logged clean -- approved), flagged with an
      `:unresolved` process-safety concern (ALWAYS escalates -- a human
      approves the FLAG itself, not the underlying concern), then
      HARD-holds `:release-batch` on `:safety-concern-unresolved` --
      never reaches a human.
    - batch-1 AGAIN: HARD-holds `:release-batch` on `:already-released`
      (double-release guard) -- never reaches a human.
    - mnt-1/reactor-1: `:schedule-maintenance` AUTO-COMMITS at phase 3
      (the one op in this domain with no direct capital/product-safety
      risk) -- no human needed.

  Returns the resulting store -- every field `render` below reads is
  real governor/store output, not a hand-typed copy."
  []
  (let [db (store/seed-db)
        actor (op/build db)]
    (exec! actor "batch-1-log" {:op :log-production-batch :subject "batch-1"})
    (approve! actor "batch-1-log")

    (let [handoff {:handoff/id "ho-2011-1"
                   :handoff/source-actor "cloud-itonami-isic-2011"
                   :handoff/batch-id "batch-1"
                   :handoff/product-type-id :product/r448a-refrigerant-blend
                   :handoff/quantity-kg 500.0
                   :handoff/unspsc-code "12352101"
                   :handoff/gtin "0212011000010"
                   :handoff/dispatched-at-iso "2026-07-18T00:00:00Z"}]
      (exec! actor "batch-1-release" {:op :release-batch :subject "batch-1" :handoff handoff})
      (approve! actor "batch-1-release"))

    (exec! actor "batch-2-log" {:op :log-production-batch :subject "batch-2" :no-spec? true})

    (exec! actor "batch-3-log" {:op :log-production-batch :subject "batch-3"})
    (approve! actor "batch-3-log")
    (exec! actor "batch-3-release" {:op :release-batch :subject "batch-3"})

    (exec! actor "batch-4-log" {:op :log-production-batch :subject "batch-4"})
    (approve! actor "batch-4-log")
    (exec! actor "batch-4-flag" {:op :flag-safety-concern :subject "batch-4"
                                 :description "反応温度異常上昇の兆候を検知"
                                 :severity :high :verdict :unresolved})
    (approve! actor "batch-4-flag")
    (exec! actor "batch-4-release" {:op :release-batch :subject "batch-4"})

    (exec! actor "batch-1-release-again" {:op :release-batch :subject "batch-1"})

    (exec! actor "mnt-1-schedule" {:op :schedule-maintenance :subject "mnt-1"
                                   :equipment-id "reactor-1"
                                   :scheduled-at-iso "2026-08-01T09:00:00Z"
                                   :description "定期反応器内部検査"})
    db))

;; ----------------------------- rendering -----------------------------

(defn- esc [v]
  (-> (str v)
      (str/replace "&" "&amp;")
      (str/replace "<" "&lt;")
      (str/replace ">" "&gt;")))

(defn- last-fact-for [ledger subject-id]
  (last (filter #(= (:subject %) subject-id) ledger)))

(defn- status-cell [ledger subject-id]
  (let [f (last-fact-for ledger subject-id)]
    (cond
      (nil? f) "<span class=\"muted\">no activity</span>"
      (= :committed (:t f)) "<span class=\"ok\">committed</span>"
      (= :approval-granted (:t f)) "<span class=\"ok\">approved &amp; committed</span>"
      (= :governor-hold (:t f))
      (let [rule (-> f :violations first :rule)]
        (str "<span class=\"critical\">HARD hold &middot; " (esc (name (or rule :unknown))) "</span>"))
      (= :approval-requested (:t f)) "<span class=\"warn\">awaiting approval</span>"
      :else "<span class=\"muted\">in progress</span>")))

(defn- batch-row [ledger {:keys [id batch-name jurisdiction product-type-id
                                 batch-logged? batch-released?]}]
  (format "        <tr><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td><td>%s</td></tr>"
          (esc id) (esc batch-name) (esc jurisdiction)
          (esc (name (or product-type-id :n-a)))
          (if batch-logged? "yes" "no") (if batch-released? "yes" "no")
          (status-cell ledger id)))

(defn- ledger-row [{:keys [t op subject disposition basis]}]
  (format "        <tr><td>%s</td><td><code>%s</code></td><td>%s</td><td>%s</td></tr>"
          (esc (name t)) (esc (name (or op :n-a))) (esc subject)
          (esc (or (some->> basis (map name) (str/join ", ")) (some-> disposition name) ""))))

(def ^:private action-gate-rows
  ;; Static description of this actor's own op contract (README `Ops`
  ;; table, `basicchem.governor`/`basicchem.phase`) -- documentation of
  ;; fixed behavior, not runtime telemetry, so it is legitimately
  ;; hand-described rather than derived from a live run.
  ["        <tr><td><code>:log-production-batch</code></td><td><span class=\"warn\">ALWAYS human approval</span> &middot; spec-basis citation independently checked</td></tr>"
   "        <tr><td><code>:release-batch</code></td><td><span class=\"warn\">ALWAYS human approval</span> &middot; evidence, reaction-temperature, product-type, safety-concern and handoff all independently re-verified</td></tr>"
   "        <tr><td><code>:flag-safety-concern</code></td><td><span class=\"warn\">ALWAYS human approval</span> (even at low severity) &middot; required fields checked</td></tr>"
   "        <tr><td><code>:schedule-maintenance</code></td><td><span class=\"ok\">auto-commit when clean, no capital/product risk</span></td></tr>"])

(defn render
  "Renders the full operator-console.html document from a store `db`
  that has already run `run-demo!` (or any other real scenario)."
  [db]
  (let [ledger (vec (store/ledger db))
        batches (store/all-batches db)
        batch-rows (str/join "\n" (map (partial batch-row ledger) batches))
        ledger-rows (str/join "\n" (map ledger-row ledger))]
    (str
     "<html><head><meta charset=\"utf-8\"><title>cloud-itonami-isic-2011 &middot; basic-chemicals manufacture</title><style>\n"
     "table { width: 100%; border-collapse: collapse; font-size: 14px; }\n"
     ".ok { color: #137a3f; }\n"
     "body { font-family: system-ui,-apple-system,sans-serif; margin: 0; color: #1a1a1a; background: #fafafa; }\n"
     "header.bar { display: flex; align-items: center; gap: 12px; padding: 12px 20px; background: #fff; border-bottom: 1px solid #e5e5e5; }\n"
     "th, td { text-align: left; padding: 8px 10px; border-bottom: 1px solid #f0f0f0; }\n"
     "h2 { margin-top: 0; font-size: 15px; }\n"
     ".warn { color: #b25c00; background: #fff8e1; padding: 2px 6px; border-radius: 4px; }\n"
     "main { max-width: 980px; margin: 24px auto; padding: 0 20px; }\n"
     "header.bar h1 { font-size: 18px; margin: 0; font-weight: 600; }\n"
     ".muted { color: #888; font-size: 13px; }\n"
     ".critical { color: #fff; background: #b3261e; padding: 2px 6px; border-radius: 4px; font-weight: 600; }\n"
     ".card { background: #fff; border: 1px solid #e5e5e5; border-radius: 8px; padding: 16px; margin-bottom: 16px; }\n"
     ".err { color: #b3261e; background: #fbe9e7; padding: 2px 6px; border-radius: 4px; }\n"
     "th { font-weight: 600; color: #555; font-size: 12px; text-transform: uppercase; letter-spacing: 0.04em; }\n"
     "header.bar .badge { margin-left: auto; font-size: 12px; color: #666; }\n"
     "code { font-size: 12px; background: #f4f4f4; padding: 1px 4px; border-radius: 3px; }\n"
     "</style></head><body>\n"
     "<header class=\"bar\">\n"
     "  <h1>Basic chemicals manufacture (ISIC 2011) — Operator Console</h1>\n"
     "  <span class=\"badge\">read-only sample · governor-gated · batch-log/batch-release/safety-flag always human-approved</span>\n"
     "</header>\n"
     "<main>\n"
     "  <section class=\"card\">\n"
     "    <h2>Production batches</h2>\n"
     "    <p class=\"muted\">Demo snapshot — build-time-generated from <code>basicchem.store</code> via <code>basicchem.render-html</code> (<code>clojure -M:dev:render-html</code>), regenerated nightly.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Batch</th><th>Name</th><th>Jurisdiction</th><th>Product type</th><th>Logged?</th><th>Released?</th><th>Last op status</th></tr></thead>\n"
     "      <tbody>\n"
     batch-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Action gate (Chemical Safety Governor)</h2>\n"
     "    <p class=\"muted\">HARD holds cannot be overridden. Reaction-temperature and evidence-completeness are independently recomputed, never trusted from the proposal; an unresolved process-safety concern permanently blocks logging/release of its batch until a human-approved resolution flag clears it.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Op</th><th>Gate</th></tr></thead>\n"
     "      <tbody>\n"
     (str/join "\n" action-gate-rows) "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "  <section class=\"card\">\n"
     "    <h2>Audit ledger (this run)</h2>\n"
     "    <p class=\"muted\">Append-only decision-fact log — every proposal, hold and commit this scenario produced.</p>\n"
     "    <table>\n"
     "      <thead><tr><th>Fact</th><th>Op</th><th>Subject</th><th>Basis</th></tr></thead>\n"
     "      <tbody>\n"
     ledger-rows "\n"
     "      </tbody>\n"
     "    </table>\n"
     "  </section>\n"
     "</main>\n"
     "</body></html>\n")))

(defn -main [& args]
  (let [out (or (first args) "docs/samples/operator-console.html")
        db (run-demo!)
        html (render db)]
    (spit out html)
    (println "wrote" out "(" (count (store/ledger db)) "ledger facts,"
             (count (store/log-history db)) "batch logs,"
             (count (store/release-history db)) "batch releases,"
             (count (store/maintenance-history db)) "maintenance schedules )")))
