# Security Policy

ORIN is in active stabilization. Security reports are handled on a best-effort basis, with priority given to reproducible issues that affect credentials, authentication, authorization, data isolation, dependency integrity, or remote code execution risk.

## Supported Versions

| Version | Supported |
|---------|-----------|
| `main` | Yes, best-effort |
| released tags | Not yet available |

The project has not published a stable release yet. Until `v0.1.0` is tagged, security fixes target `main`.

## Reporting a Vulnerability

Do not publish secrets, exploit details, private logs, API keys, tokens, passwords, database dumps, or personal data in public issues.

Preferred reporting flow:

1. Use GitHub Private Vulnerability Reporting if it is enabled for this repository.
2. If private reporting is unavailable, open a minimal public issue that says a security concern exists, without exploit details or sensitive data.
3. Contact the repository owner through their GitHub profile for private follow-up when needed.

Include:

- A short description of the affected area
- Reproduction steps with safe, redacted examples
- Expected impact
- Affected commit, branch, or version
- Whether the issue requires external services or credentials

## Response Expectations

Maintainers will acknowledge valid reports as capacity allows, triage severity, and coordinate a fix path. High-risk issues may result in temporary documentation warnings, dependency pinning, feature disablement, or a patch release once releases exist.

## Scope

In scope:

- Authentication and authorization bypass
- API Key, JWT, token, or secret exposure
- Unsafe logging of sensitive data
- Dependency vulnerabilities with a practical ORIN impact
- Remote code execution or unsafe tool execution
- Cross-service data isolation failures

Out of scope:

- Vulnerabilities requiring leaked credentials
- Denial-of-service claims without a practical reproduction
- Reports against optional third-party providers unless ORIN integration code is affected
- Social engineering or physical access scenarios
- Unredacted production data supplied by the reporter

## Dependency Maintenance

Dependabot is configured to monitor backend Maven dependencies, frontend npm dependencies, AI Engine Python dependencies, and GitHub Actions. Dependency update PRs still require normal CI checks and manual review; automatic merge is not enabled.
