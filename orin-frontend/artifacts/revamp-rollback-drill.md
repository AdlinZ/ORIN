# ORIN Frontend Route Rollback Drill

Generated at: 2026-04-15T09:20:04.348Z

## 1. Preconditions
- [ ] `npm test` and `npm run smoke:revamp` pass on current branch
- [ ] Confirm target rollback scope (single module vs full router map)

## 2. Single-Module Rollback
- [ ] Revert only the target route/component mapping in `src/router/index.js`
- [ ] Keep legacy redirects intact in `src/router/routes.js`
- [ ] Run `npm test` and verify no new dead-route/duplicate-route failures

## 3. Full Rollback
- [ ] Restore previous router and route constants commit
- [ ] Re-run `npm run smoke:revamp` and confirm redirected legacy URLs still resolve
- [ ] Re-run `npm run build` before merge

## 4. Post-Rollback Validation
- [ ] `/dashboard/control/gateway` and `/dashboard/runtime/overview` functional smoke
- [ ] `/dashboard/applications/workflows` imports/exports and diagnostics smoke
- [ ] `/dashboard/resources/knowledge` list actions smoke
