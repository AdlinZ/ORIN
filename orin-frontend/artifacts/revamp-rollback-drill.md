# ORIN Revamp Rollback Drill

Generated at: 2026-04-08T17:44:51.277Z

## 1. Preconditions
- [ ] `npm run smoke:revamp` currently passes
- [ ] Operator has admin access to `/dashboard/control/revamp-rollout`

## 2. Full Rollback Steps
- [ ] In rollout console, click `全部关闭`
- [ ] Run `npm run smoke:revamp` and verify all routes still resolve
- [ ] Verify core pages fallback to legacy views

## 3. Browser Console Fallback Script
```js
(() => {
  localStorage.setItem('orin_ff_revampAgentsHub', 'false')
  localStorage.setItem('orin_ff_revampKnowledgeHub', 'false')
  localStorage.setItem('orin_ff_revampWorkflowHub', 'false')
  localStorage.setItem('orin_ff_revampCollaboration', 'false')
  localStorage.setItem('orin_ff_revampRuntimeOverview', 'false')
  localStorage.setItem('orin_ff_revampSystemGateway', 'false')
  localStorage.setItem('orin_ff_revampAuditCenter', 'false')
  localStorage.setItem('orin_ff_revampSystemConfigHub', 'false')
  console.log('revamp flags disabled')
})()
```

## 4. Recovery Steps
- [ ] Re-enable by stage order in rollout console
- [ ] Re-run `npm run smoke:revamp && npm run test:revamp`
- [ ] Confirm audit logs include rollback operator and timestamp

## 5. Consistency Check
- Rollout flags and feature flag registry are consistent
