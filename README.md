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
intake + identity + process specification + production/inspection mission
        |
        v
Manufacturing Advisor -> Chemical Safety Governor -> process record, batch release, or human approval
        |
        v
robot actions (gated) + production history record + release record + audit ledger
```

No automated advice can release a chemical batch the governor refuses,
approve a process change outside its verified specification, or publish
a release record without governor approval and audit evidence.

## Capability layer

Resolves via [`kotoba-lang/industry`](https://github.com/kotoba-lang/industry)
(ISIC `2011`). Implemented by:

- [`kotoba-lang/robotics`](https://github.com/kotoba-lang/robotics) — missions, actions, safety-stops, telemetry proofs
- [`kotoba-lang/cae-solver`](https://github.com/kotoba-lang/cae-solver) — computer-aided engineering + computational fluid dynamics simulation contracts

See [`docs/business-model.md`](docs/business-model.md) and
[`docs/operator-guide.md`](docs/operator-guide.md).

## License

AGPL-3.0-or-later.
