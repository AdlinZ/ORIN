<template>
  <div class="workflow-runner">
    <div class="runner-header">
      <h4>Workflow Configuration</h4>
      <p class="desc">Configure inputs and execute the workflow.</p>
    </div>

    <div class="form-container">
      <el-form label-position="top" size="large">
        <template v-if="formFields.length > 0">
          <el-form-item 
            v-for="field in formFields" 
            :key="field.variable" 
            :label="field.label"
            :required="field.required"
          >
            <!-- Text Input -->
            <el-input 
              v-if="field.type === 'text-input'" 
              v-model="inputs[field.variable]" 
              :placeholder="field.help || 'Enter text...'"
            />
            
            <!-- Select Input -->
            <el-select 
              v-else-if="field.type === 'select'" 
              v-model="inputs[field.variable]" 
              style="width: 100%"
            >
              <el-option 
                v-for="opt in field.options" 
                :key="opt" 
                :label="opt" 
                :value="opt" 
              />
            </el-select>

            <!-- Paragraph / Textarea -->
            <el-input 
              v-else 
              v-model="inputs[field.variable]" 
              type="textarea" 
              :rows="4"
              :placeholder="field.help || 'Enter detailed text...'"
            />
          </el-form-item>
        </template>
        <div v-else class="no-inputs">
           <el-alert title="No inputs required for this workflow." type="info" :closable="false" show-icon />
        </div>

        <div class="actions">
          <el-button type="primary" size="large" @click="runWorkflow" :loading="loading" class="run-btn">
            <el-icon><VideoPlay /></el-icon> Run Workflow
          </el-button>
        </div>
      </el-form>
    </div>

    <!-- Output Section -->
    <div class="output-section" v-if="result || error">
      <div class="output-header">Execution Result</div>
      
      <div v-if="error" class="error-box">
        <el-alert :title="error" type="error" :closable="false" show-icon />
      </div>

      <div v-if="result" class="result-box">
        <pre>{{ resultFormatted }}</pre>
      </div>
    </div>
  </div>
</template>

<script setup>
import { ref, computed, watch, onMounted } from 'vue';
import { VideoPlay } from '@element-plus/icons-vue';
import { chatAgent } from '@/api/agent'; // Use chat API but route to workflow logic
import { ElMessage } from 'element-plus';

const props = defineProps({
  agentId: { type: String, required: true },
  parameters: { type: [String, Object], default: null }
});

const inputs = ref({});
const loading = ref(false);
const result = ref(null);
const error = ref(null);

const formFields = computed(() => {
  if (!props.parameters) return [];
  try {
    const params = typeof props.parameters === 'string' 
      ? JSON.parse(props.parameters) 
      : props.parameters;
    
    // Dify format: user_input_form: [ { text-input: ... } ]
    if (params && params.user_input_form && Array.isArray(params.user_input_form)) {
        return params.user_input_form.map(f => {
            // Flatten the structure purely for UI
            // Dify structure usually: { "text-input": { "label": "...", "variable": "...", "required": true } }
            // or { "select": { ... } }
            const type = Object.keys(f)[0]; 
            const config = f[type];
            return {
                type, 
                ...config
            };
        });
    }
    return [];
  } catch (e) {
    console.warn('Failed to parse parameters', e);
    return [];
  }
});

// Initialize defaults
watch(() => formFields.value, (fields) => {
    const newInputs = {};
    fields.forEach(f => {
        newInputs[f.variable] = f.default || '';
    });
    inputs.value = newInputs;
}, { immediate: true });

const resultFormatted = computed(() => {
    if (!result.value) return '';
    try {
        if (typeof result.value === 'object') return JSON.stringify(result.value, null, 2);
        return result.value;
    } catch (e) {
        return result.value;
    }
});

const runWorkflow = async () => {
    loading.value = true;
    error.value = null;
    result.value = null;
    
    try {
        // We use chatAgent API but pass JSON string as message
        // The backend detects (starts with '{') and parses it as inputs for Workflow mode
        const payload = JSON.stringify(inputs.value);
        const res = await chatAgent(props.agentId, payload);
        
        // Workflow response from Dify often under 'data' or 'outputs'
        if (res.data && res.data.outputs) {
             result.value = res.data.outputs;
        } else if (res.outputs) {
             result.value = res.outputs;
        } else {
             result.value = res;
        }
    } catch (e) {
        error.value = e.message || 'Execution failed';
        ElMessage.error('Workflow execution failed');
    } finally {
        loading.value = false;
    }
};
</script>

<style scoped>
.workflow-runner {
    padding: 20px;
    height: 100%;
    display: flex;
    flex-direction: column;
    overflow-y: auto;
}

.runner-header { margin-bottom: 24px; border-bottom: 1px solid #eee; padding-bottom: 16px; }
.runner-header h4 { margin: 0 0 8px 0; font-size: 18px; color: #333; }
.desc { margin: 0; color: #666; font-size: 13px; }

.form-container {
    max-width: 600px;
    margin-bottom: 32px;
}

.run-btn { width: 100%; margin-top: 16px; font-weight: bold; }

.output-section {
    flex: 1;
    border-top: 1px dashed #ddd;
    padding-top: 20px;
}

.output-header {
    font-size: 14px;
    font-weight: 700;
    margin-bottom: 12px;
    color: #444;
}

.result-box {
    background: #f8f9fa;
    padding: 16px;
    border-radius: 8px;
    border: 1px solid #eee;
    overflow-x: auto;
}

.result-box pre { margin: 0; font-family: 'Fira Code', monospace; font-size: 13px; color: #333; }
.error-box { margin-bottom: 16px; }
</style>
