# cloud-itonami-2011

Open Business Blueprint for **ISIC Rev.5 2011**: manufacture of basic
chemicals (industrial and specialty chemical production).

This repository designs a forkable OSS business for community basic
chemicals manufacturing: process-safety and specification-version
management, robotics-assisted production and inspection, and batch
release/disclosure records — run by a qualified operator so a
manufacturer keeps its own process-safety and production history
instead of renting a closed quality-management platform.

## Robotics premise

All cloud-itonami verticals are designed on the premise that a **robot
performs the physical domain work**. Here robots (process operation,
sampling, inspection) operate under an actor that proposes actions and
an independent **Chemical Safety Governor** that gates them. The
governor never releases a batch to market itself; `:high`/
`:safety-critical` actions (releasing a batch that has not passed
verification, any process change to a safety-critical reaction
parameter) require human sign-off.

## Core Contract

```text
log-production-batch + schedule-maintenance + flag-safety-concern + release-batch
        |
        v
Basic Chemicals Advisor -> Chemical Safety Governor (HARD holds un-overridable)
        |
        v
phase gate (log/release/flag-concern always escalate; only maintenance may auto-commit)
        |
        v
human approval for high stakes -> append-only ledger + draft records
```

No automated advice can release a chemical batch the governor refuses,
release a batch that was never logged or whose own reaction parameters
fall outside spec, or commit a batch record/release while an
unresolved process-safety concern is on file for it.

## Actuation honesty

Logging a production batch and releasing a batch to market produce
**unsigned draft records and ledger facts only**. This actor does not
talk to a real plant control system or a market-release/customs
portal. Signature and the physical act of shipping/releasing product
are the chemical manufacturer's own acts.

## Ops

| Op | Effect |
|---|---|
| `:log-production-batch` | record a produced batch under its jurisdiction's evidence checklist (always human) |
| `:schedule-maintenance` | routine reactor/equipment maintenance schedule (phase 3 may auto-commit when clean -- no capital/product risk) |
| `:flag-safety-concern` | process-safety concern report -- abnormal reaction temperature/pressure, toxic-substance leak risk, etc. (ALWAYS human, even when the reported concern is minor -- it never HARD-holds on its own finding, unlike a defect-screening op) |
| `:release-batch` | market-release decision for a batch (always human; HARD-blocked if unlogged, evidence-incomplete, reaction parameters out of spec, or an unresolved safety concern is on file) |

## Refrigerant supply linkage (cloud-itonami-isic-2813)

`:release-batch`'s proposal may OPTIONALLY carry a `:handoff` (the
superproject `:handoff` shared shape, ADR-2607177600/ADR-2800000500,
reused as-is) naming this actor as the R-448A refrigerant-charge
supplier of `cloud-itonami-isic-2813`'s `part:refrigerant-charge` BOM
line, received via that actor's own `:register-part-receipt` op.
`:handoff` itself is entirely optional; a `:handoff` that IS present
but missing its own required identity fields (`:handoff/id`/
`:handoff/source-actor`/`:handoff/batch-id`) is a HARD hold.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `2011`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/cae-solver`](https://github.com/kotoba-lang/cae-solver) — computer-aided engineering + computational fluid dynamics simulation contracts

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## Develop

```bash
clojure -M:dev:test
clojure -M:lint
clojure -M:dev:run
```

## License

AGPL-3.0-or-later.
