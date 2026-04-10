const fs = require('fs');
const path = require('path');

const file = '/Users/adlin/Documents/Code/ORIN/orin-frontend/src/views/Agent/AgentWorkspace.vue';
let content = fs.readFileSync(file, 'utf8');

// 1. Extract the content of workspace-config (without the aside tags)
const configMatch = content.match(/<aside class="workspace-config"[^>]*>([\s\S]*?)<\/aside>/);
if (!configMatch) throw new Error("Could not find workspace-config aside.");
let configContent = configMatch[1];
// Remove collapse handle from configContent
configContent = configContent.replace(/<div class="config-collapse-handle">[\s\S]*?<\/div>/, '');

// Clean up configHeader close button
configContent = configContent.replace(/<el-button class="header-icon-btn" circle :icon="Close" \/>/, '');

// Wrap it in a div
const configPane = `\n      <div v-show="sidebarTab === 'config' && !sessionPaneCollapsed" class="workspace-config-pane">\n${configContent}\n      </div>`;

// 2. Modify workspace-sidebar
const sidebarTabs = `
      <div v-if="!sessionPaneCollapsed" class="sidebar-tabs">
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'session' }" @click="sidebarTab = 'session'">会话记录</div>
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'config' }" @click="sidebarTab = 'config'">工作台设置</div>
      </div>
`;
content = content.replace(/<div class="workspace-session-pane">/, sidebarTabs + `      <div v-show="sidebarTab === 'session'" class="workspace-session-pane">`);

// Insert configPane right before the closing aside of workspace-sidebar
content = content.replace(/<\/aside>/, configPane + '\n    </aside>');

// 3. Remove the original workspace-config and its overlay
content = content.replace(/<div v-if="isRightDrawer[^>]*><\/div>\s*<aside class="workspace-config"[\s\S]*?<\/aside>/, '');

// 4. Update InteractionTopBar call
content = content.replace(/:settings-open="!configPaneCollapsed"/, ':settings-open="sidebarTab === \'config\'"');
content = content.replace(/@toggle-settings="configPaneCollapsed = !configPaneCollapsed"/, '@toggle-settings="openSettings"');
content = content.replace(/configPaneCollapsed\.value = false/g, 'openSettings()');
content = content.replace(/configPaneCollapsed\.value = !configPaneCollapsed\.value/g, 'openSettings()');

// 5. Variables
content = content.replace(/const configPaneCollapsed = ref\(true\);/, `const sidebarTab = ref('session');`);
content = content.replace(/sessionPaneCollapsed, configPaneCollapsed, activeConfigTab/, `sessionPaneCollapsed, sidebarTab, activeConfigTab`);
content = content.replace(/configPaneCollapsed\.value = savedState\.configPaneCollapsed \?\? true;/, `sidebarTab.value = savedState.sidebarTab ?? 'session';`);
content = content.replace(/configPaneCollapsed: configPaneCollapsed\.value,/, `sidebarTab: sidebarTab.value,`);

// Add openSettings method
const openSettingsSrc = `
const openSettings = () => {
  sidebarTab.value = 'config';
  sessionPaneCollapsed.value = false;
};
`;
content = content.replace(/const scrollToBottom = \(\) => {/, openSettingsSrc + '\nconst scrollToBottom = () => {');

// Remove isRightDrawer
content = content.replace(/isRightDrawer\n/, '');
content = content.replace(/,\s*isRightDrawer/, '');
content = content.replace(/rightDrawerMode: 'always'/, '');

// 6. CSS Changes
// Add styles for sidebar-tabs
const tabsCss = `
.sidebar-tabs {
  display: flex;
  padding: 16px 16px 4px;
  gap: 8px;
}
.sidebar-tab {
  flex: 1;
  text-align: center;
  padding: 8px 0;
  font-size: 13px;
  font-weight: 600;
  color: #64748b;
  border-radius: 8px;
  cursor: pointer;
  transition: all 0.2s ease;
  background: transparent;
}
.sidebar-tab:hover {
  background: rgba(241, 245, 249, 0.6);
  color: #3b82f6;
}
.sidebar-tab.active {
  background: #ffffff;
  color: #1e293b;
  box-shadow: 0 2px 6px rgba(0,0,0,0.04);
}

.workspace-config-pane {
  flex: 1;
  display: flex;
  flex-direction: column;
  min-height: 0;
}
`;
content = content.replace(/(\.workspace-sidebar \{)/, tabsCss + '\n$1');
// workspace-config css to workspace-config-pane styles
content = content.replace(/\.workspace-config/g, '.workspace-config-pane');
// Fix some drawer styles
content = content.replace(/\.workspace-config-pane\.is-drawer/g, '.DELETED_SELECTOR'); 

fs.writeFileSync(file, content, 'utf8');
console.log('Refactor complete.');
