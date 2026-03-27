# ORIN 文档索引

`docs/` 目录里同时包含了当前仍在参考的说明、阶段性设计稿、以及历史评估记录。为了避免把过时内容当成现状，建议按下面顺序阅读。

## 优先阅读

- [README.md](/Users/adlin/Documents/Code/ORIN/README.md)：项目总览、目录结构、启动方式
- [部署指南.md](/Users/adlin/Documents/Code/ORIN/docs/部署指南.md)：本地开发与服务器部署
- [使用指南.md](/Users/adlin/Documents/Code/ORIN/docs/使用指南.md)：当前前端导航、常见入口、联调方式
- [API文档.md](/Users/adlin/Documents/Code/ORIN/docs/API文档.md)：接口分组与 OpenAPI 入口
- [系统功能实现评估报告.md](/Users/adlin/Documents/Code/ORIN/docs/系统功能实现评估报告.md)：当前能力边界与交付口径
- [原始设计与当前实现对照.md](/Users/adlin/Documents/Code/ORIN/docs/原始设计与当前实现对照.md)：早期设计和现有仓库的差异

## 开发专题

- [阶段0_改造基线.md](/Users/adlin/Documents/Code/ORIN/docs/阶段0_改造基线.md)
- [链路基线.md](/Users/adlin/Documents/Code/ORIN/docs/链路基线.md)
- [三层观测模型.md](/Users/adlin/Documents/Code/ORIN/docs/三层观测模型.md)
- [多模态降级策略与资源边界.md](/Users/adlin/Documents/Code/ORIN/docs/多模态降级策略与资源边界.md)
- [知识图谱存储模型与查询接口.md](/Users/adlin/Documents/Code/ORIN/docs/知识图谱存储模型与查询接口.md)
- [dev/](/Users/adlin/Documents/Code/ORIN/docs/dev)：集成与开发细节

## 历史/阶段性文档

- [真实完成度报告.md](/Users/adlin/Documents/Code/ORIN/docs/真实完成度报告.md)：偏“阶段性审视报告”，适合了解项目曾经的风险判断
- [archive/](/Users/adlin/Documents/Code/ORIN/docs/archive)：问题复盘、阶段实施记录、修复总结

## 阅读约定

- 看到“优秀 / 已完成 / 90%+”之类结论时，先和 [系统功能实现评估报告.md](/Users/adlin/Documents/Code/ORIN/docs/系统功能实现评估报告.md) 对照。
- 涉及具体接口参数时，以后端控制器和 OpenAPI 为准，不要只信旧截图或旧 curl 示例。
- 涉及页面入口时，以 `orin-frontend/src/router` 的当前路由定义为准。

## 本次清理说明

- 已移除无意义的 `.DS_Store`
- 已把使用、部署、API、评估类文档统一到当前仓库口径
- `docs/archive` 内文件保留为历史记录，不再作为当前实现承诺
