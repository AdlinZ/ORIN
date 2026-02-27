#!/usr/bin/env node

const express = require('express');
const cors = require('cors');

const app = express();
app.use(cors());
app.use(express.json());

const PORT = process.env.PORT || 8081;

// Health endpoint
app.get('/health', (req, res) => {
  res.json({
    status: 'ok',
    paired: false,
    require_pairing: true
  });
});

// Status endpoint
app.get('/status', (req, res) => {
  res.json({
    version: '0.1.7',
    uptime: 'mock-uptime',
    activeTasks: 0,
    status: 'ok'
  });
});

// Analysis endpoint - Mock response for ORIN
app.post('/api/analyze', (req, res) => {
  const { analysisType, data } = req.body;

  console.log('Analysis request:', analysisType, data);

  // Mock analysis response based on type
  const responses = {
    'PERFORMANCE': {
      title: 'Performance Analysis Report',
      summary: 'System performance analysis completed. CPU and memory usage are within normal ranges.',
      rootCause: 'No bottlenecks detected. System is operating efficiently.',
      recommendations: 'Current configuration is optimal. Continue monitoring.',
      severity: 'INFO'
    },
    'ANOMALY': {
      title: 'Anomaly Detection Report',
      summary: 'Anomaly analysis completed. No critical anomalies detected.',
      rootCause: 'Minor fluctuations in response time detected during peak hours.',
      recommendations: 'Consider implementing caching for frequently accessed resources.',
      severity: 'LOW'
    },
    'ANOMALY_DIAGNOSIS': {
      title: 'Anomaly Diagnosis Report',
      summary: 'System health analysis completed. No critical issues detected.',
      rootCause: 'Minor performance degradation due to high load.',
      recommendations: 'Consider scaling resources during peak hours.',
      severity: 'WARNING'
    },
    'TREND_FORECAST': {
      title: '24-Hour Trend Forecast',
      summary: 'System load expected to remain stable with minor fluctuations.',
      rootCause: 'Normal operational patterns detected.',
      recommendations: 'Continue current monitoring practices.',
      severity: 'INFO'
    },
    'SYSTEM_OVERVIEW': {
      title: 'System Overview',
      summary: 'All systems operational.',
      rootCause: 'N/A',
      recommendations: 'None required.',
      severity: 'INFO'
    }
  };

  const response = responses[analysisType] || responses['SYSTEM_OVERVIEW'];
  res.json(response);
});

// Self-healing endpoint - Mock response
app.post('/api/self-healing', (req, res) => {
  const { action, params } = req.body;

  console.log('Self-healing request:', action, params);

  // Simulate success
  res.json({
    success: true,
    action: action,
    result: 'Operation completed successfully',
    details: {
      timestamp: new Date().toISOString(),
      affected: params?.target || 'unknown'
    }
  });
});

// Default route
app.get('/', (req, res) => {
  res.json({
    message: 'ZeroClaw Mock Server',
    version: '1.0.0',
    endpoints: {
      health: '/health',
      status: '/status',
      analyze: '/api/analyze',
      selfHealing: '/api/self-healing'
    }
  });
});

app.listen(PORT, '0.0.0.0', () => {
  console.log(`🚀 ZeroClaw Mock Server running on port ${PORT}`);
  console.log(`   Health: http://localhost:${PORT}/health`);
  console.log(`   Status: http://localhost:${PORT}/status`);
  console.log(`   Analyze: http://localhost:${PORT}/api/analyze`);
  console.log(`   Self-Healing: http://localhost:${PORT}/api/self-healing`);
});
