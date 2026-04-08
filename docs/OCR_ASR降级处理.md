# OCR/ASR 降级处理指南

## 当前状态

ORIN 支持多种 OCR（图片文字识别）和 ASR（语音转文字）能力，当外部服务未配置时的降级处理如下：

## OCR 降级

### SiliconFlow VLM OCR（默认）

**配置方式：**
```properties
# application.properties
siliconflow.api.key=your-api-key
```

**降级行为：**
- 返回错误信息: `[OCR Error] SiliconFlow API key not configured`
- 日志警告: `SiliconFlow API key not configured, OCR failed`

### 其他 OCR 能力（规划中）

| 服务商 | 状态 | 降级返回 |
|--------|------|----------|
| 阿里云 OCR | TODO | `[OCR Error] AliCloud OCR not implemented` |
| 腾讯云 OCR | TODO | `[OCR Error] Tencent Cloud OCR not implemented` |
| 本地 Tesseract | TODO | `[OCR Error] Local OCR not implemented` |

## ASR 降级

### SiliconFlow ASR（默认）

**配置方式：**
```properties
# 使用与 OCR 相同的 SiliconFlow 配置
siliconflow.api.key=your-api-key
```

**降级行为：**
- 返回错误信息: `[ASR Error] SiliconFlow API key not configured`
- 日志警告: `SiliconFlow API key not configured, ASR failed`

### 本地 Whisper CLI（备选）

**配置方式：**
```properties
# 启用本地 Whisper
whisper.cli.enabled=true
whisper.cli.path=/usr/local/bin/whisper
```

**降级行为：**
- 如果 Whisper CLI 不存在: `[ASR Error] Whisper CLI not found`
- 如果音频文件不存在: `[ASR Error] Audio file not found: {path}`

## 错误码规范

| 错误码 | 含义 | 建议处理 |
|--------|------|----------|
| `OCR Error` 开头 | OCR 服务异常 | 检查 API Key 配置 |
| `ASR Error` 开头 | ASR 服务异常 | 检查 API Key 或本地 Whisper |
| 配置缺失 | 关键配置未填写 | 前往系统配置页面补充 |

## 监控建议

- 通过 `MonitorDashboard` 观察多模态服务调用成功率
- 失败任务可在 `知识任务` 页面查看 (`/knowledge/tasks/failed`)
- 建议配置告警规则：当 OCR/ASR 错误率 > 10% 时通知