<template>
  <div class="page-container">
    <PageHeader 
      title="模型训练看板" 
      description="配置并启动大模型微调任务，实时监控 Loss 曲线与训练状态"
      icon="TrendCharts"
    />


    <el-row :gutter="24">
      <!-- Left: Configurations -->
      <el-col :lg="10" :md="24">
        <el-card shadow="never" class="config-card">
          <template #header>
            <div class="card-header">
              <span class="module-title">训练超参数配置</span>
              <el-tag type="info" size="small">Llama-Factory Engine</el-tag>
            </div>
          </template>
          
          <el-form :model="trainConfig" label-position="top">
            <el-form-item label="基础模型">
              <el-select v-model="trainConfig.baseModel" style="width: 100%">
                <el-option label="llama3-8b-instruct" value="llama3-8b" />
                <el-option label="qwen1.5-7b-chat" value="qwen7b" />
                <el-option label="baichuan2-13b-chat" value="baichuan13b" />
              </el-select>
            </el-form-item>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="微调方法">
                  <el-select v-model="trainConfig.method" style="width: 100%">
                    <el-option label="LoRA" value="lora" />
                    <el-option label="Full Fine-tuning" value="full" />
                    <el-option label="P-Tuning v2" value="ptuning" />
                  </el-select>
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="学习率">
                  <el-input-number v-model="trainConfig.lr" :step="0.00001" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <el-form-item label="训练数据集">
              <el-select v-model="trainConfig.dataset" multiple collapse-tags style="width: 100%">
                <el-option label="customer_support_qa" value="ds1" />
                <el-option label="legal_corpus" value="ds2" />
              </el-select>
            </el-form-item>

            <el-row :gutter="16">
              <el-col :span="12">
                <el-form-item label="训练轮数 (Epochs)">
                  <el-input-number v-model="trainConfig.epochs" :min="1" style="width: 100%" />
                </el-form-item>
              </el-col>
              <el-col :span="12">
                <el-form-item label="Batch Size">
                  <el-input-number v-model="trainConfig.batchSize" :min="1" style="width: 100%" />
                </el-form-item>
              </el-col>
            </el-row>

            <div style="margin-top: 24px; display: flex; gap: 12px;">
              <el-button type="primary" style="flex: 1" @click="startTrain" :loading="training">开始训练</el-button>
              <el-button style="flex: 1" plain @click="stopTrain" :disabled="!training">停止任务</el-button>
            </div>
          </el-form>
        </el-card>
      </el-col>

      <!-- Right: Live Monitor -->
      <el-col :lg="14" :md="24">
        <el-card shadow="never" class="monitor-card" style="margin-bottom: 24px;">
          <template #header>
            <div class="card-header">
              <span class="module-title">训练实况 (Loss Curve)</span>
              <el-tag :type="training ? 'success' : 'info'" effect="plain">
                <span v-if="training" class="status-dot online"></span>
                {{ training ? '正在训练: 12.5%' : '未启动' }}
              </el-tag>
            </div>
          </template>
          <div class="chart-container" style="height: 300px;">
             <!-- LineChart Component -->
             <LineChart title="Global Loss" :data="lossData" yAxisName="Loss" color="#6366f1" />
          </div>
        </el-card>

        <el-card shadow="never" class="log-card">
          <template #header>
             <span class="module-title" style="font-size: 14px;">运行日志</span>
          </template>
          <div class="log-console">
             <div v-for="(log, i) in trainLogs" :key="i" class="log-line">
               <span class="timestamp">[{{ log.time }}]</span>
               <span class="msg">{{ log.msg }}</span>
             </div>
          </div>
        </el-card>
      </el-col>
    </el-row>
  </div>
</template>

<script setup>
import { ref, reactive } from 'vue';
import { ElMessage } from 'element-plus';
import LineChart from '@/components/LineChart.vue';
import PageHeader from '@/components/PageHeader.vue';
import { TrendCharts } from '@element-plus/icons-vue';

const training = ref(false);
const trainConfig = reactive({
  baseModel: 'llama3-8b',
  method: 'lora',
  lr: 0.00005,
  dataset: ['ds1'],
  epochs: 3,
  batchSize: 4
});

const lossData = ref([
  { timestamp: 1710910000000, value: 2.5 },
  { timestamp: 1710911000000, value: 2.1 },
  { timestamp: 1710912000000, value: 1.8 },
  { timestamp: 1710913000000, value: 1.6 },
  { timestamp: 1710914000000, value: 1.4 }
]);

const trainLogs = ref([
  { time: '10:05:22', msg: 'Loading base model: meta-llama3-8b...' },
  { time: '10:05:45', msg: 'Processing datasets: dataset_1 (2.5k samples)' },
  { time: '10:06:01', msg: 'LoRA adapter initialized successfully.' },
  { time: '10:06:05', msg: 'Starting training loop (Epoch 1/3)...' }
]);

const startTrain = () => {
  training.value = true;
  ElMessage.success('训练任务已提交至集群');
};

const stopTrain = () => {
  training.value = false;
  ElMessage.warning('训练任务已中止');
};
</script>

<style scoped>
.page-container {
  padding: 0;
}
.log-console {
  background: #000;
  color: #10b981;
  padding: 16px;
  border-radius: 8px;
  height: 200px;
  overflow-y: auto;
  font-family: 'Courier New', Courier, monospace;
  font-size: 12px;
  line-height: 1.5;
}

.log-line .timestamp {
  color: #64748b;
  margin-right: 8px;
}

.config-card {
  border-radius: var(--radius-xl) !important;
}

.card-header {
  display: flex;
  justify-content: space-between;
  align-items: center;
}
</style>
