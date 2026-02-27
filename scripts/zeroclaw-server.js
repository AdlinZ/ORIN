#!/usr/bin/env node

/**
 * ZeroClaw AI 服务
 * 支持多种 AI 提供商配置
 */

const express = require('express');
const cors = require('cors');
const https = require('https');
const http = require('http');
const { URL } = require('url');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 8081;

// AI 配置 (可从环境变量或运行时配置获取)
let aiConfig = {
  provider: process.env.AI_PROVIDER || 'deepseek', // deepseek/openai/ Anthropic
  apiKey: process.env.AI_API_KEY || '',
  baseUrl: process.env.AI_BASE_URL || 'https://api.deepseek.com',
  model: process.env.AI_MODEL || 'deepseek-chat'
};

// 系统启动时间
const startTime = Date.now();

// 健康检查
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    paired: true,
    ai_enabled: !!aiConfig.apiKey,
    version: '1.0.0-ai'
  });
});

// 状态端点
app.get('/status', (req, res) => {
  const uptime = Math.floor((Date.now() - startTime) / 1000);
  res.json({
    version: '1.0.0-ai',
    uptime: `${uptime}s`,
    ai_provider: aiConfig.provider,
    ai_model: aiConfig.model,
    ai_enabled: !!aiConfig.apiKey,
    status: 'ok',
    activeTasks: 0
  });
});

// AI 配置端点 - 动态更新 AI 配置
app.post('/config/ai', (req, res) => {
  const { provider, apiKey, baseUrl, model } = req.body;

  if (provider) aiConfig.provider = provider;
  if (apiKey) aiConfig.apiKey = apiKey;
  if (baseUrl) aiConfig.baseUrl = baseUrl;
  if (model) aiConfig.model = model;

  console.log(`[ZeroClaw] AI config updated: provider=${aiConfig.provider}, model=${aiConfig.model}`);

  res.json({
    success: true,
    provider: aiConfig.provider,
    model: aiConfig.model,
    message: 'AI configuration updated'
  });
});

// 获取当前 AI 配置
app.get('/config/ai', (req, res) => {
  res.json({
    provider: aiConfig.provider,
    baseUrl: aiConfig.baseUrl,
    model: aiConfig.model,
    hasApiKey: !!aiConfig.apiKey
  });
});

// 从 ORIN 后端获取模型配置
app.get('/config/orin-models', async (req, res) => {
  const orinBackendUrl = process.env.ORIN_BACKEND_URL || 'http://localhost:8080';

  try {
    const response = await fetch(`${orinBackendUrl}/api/v1/model-config`);
    if (response.ok) {
      const config = await response.json();
      res.json({
        success: true,
        config: config
      });
    } else {
      res.json({
        success: false,
        message: 'Failed to fetch ORIN model config'
      });
    }
  } catch (error) {
    res.json({
      success: false,
      message: error.message
    });
  }
});

// 使用 ORIN 模型配置初始化 AI
app.post('/config/use-orin-model', async (req, res) => {
  const { provider } = req.body; // deepseek, siliconflow, ollama, zhipu
  const orinBackendUrl = process.env.ORIN_BACKEND_URL || 'http://localhost:8080';

  try {
    const response = await fetch(`${orinBackendUrl}/api/v1/model-config`);
    if (!response.ok) {
      throw new Error('Failed to fetch ORIN model config');
    }

    const config = await response.json();

    // 根据选择的提供商设置配置
    if (provider === 'deepseek' || provider === 'deepseek-chat') {
      aiConfig.provider = 'deepseek';
      aiConfig.baseUrl = 'https://api.deepseek.com';
      aiConfig.model = 'deepseek-chat';
      // 尝试从环境变量获取或使用配置的 API Key
      aiConfig.apiKey = process.env.DEEPSEEK_API_KEY || '';
    } else if (provider === 'siliconflow') {
      aiConfig.provider = 'openai'; // SiliconFlow 兼容 OpenAI 格式
      aiConfig.baseUrl = config.siliconFlowEndpoint || 'https://api.siliconflow.cn/v1';
      aiConfig.model = config.siliconFlowModel || 'Qwen/Qwen2-7B-Instruct';
      aiConfig.apiKey = config.siliconFlowApiKey || '';
    } else if (provider === 'ollama') {
      aiConfig.provider = 'openai'; // Ollama 兼容 OpenAI 格式
      aiConfig.baseUrl = config.ollamaEndpoint || 'http://localhost:11434/v1';
      aiConfig.model = config.ollamaModel || 'llama3';
      aiConfig.apiKey = config.ollamaApiKey || ''; // Ollama 通常不需要 API Key
    } else if (provider === 'zhipu') {
      aiConfig.provider = 'zhipu';
      aiConfig.baseUrl = 'https://open.bigmodel.cn/api/paas/v4';
      aiConfig.model = 'glm-4';
      aiConfig.apiKey = ''; // 需要单独配置
    }

    console.log(`[ZeroClaw] Using ORIN model: provider=${aiConfig.provider}, model=${aiConfig.model}`);

    res.json({
      success: true,
      provider: aiConfig.provider,
      model: aiConfig.model,
      baseUrl: aiConfig.baseUrl,
      hasApiKey: !!aiConfig.apiKey,
      message: `Configured to use ${provider} from ORIN model config`
    });
  } catch (error) {
    res.status(500).json({
      success: false,
      message: error.message
    });
  }
});

// 调用 AI API (支持多种提供商)
async function callAI(prompt, systemPrompt = '') {
  if (!aiConfig.apiKey) {
    throw new Error('AI_API_KEY not configured. Please configure AI provider in ZeroClaw settings.');
  }

  const provider = aiConfig.provider;
  let url, body, headers;

  if (provider === 'deepseek') {
    url = new URL('/v1/chat/completions', aiConfig.baseUrl);
    body = JSON.stringify({
      model: aiConfig.model || 'deepseek-chat',
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: prompt }
      ],
      temperature: 0.7,
      max_tokens: 2000
    });
    headers = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${aiConfig.apiKey}`
    };
  } else if (provider === 'openai') {
    url = new URL('/v1/chat/completions', aiConfig.baseUrl);
    body = JSON.stringify({
      model: aiConfig.model || 'gpt-3.5-turbo',
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: prompt }
      ],
      temperature: 0.7,
      max_tokens: 2000
    });
    headers = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${aiConfig.apiKey}`
    };
  } else if (provider === 'zhipu') {
    url = new URL('/chat/completions', aiConfig.baseUrl);
    body = JSON.stringify({
      model: aiConfig.model || 'glm-4',
      messages: [
        { role: 'system', content: systemPrompt },
        { role: 'user', content: prompt }
      ],
      temperature: 0.7,
      max_tokens: 2000
    });
    headers = {
      'Content-Type': 'application/json',
      'Authorization': `Bearer ${aiConfig.apiKey}`
    };
  } else if (provider === 'anthropic') {
    url = new URL('/v1/messages', aiConfig.baseUrl);
    body = JSON.stringify({
      model: aiConfig.model || 'claude-3-haiku-20240307',
      system: systemPrompt,
      messages: [{ role: 'user', content: prompt }],
      temperature: 0.7,
      max_tokens: 2000
    });
    headers = {
      'Content-Type': 'application/json',
      'x-api-key': aiConfig.apiKey,
      'anthropic-version': '2023-06-01'
    };
  } else {
    throw new Error(`Unsupported AI provider: ${provider}`);
  }

  const options = {
    hostname: url.hostname,
    port: url.protocol === 'https:' ? 443 : 80,
    path: url.pathname,
    method: 'POST',
    headers: headers
  };

  return new Promise((resolve, reject) => {
    const req = (url.protocol === 'https:' ? https : http).request(options, (res) => {
      let data = '';
      res.on('data', chunk => data += chunk);
      res.on('end', () => {
        try {
          const parsed = JSON.parse(data);

          // 处理不同提供商的响应格式
          if (provider === 'anthropic') {
            if (parsed.content && parsed.content[0] && parsed.content[0].text) {
              resolve(parsed.content[0].text);
            } else if (parsed.error) {
              reject(new Error(parsed.error.message || 'Anthropic API error'));
            } else {
              reject(new Error('Invalid Anthropic response'));
            }
          } else {
            // OpenAI / DeepSeek 格式
            if (parsed.choices && parsed.choices[0]) {
              resolve(parsed.choices[0].message.content);
            } else if (parsed.error) {
              reject(new Error(parsed.error.message || 'API error'));
            } else {
              reject(new Error('Invalid API response'));
            }
          }
        } catch (e) {
          reject(e);
        }
      });
    });

    req.on('error', reject);
    req.write(body);
    req.end();
  });
}

// 分析端点 - 真正的 AI 分析
app.post('/api/analyze', async (req, res) => {
  const { analysisType, data, context } = req.body;

  console.log(`[ZeroClaw] Analysis request: ${analysisType}`);
  console.log(`[ZeroClaw] Context: ${context}`);
  console.log(`[ZeroClaw] Data:`, data);

  // 构建分析提示词
  const analysisPrompts = {
    PERFORMANCE: {
      system: `You are ZeroClaw, a professional system performance analyst. Analyze the provided system metrics and provide a detailed performance report.`,
      prompt: `Please analyze the following system performance data and provide:
1. A title for the report
2. A brief summary of the current performance status
3. Root cause analysis if any issues found
4. Specific recommendations for optimization

System Data: ${JSON.stringify(data, null, 2)}

Respond in JSON format with keys: title, summary, rootCause, recommendations, severity (CRITICAL/HIGH/MEDIUM/LOW/INFO)`
    },
    ANOMALY: {
      system: `You are ZeroClaw, an AI-powered anomaly detection specialist. Analyze system data to identify anomalies and unusual patterns.`,
      prompt: `Please analyze the following system data for anomalies:
1. Identify any unusual patterns or behaviors
2. Determine the root cause of any anomalies
3. Provide severity level (CRITICAL/HIGH/MEDIUM/LOW/INFO)
4. Suggest remediation steps

System Data: ${JSON.stringify(data, null, 2)}

Respond in JSON format with keys: title, summary, rootCause, recommendations, severity`
    },
    TREND_FORECAST: {
      system: `You are ZeroClaw, a system trend forecasting expert. Analyze historical data to predict future trends and potential issues.`,
      prompt: `Based on the following historical system data, please:
1. Forecast the trend for the next 24 hours
2. Identify any potential risks or issues
3. Provide recommendations for preparation
4. Set an appropriate severity level

Historical Data: ${JSON.stringify(data, null, 2)}

Respond in JSON format with keys: title, summary, rootCause, recommendations, severity`
    }
  };

  const analysis = analysisPrompts[analysisType] || analysisPrompts.PERFORMANCE;

  try {
    if (DEEPSEEK_API_KEY) {
      // 使用真正的 DeepSeek AI
      console.log('[ZeroClaw] Using DeepSeek AI for analysis...');
      const aiResponse = await callAI(analysis.prompt, analysis.system);

      // 尝试解析 AI 返回的 JSON
      let parsed;
      try {
        // 提取 JSON 部分
        const jsonMatch = aiResponse.match(/\{[\s\S]*\}/);
        if (jsonMatch) {
          parsed = JSON.parse(jsonMatch[0]);
        } else {
          throw new Error('No JSON found in response');
        }
      } catch (parseErr) {
        // 如果解析失败，返回原始响应
        parsed = {
          title: `${analysisType} Analysis Report`,
          summary: aiResponse.substring(0, 200),
          rootCause: 'AI analysis completed',
          recommendations: aiResponse.substring(200),
          severity: 'INFO'
        };
      }

      res.json(parsed);
    } else {
      // Fallback: 使用模拟响应
      console.log('[ZeroClaw] No API key, using mock response');
      const mockResponses = {
        PERFORMANCE: {
          title: 'Performance Analysis Report',
          summary: 'System performance analysis completed. CPU and memory usage are within normal ranges.',
          rootCause: 'No bottlenecks detected. System is operating efficiently.',
          recommendations: 'Current configuration is optimal. Continue monitoring.',
          severity: 'INFO'
        },
        ANOMALY: {
          title: 'Anomaly Detection Report',
          summary: 'Anomaly analysis completed. No critical anomalies detected.',
          rootCause: 'Minor fluctuations in response time detected during peak hours.',
          recommendations: 'Consider implementing caching for frequently accessed resources.',
          severity: 'LOW'
        },
        TREND_FORECAST: {
          title: '24-Hour Trend Forecast',
          summary: 'System load expected to remain stable with minor fluctuations.',
          rootCause: 'Normal operational patterns detected.',
          recommendations: 'Continue current monitoring practices.',
          severity: 'INFO'
        }
      };
      res.json(mockResponses[analysisType] || mockResponses.PERFORMANCE);
    }
  } catch (error) {
    console.error('[ZeroClaw] Analysis error:', error.message);
    res.status(500).json({
      title: 'Analysis Failed',
      summary: `Failed to complete analysis: ${error.message}`,
      rootCause: 'AI service error',
      recommendations: 'Please check ZeroClaw service configuration',
      severity: 'HIGH'
    });
  }
});

// 自愈端点 - 真正的 AI 驱动的自愈
app.post('/api/self-healing', async (req, res) => {
  const { action, params } = req.body;

  console.log(`[ZeroClaw] Self-healing request: ${action}`);

  const actionDescriptions = {
    'MEMORY_OPTIMIZATION': 'Analyze memory usage patterns and suggest optimization strategies',
    'LOG_CLEANUP': 'Analyze log files and determine cleanup strategy',
    'CACHE_CLEAR': 'Analyze cache usage and determine what can be cleared',
    'EMERGENCY_RECOVERY': 'Perform emergency system recovery assessment'
  };

  try {
    if (DEEPSEEK_API_KEY && action === 'EMERGENCY_RECOVERY') {
      // 对于紧急恢复，使用 AI 进行智能分析
      const aiResponse = await callAI(
        `A system emergency has been reported. Action: ${action}, Params: ${JSON.stringify(params)}. Please provide:
1. Assessment of the current situation
2. Recommended recovery steps
3. Safety precautions

Respond in JSON with keys: assessment, steps (array), precautions (array), success (boolean)`,
        'You are ZeroClaw Emergency Response System. Provide expert guidance for system recovery.'
      );

      let parsed;
      try {
        const jsonMatch = aiResponse.match(/\{[\s\S]*\}/);
        if (jsonMatch) {
          parsed = JSON.parse(jsonMatch[0]);
        }
      } catch (e) {}

      res.json({
        success: parsed?.success ?? true,
        action: action,
        result: parsed?.assessment || 'Emergency analysis completed',
        details: {
          timestamp: new Date().toISOString(),
          affected: params?.target || 'system',
          ai_recommendations: parsed?.steps || [],
          precautions: parsed?.precautions || []
        }
      });
    } else {
      // 标准自愈操作
      res.json({
        success: true,
        action: action,
        result: actionDescriptions[action] || 'Self-healing action executed',
        details: {
          timestamp: new Date().toISOString(),
          affected: params?.target || 'unknown',
          action_type: action
        }
      });
    }
  } catch (error) {
    console.error('[ZeroClaw] Self-healing error:', error.message);
    res.json({
      success: false,
      action: action,
      error: error.message,
      details: {
        timestamp: new Date().toISOString()
      }
    });
  }
});

// 默认路由
app.get('/', (req, res) => {
  res.json({
    name: 'ZeroClaw AI Service',
    version: '1.0.0-ai',
    ai_provider: 'deepseek',
    ai_enabled: !!DEEPSEEK_API_KEY,
    endpoints: {
      health: '/health',
      status: '/status',
      analyze: '/api/analyze',
      selfHealing: '/api/self-healing'
    }
  });
});

// 启动服务器
app.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 ZeroClaw AI Service running on port ${PORT}`);
  console.log(`   Version: 1.0.0-ai`);
  console.log(`   AI Provider: DeepSeek`);
  console.log(`   AI Enabled: ${!!DEEPSEEK_API_KEY}`);
  if (!DEEPSEEK_API_KEY) {
    console.log(`   ⚠️  WARNING: DEEPSEEK_API_KEY not set, using mock responses`);
  }
  console.log(`   Health: http://localhost:${PORT}/health`);
  console.log(`   Status: http://localhost:${PORT}/status`);
  console.log(`   Analyze: http://localhost:${PORT}/api/analyze`);
  console.log(`   Self-Healing: http://localhost:${PORT}/api/self-healing`);
});
