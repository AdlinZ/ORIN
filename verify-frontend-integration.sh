#!/bin/bash

# Skill-Hub 前端整合验证脚本
# 用于验证 Skill-Hub 功能是否成功整合到现有前端系统

echo "=========================================="
echo "Skill-Hub 前端整合验证"
echo "=========================================="
echo ""

# 检查前端文件是否存在
echo "📋 检查前端文件..."
echo ""

files=(
    "/Users/adlin/Documents/Code/ORIN/orin-frontend/src/views/Skill/SkillManagement.vue"
    "/Users/adlin/Documents/Code/ORIN/orin-frontend/src/views/Workflow/WorkflowManagement.vue"
    "/Users/adlin/Documents/Code/ORIN/orin-frontend/src/views/Trace/TraceViewer.vue"
)

all_files_exist=true
for file in "${files[@]}"; do
    if [ -f "$file" ]; then
        echo "✅ $(basename "$file") 存在"
    else
        echo "❌ $(basename "$file") 不存在"
        all_files_exist=false
    fi
done

echo ""

# 检查路由配置
echo "📋 检查路由配置..."
echo ""

router_file="/Users/adlin/Documents/Code/ORIN/orin-frontend/src/router/index.js"
if grep -q "SkillManagement" "$router_file" && \
   grep -q "WorkflowManagement" "$router_file" && \
   grep -q "TraceViewer" "$router_file"; then
    echo "✅ 路由配置正确"
else
    echo "❌ 路由配置缺失"
    all_files_exist=false
fi

echo ""

# 检查侧边栏菜单
echo "📋 检查侧边栏菜单..."
echo ""

sidebar_file="/Users/adlin/Documents/Code/ORIN/orin-frontend/src/layout/components/Sidebar.vue"
if grep -q "技能管理" "$sidebar_file" && \
   grep -q "工作流编排" "$sidebar_file"; then
    echo "✅ 侧边栏菜单配置正确"
else
    echo "❌ 侧边栏菜单配置缺失"
    all_files_exist=false
fi

echo ""
echo "=========================================="

if [ "$all_files_exist" = true ]; then
    echo "✅ 所有检查通过！Skill-Hub 已成功整合到前端系统"
    echo ""
    echo "📝 下一步操作："
    echo "1. 启动后端服务: cd /Users/adlin/Documents/Code/ORIN/orin-backend && mvn spring-boot:run"
    echo "2. 安装前端依赖: cd /Users/adlin/Documents/Code/ORIN/orin-frontend && npm install echarts marked"
    echo "3. 启动前端服务: npm run dev"
    echo "4. 访问前端界面: http://localhost:5173"
    echo ""
    echo "📌 可访问的页面："
    echo "   - 技能管理: http://localhost:5173/dashboard/skill/management"
    echo "   - 工作流编排: http://localhost:5173/dashboard/workflow/management"
    echo "   - 工作流列表: http://localhost:5173/dashboard/workflow/list"
    echo ""
else
    echo "❌ 部分检查失败，请检查上述错误"
    exit 1
fi

echo "=========================================="
