<template>
  <div class="workflow-editor">
    <div class="header">
      <el-page-header @back="goBack">
        <template #content>
          <span class="text-large font-600 mr-3">
            {{ isEdit ? '编辑工作流' : '创建工作流' }}
          </span>
        </template>
        <template #extra>
          <el-button @click="goBack">取消</el-button>
          <el-button type="primary" :loading="submitting" @click="handleSubmit">
            保存
          </el-button>
        </template>
      </el-page-header>
    </div>

    <div class="content">
      <el-form :model="form" label-width="120px" ref="formRef" :rules="rules">
        <!-- Basic Info -->
        <el-card class="box-card mb-4" header="基本信息">
          <el-form-item label="工作流名称" prop="workflowName">
            <el-input v-model="form.workflowName" placeholder="例如：智能研报生成助手" />
          </el-form-item>
          <el-form-item label="描述" prop="description">
            <el-input type="textarea" v-model="form.description" :rows="3" />
          </el-form-item>
          <el-form-item label="超时时间(秒)" prop="timeoutSeconds">
            <el-input-number v-model="form.timeoutSeconds" :min="1" />
          </el-form-item>
        </el-card>

        <!-- Steps -->
        <el-card class="box-card" header="流程编排">
          <template #header>
            <div class="card-header">
              <span>步骤列表</span>
              <el-button type="primary" link @click="addStep">
                <el-icon><Plus /></el-icon> 添加步骤
              </el-button>
            </div>
          </template>

          <div v-if="form.steps.length === 0" class="empty-steps">
            <el-empty description="暂无步骤，请添加" />
          </div>

          <el-collapse v-model="activeSteps">
            <el-collapse-item 
              v-for="(step, index) in form.steps" 
              :key="index" 
              :name="index"
            >
              <template #title>
                <div class="step-title">
                  <span class="step-index">步骤 {{ index + 1 }}</span>
                  <span class="step-name">{{ step.stepName || '未命名步骤' }}</span>
                  <el-tag size="small" :type="getStepTypeTag(step.stepType)">{{ step.stepType }}</el-tag>
                  <el-button 
                    type="danger" 
                    link 
                    size="small" 
                    class="delete-btn"
                    @click.stop="removeStep(index)"
                  >
                    删除
                  </el-button>
                </div>
              </template>

              <div class="step-content">
                <el-form-item 
                  label="步骤名称" 
                  :prop="'steps.' + index + '.stepName'"
                  :rules="{ required: true, message: '请输入步骤名称', trigger: 'blur' }"
                >
                  <el-input v-model="step.stepName" />
                </el-form-item>

                <el-form-item label="类型" required>
                  <el-radio-group v-model="step.stepType">
                    <el-radio-button label="AGENT">智能体 (Agent)</el-radio-button>
                    <el-radio-button label="SKILL">技能 (Skill)</el-radio-button>
                    <el-radio-button label="LOGIC">逻辑控制</el-radio-button>
                  </el-radio-group>
                </el-form-item>

                <!-- Agent Selection -->
                <el-form-item 
                  v-if="step.stepType === 'AGENT'" 
                  label="选择智能体" 
                  :prop="'steps.' + index + '.agentId'"
                  :rules="{ required: true, message: '请选择智能体', trigger: 'change' }"
                >
                  <el-select v-model="step.agentId" placeholder="请选择智能体" style="width: 100%">
                    <el-option
                      v-for="agent in agentList"
                      :key="agent.id"
                      :label="agent.name"
                      :value="agent.id"
                    />
                  </el-select>
                </el-form-item>

                <!-- Skill Selection -->
                 <el-form-item 
                  v-if="step.stepType === 'SKILL'" 
                  label="选择技能" 
                  :prop="'steps.' + index + '.skillId'"
                  :rules="{ required: true, message: '请选择技能', trigger: 'change' }"
                >
                  <el-input v-model="step.skillId" placeholder="请输入技能ID (暂未对接技能列表)" />
                </el-form-item>

                <el-form-item label="输入映射 (JSON)">
                  <el-input 
                    type="textarea" 
                    v-model="step.inputMappingStr" 
                    placeholder='{"query": "${previous_step.output}"}' 
                    :rows="3"
                  />
                </el-form-item>
              </div>
            </el-collapse-item>
          </el-collapse>
        </el-card>
      </el-form>
    </div>
  </div>
</template>

<script setup>
import { ref, onMounted, reactive } from 'vue';
import { useRouter, useRoute } from 'vue-router';
import { Plus } from '@element-plus/icons-vue';
import { ElMessage } from 'element-plus';
import { createWorkflow } from '@/api/workflow';
// Assuming we have a generic agent API. If not, I'll need to double check the path. 
// Based on file list, src/api/agent.js exists.
import { getAgentList } from '@/api/agent'; 

const router = useRouter();
const route = useRoute();
const formRef = ref(null);

const isEdit = ref(false);
const submitting = ref(false);
const activeSteps = ref([0]);
const agentList = ref([]);

const form = reactive({
  workflowName: '',
  description: '',
  workflowType: 'SEQUENTIAL',
  timeoutSeconds: 300,
  steps: []
});

const rules = {
  workflowName: [{ required: true, message: '请输入工作流名称', trigger: 'blur' }],
};

onMounted(async () => {
  if (route.params.id) {
    isEdit.value = true;
    // fetchWorkflow(route.params.id); // TODO: Implement edit mode
  }
  await fetchAgents();
});

const fetchAgents = async () => {
  try {
    // Ideally this API returns a list of agents
    const res = await getAgentList({ page: 0, size: 100 });
    if (res && res.content) {
      agentList.value = res.content;
    } else if (Array.isArray(res)) {
      agentList.value = res;
    }
  } catch (error) {
    console.error('Failed to fetch agents', error);
  }
};

const addStep = () => {
  form.steps.push({
    stepName: '',
    stepType: 'AGENT',
    stepOrder: form.steps.length + 1,
    agentId: null,
    skillId: null,
    inputMappingStr: '{}'
  });
  activeSteps.value.push(form.steps.length - 1);
};

const removeStep = (index) => {
  form.steps.splice(index, 1);
  // Re-order steps
  form.steps.forEach((step, idx) => {
    step.stepOrder = idx + 1;
  });
};

const getStepTypeTag = (type) => {
  const map = {
    'AGENT': 'success',
    'SKILL': 'warning',
    'LOGIC': 'info'
  };
  return map[type] || '';
};

const handleSubmit = async () => {
  if (!formRef.value) return;
  
  await formRef.value.validate(async (valid) => {
    if (valid) {
      submitting.value = true;
      try {
        // 1. Validate Legality & Serialize to JSON (DSL)
        const dsl = serializeWorkflow(form);
        console.log('Generated DSL:', dsl);

        // 2. Save to Procedural Knowledge Base
        const payload = {
          ...form,
          steps: dsl.steps, // Use the serialized steps
          workflowDefinition: dsl // Complete definition
        };

        if (isEdit.value) {
          // await updateWorkflow(payload);
        } else {
          await createWorkflow(payload);
        }
        
        ElMessage.success('保存成功');
        goBack();
      } catch (error) {
        console.error(error);
        ElMessage.error(error.message || '保存失败');
      } finally {
        submitting.value = false;
      }
    }
  });
};

const serializeWorkflow = (formData) => {
    // 1. Basic Structure
    const dsl = {
        meta: {
            name: formData.workflowName,
            description: formData.description,
            timeout: formData.timeoutSeconds,
            version: '1.0.0'
        },
        steps: []
    };

    // 2. Step Validator & Serializer
    dsl.steps = formData.steps.map((step, index) => {
        // Validation: Verify Input Mapping JSON
        let inputMapping = {};
        try {
            inputMapping = JSON.parse(step.inputMappingStr || '{}');
        } catch (e) {
            throw new Error(`Step ${index + 1} (${step.stepName}): Invalid Input Mapping JSON`);
        }

        // Return pure DSL node
        return {
            id: `step_${index + 1}`,
            name: step.stepName,
            type: step.stepType,
            agentId: step.stepType === 'AGENT' ? step.agentId : undefined,
            skillId: step.stepType === 'SKILL' ? step.skillId : undefined,
            inputs: inputMapping,
            next: index < formData.steps.length - 1 ? `step_${index + 2}` : null
        };
    });

    return dsl;
};

const goBack = () => {
  router.push('/workflow');
};
</script>

<style scoped>
.workflow-editor {
  padding: 20px;
  background: #fff;
  min-height: 100vh;
}

.header {
  margin-bottom: 24px;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}

.step-title {
  display: flex;
  align-items: center;
  gap: 12px;
  width: 100%;
}

.step-index {
  font-weight: bold;
  color: #909399;
}

.step-name {
  font-weight: 500;
  flex: 1;
}

.delete-btn {
  margin-left: auto;
  margin-right: 20px;
}

.step-content {
  padding: 10px;
}

.mb-4 {
  margin-bottom: 16px;
}
</style>
