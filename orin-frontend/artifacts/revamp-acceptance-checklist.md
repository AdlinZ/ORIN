# ORIN Frontend Cleanup Acceptance Checklist

Generated at: 2026-04-15T09:20:04.343Z

## 1. Build & Test Gates
- [ ] `npm test` passed
- [ ] `npm run build` passed
- [ ] `npm run smoke:revamp` passed

## 2. Canonical Route Smoke
- [ ] /dashboard/applications/agents
- [ ] /dashboard/resources/knowledge
- [ ] /dashboard/applications/collaboration/dashboard
- [ ] /dashboard/control/audit-logs
- [ ] /dashboard/applications/workflows
- [ ] /dashboard/runtime/overview
- [ ] /dashboard/control/gateway
- [ ] /dashboard/control/system-env

## 3. Redirect Compatibility Smoke
- [ ] /dashboard/applications/collaboration/tasks -> /dashboard/applications/collaboration
- [ ] /dashboard/applications/collaboration/config -> /dashboard/applications/collaboration
- [ ] /dashboard/applications/tools -> /dashboard/applications/mcp
- [ ] /dashboard/runtime/alert-rules -> /dashboard/runtime/alerts
- [ ] /dashboard/control/revamp-rollout -> /dashboard/control

## 4. Data Integrity
- [ ] Runtime latency page shows empty state on API failure (no fake trend/history rows)
- [ ] No localStorage mock document behavior in knowledge list flow
