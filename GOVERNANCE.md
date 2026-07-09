# Governance

`cloud-itonami-2011` is an OSS open-business blueprint for community
basic chemicals manufacturing, robotics-premised.

## Maintainers
Maintainers may merge changes that preserve these invariants:
- a robot action the governor refuses is never dispatched to hardware.
- the Chemical Safety Governor remains independent of the advisor.
- hard policy violations (out-of-specification process, unverified batch release, evidenceless release record) cannot be overridden by human approval.
- every dispatch, sign-off, process and release-record path is auditable.
- sensitive process and production data stays outside Git.

## Decision Records
Architecture decisions live in `docs/adr/`. Changes to the trust model, storage contract, public business model, operator certification or license should add or update an ADR.

## Operator Governance
Anyone may fork and operate independently. itonami.cloud certification is a separate trust mark and should require security, robot-safety, audit and data-flow review.

Certified operators can lose certification for:
- bypassing robot-safety or specification-scope checks
- mishandling process or production data
- misrepresenting certification status
- failing to respond to safety incidents
