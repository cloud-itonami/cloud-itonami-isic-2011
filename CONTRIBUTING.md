# Contributing

`cloud-itonami-2011` accepts contributions to the OSS blueprint, capability
bindings, policy tests, documentation and operator model.

## Development
The capability layer lives in `kotoba-lang/robotics` and
`kotoba-lang/cae-solver`. This repo holds the business blueprint and
operator contracts.

```bash
clojure -X:test
clojure -M:lint
```

## Rules
- Do not commit real process, production or customer data.
- Keep robot dispatch, process specifications and release records
  behind the Chemical Safety Governor.
- Treat production/inspection workflows as high-risk: add tests for
  robot-safety gating, specification scope, evidence, disclosure and
  audit logging.
- Document any new business-model or operator assumption in `docs/`.

## Pull Requests
PRs should describe: what behavior changed, which policy invariant is
affected, how it was tested, whether operator or certification docs need
updates.
