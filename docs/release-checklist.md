# ORIN Release Checklist

This checklist is the release gate for a tagged ORIN build. It is deliberately
stricter than “the application starts”: a release must be reproducible,
securely configured, and backed by the user journeys it claims to support.

## Candidate version

The single source for a candidate version is [`VERSION`](../VERSION). The
current candidate is `0.3.0-rc.1`; it is a release candidate, not a final
stable tag. Before tagging, run:

```bash
bash scripts/check-release-version.sh
ORIN_RELEASE_REQUIRE_CLEAN=1 bash scripts/check-release-version.sh
```

The second command must run from a clean release branch. It intentionally does
not inspect or print `.env` values.

To build the reviewable artifacts locally from that clean commit, run:

```bash
ORIN_RELEASE_PYTHON=/path/to/python3.11 bash scripts/package-release-candidate.sh
```

The candidate build requires a working Docker Buildx plugin (`docker buildx
version`). This is deliberate: image construction must use the same BuildKit
path as the release workflow, rather than silently falling back to a legacy
builder with different cache and network behaviour.

Artifacts and checksums are written to `artifacts/release/<version>/`. The
same operation is available as the manually triggered **Release Candidate
Package** GitHub Actions workflow; it uploads artifacts but never creates a tag
or publishes an image.

## Required engineering gates

- [ ] A clean release branch contains the intended changes only; no generated
  artifacts, credentials, or unrelated worktree changes are included.
- [ ] `bash scripts/check-release-version.sh` passes and all distributable
  components use the value from `VERSION`.
- [ ] `cd orin-backend && mvn clean test` passes.
- [ ] `cd orin-frontend && npm ci && npm run lint -- --max-warnings=0 && npm run test:coverage && npm run build` passes.
- [ ] `cd orin-ai-engine && python -m compileall app tests && pytest --cov=app --cov-report=xml:coverage.xml` passes.
- [ ] `bash scripts/check-schema-baseline.sh` and `bash -n scripts/*.sh` pass
  for every release-operated script.
- [ ] CI checks, gitleaks, CodeQL, coverage gates and Docker smoke are green
  on the exact release commit.

## Runtime and security gates

- [ ] A fresh Docker volume passes `bash scripts/docker-smoke-isolated.sh`.
  It generates a disposable compose project, ports and local-only credentials,
  so it never resets a developer or production-like `orin-*` stack. Do not use
  a production `.env` in smoke automation.
- [ ] Production/staging uses unique database, Redis, RabbitMQ, JWT and admin
  credentials; `CORS_ALLOWED_ORIGINS` is an explicit allow-list and
  `ORIN_SETUP_ENABLED=false` after initialization.
- [ ] No API Key, enrollment token, password, private key, or provider secret
  appears in the diff, release notes, test artifacts, or logs.
- [ ] Backup and restore smoke has passed against the release schema.

## Product gates

- [ ] F01: a Runner enrollment and lifecycle smoke passes on an isolated
  machine/container.
- [ ] F02: create, save and freeze an Agent Version with authorization checks.
- [ ] F03/F04: an Agent Run is claimed by a Runner, completes, remains
  observable, can be cancelled, and retry preserves the original fact.
- [ ] F05: `scripts/f05-external-acceptance.sh` passes with
  `ORIN_F05_REQUIRE_STDIO_BRIDGE=1` and one real MCP desktop client (Codex,
  Claude Desktop, Cursor, or Windsurf) completes `initialize → tools/list →
  tools/call` against the release build.
- [ ] The current documented feature statuses match that evidence; no
  `Partially Integrated` feature is advertised as release-complete.

## Publication

1. Update `CHANGELOG.md` with the exact release date, migration notes, security
   changes, and known limitations.
2. Build and archive the backend JAR, frontend `dist`, AI Engine wheel, and
   Runner image using the same commit and version.
3. Create an annotated tag `v<version>` only after all gates pass.
4. Publish checksums and image digests with the release notes.
5. Keep the release candidate status until the production smoke and rollback
   drill complete.
