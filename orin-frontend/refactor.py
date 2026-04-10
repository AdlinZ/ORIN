import re
import os

file_path = '/Users/adlin/Documents/Code/ORIN/orin-frontend/src/views/Agent/AgentWorkspace.vue'
with open(file_path, 'r', encoding='utf8') as f:
    content = f.read()

# 1. Extract workspace-config
config_match = re.search(r'<aside class="workspace-config"[^>]*>([\s\S]*?)</aside>', content)
if not config_match:
    raise Exception("Could not find workspace-config aside.")
config_content = config_match.group(1)

# Remove collapse handle from config content
config_content = re.sub(r'<div class="config-collapse-handle">[\s\S]*?</div>', '', config_content)

# Remove Close button
config_content = re.sub(r'<el-button class="header-icon-btn" circle :icon="Close" />', '', config_content)

config_pane = f"""
      <div v-show="sidebarTab === 'config' && !sessionPaneCollapsed" class="workspace-config-pane">
{config_content}
      </div>"""

# 2. Modify workspace-sidebar
sidebar_tabs = """
      <div v-if="!sessionPaneCollapsed" class="sidebar-tabs">
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'session' }" @click="sidebarTab = 'session'">会话记录</div>
        <div class="sidebar-tab" :class="{ active: sidebarTab === 'config' }" @click="sidebarTab = 'config'">工作台设置</div>
      </div>
"""
content = re.sub(r'<div class="workspace-session-pane">', sidebar_tabs + '      <div v-show="sidebarTab === \'session\'" class="workspace-session-pane">', content, count=1)

content = re.sub(r'(        </template>\n      </div>\n    </aside>)', '        </template>\n      </div>' + config_pane + '\n    </aside>', content, count=1)

# 3. Remove old workspace-config and overlay
content = re.sub(r'<div v-if="isRightDrawer[^>]*></div>\s*<aside class="workspace-config"[\s\S]*?</aside>', '', content)

# 4. InteractionTopBar adjustments
content = content.replace(':settings-open="!configPaneCollapsed"', ':settings-open="sidebarTab === \'config\'"')
content = content.replace('@toggle-settings="configPaneCollapsed = !configPaneCollapsed"', '@toggle-settings="openSettings"')
content = content.replace('configPaneCollapsed.value = false', 'openSettings()')
content = content.replace('configPaneCollapsed.value = !configPaneCollapsed.value', 'openSettings()')

# 5. Variables
content = content.replace("const configPaneCollapsed = ref(true);", "const sidebarTab = ref('session');")
content = content.replace("sessionPaneCollapsed, configPaneCollapsed, activeConfigTab", "sessionPaneCollapsed, sidebarTab, activeConfigTab")
content = content.replace("configPaneCollapsed.value = savedState.configPaneCollapsed ?? true;", "sidebarTab.value = savedState.sidebarTab ?? 'session';")
content = content.replace("configPaneCollapsed: configPaneCollapsed.value,", "sidebarTab: sidebarTab.value,")

open_settings_src = """
const openSettings = () => {
  sidebarTab.value = 'config';
  sessionPaneCollapsed.value = false;
};
"""
content = content.replace("const scrollToBottom = () => {", open_settings_src + "\nconst scrollToBottom = () => {")

# Remove isRightDrawer
content = re.sub(r'^\s*isRightDrawer,?\n*', '', content, flags=re.MULTILINE)
content = re.sub(r'rightDrawerMode:\s*\'always\'', '', content)


# 6. CSS Changes
tabs_css = """
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
"""
content = re.sub(r'(\.workspace-sidebar \{)', tabs_css + r'\n\1', content)
content = content.replace('.workspace-config', '.workspace-config-pane')
content = content.replace('.workspace-config-pane.is-drawer', '.DELETED_SELECTOR')

# 7. Write back
with open(file_path, 'w', encoding='utf8') as f:
    f.write(content)

print('Refactor complete via Python.')
