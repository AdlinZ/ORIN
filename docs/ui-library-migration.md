# ORIN UI Library Migration

## Fourth-Round Decision
Arco Design Vue is the default UI base for new enterprise management pages. Element Plus remains as the compatibility layer for legacy pages, global messages, existing dialogs, and unmigrated workflows.

## Migration Rules
- New management pages must enter through the ORIN component layer or `src/ui/arco`; do not compose large business surfaces directly from raw Arco primitives.
- Pilot pages may use Arco controls inside ORIN Arco shells, but table, drawer, modal, and form shell behavior should stay wrapped.
- Do not mix Element Plus table/form/dialog/drawer with Arco table/form/modal/drawer in the same migrated page.
- Keep pure white page surfaces, compact table density, visible borders, actionable empty states, right-side detail drawers, and form dialogs for create/edit workflows.
- Do not add gradients, glow effects, strong shadows, large radii, `premium-card`, `glass-panel`, or generic `暂无数据` copy.

## Fourth-Round Pilot Pages
- `ModelList.vue`: validates resource table density, provider/status rendering, model detail drawer, model edit dialog, key management dialog, and bulk operations.
- `UserManagement.vue`: validates governance table density, search, pagination, user detail drawer, create/edit form dialog, and account lifecycle actions.

## Fifth-Round Candidates
Migrate `ApiKeyManagement.vue`, `WorkflowList.vue`, `AuditCenterV2.vue`, and `PricingConfig.vue` after the pilot pages pass build, lint, and manual workflow checks.
