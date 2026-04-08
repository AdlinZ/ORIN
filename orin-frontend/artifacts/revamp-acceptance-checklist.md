# ORIN Revamp Acceptance Checklist

Generated at: 2026-04-08T17:44:51.276Z

## 1. Smoke & Regression Gates
- [ ] `npm run smoke:revamp` passed
- [ ] `npm run test:revamp` passed
- [ ] `npm run build` passed

## 2. Staged Rollout Verification
- [ ] Stage 1: revampAgentsHub
- [ ] Stage 2: revampKnowledgeHub + revampWorkflowHub
- [ ] Stage 3: revampRuntimeOverview
- [ ] Stage 4: revampCollaboration
- [ ] Stage 5: revampSystemGateway + revampSystemConfigHub
- [ ] Stage 6: revampAuditCenter

## 3. Domain Core Path Acceptance
### 智能体中枢
- [ ] 智能体中枢 (/dashboard/applications/agents)
- [ ] 多智能体协作 (/dashboard/applications/collaboration/dashboard)

### 知识与工作流
- [ ] 知识中枢 (/dashboard/resources/knowledge)
- [ ] 工作流中枢 (/dashboard/applications/workflows)

### 监控与运维
- [ ] 运行总览 (/dashboard/runtime/overview)

### 系统与网关
- [ ] 系统与网关 (/dashboard/control/gateway)
- [ ] 系统配置中心 (/dashboard/control/system-env)
- [ ] 审计中心 (/dashboard/control/audit-logs)

## 4. Rollback Readiness
- [ ] Single-module rollback drill completed
- [ ] Full rollback drill completed
- [ ] Audit traceability confirmed after rollback
