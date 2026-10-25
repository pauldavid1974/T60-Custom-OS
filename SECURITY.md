# Security and responsible investigation

## Scope

T60 Custom OS is a public research and development repository. There is currently no released or installable watch firmware. Security coverage applies to the `main` branch, repository tooling, host-only code, handling of device/app evidence, and future release artifacts.

## Report a sensitive issue

Contact Paul (`pauldavid1974`) through the existing private project channel. Do not open a public issue, publish a proof of concept, or place sensitive material in a pull request.

Include, where available:

- Affected file, commit, tool, or device/app version.
- Impact and preconditions.
- Reproduction steps using sanitized or synthetic data.
- Suggested containment or remediation.

Do not include credentials, tokens, private device dumps, proprietary APKs or firmware, personal data, or unlicensed source. No response-time or remediation guarantee is implied.

## Safe investigation rules

- Do not test a suspected vulnerability against the live watch or paired phone without Paul's explicit approval of the exact action.
- A suspected security issue does not suspend device-safety, backup, recovery, or approval gates.
- Prefer offline analysis, sanitized fixtures, and host-only tests.
- Do not disable hooks, signature checks, authentication, encryption, or other protections to make a test pass.
- Do not execute or publish a device-write, DFU/OTA, reset, unlock, or recovery procedure unless its prerequisites and exact approval are documented under `GOVERNANCE.md` and `docs/RECOVERY.md`.

## Repository hygiene

- Keep secrets and private evidence out of Git history, issues, pull requests, CI logs, screenshots, and handoffs.
- Use `.gitignore` as a backstop, not as a substitute for reviewing staged files.
- Pin or verify third-party dependencies where practical and check license compatibility before import.
- If sensitive material is committed accidentally, stop further distribution and notify Paul privately. History rewriting, key rotation, and evidence preservation require an owner-directed response.
