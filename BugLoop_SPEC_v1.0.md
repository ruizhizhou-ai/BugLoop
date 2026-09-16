



# BugLoop Spec v1.0

本文件是 BugLoop 一期规范的统一入口。原单体 Spec 已按职责拆分为七份文档，避免产品背景、技术方案和实现约束相互混杂。

## 文档目录

1. [产品需求](docs/01-product-requirements.md)：项目背景、用户、一期范围、业务流程和页面范围。
2. [调研与方案决策](docs/02-research-and-decisions.md)：开源方案调研、自研依据和最终决策。
3. [技术设计](docs/03-technical-design.md)：技术选型、架构、概要设计、安全、部署和风险。
4. [领域与实现规则](docs/04-domain-rules.md)：默认约定、领域模型、状态机、权限、数据库和并发规则。
5. [API Spec](docs/05-api-spec.md)：API 规范、错误码、接口契约、附件和 Markdown 安全规则。
6. [前端页面 Spec](docs/06-frontend-spec.md)：页面结构、交互规则和状态操作。
7. [质量与交付](docs/07-quality-and-delivery.md)：验收、非功能要求、实施顺序、一期边界和待确认项。

## 推荐阅读顺序

- 产品、评审或需求确认：`01 → 02 → 03 → 07`
- 后端开发：`01 → 04 → 05 → 07`
- 前端开发：`01 → 04 → 05 → 06 → 07`
- 测试与验收：`01 → 04 → 05 → 06 → 07`

## 事实来源规则

- 产品目标、范围和业务意图以 `01-product-requirements.md` 为准。
- 技术架构和非业务性设计选择以 `03-technical-design.md` 为准。
- 状态、权限、字段、数据库和事务规则以 `04-domain-rules.md` 为准。
- HTTP 接口、错误码、文件和 Markdown 安全契约以 `05-api-spec.md` 为准。
- 页面与交互行为以 `06-frontend-spec.md` 为准。
- 验收口径、一期不做范围及完成定义以 `07-quality-and-delivery.md` 为准。
- `02-research-and-decisions.md` 记录决策背景，不覆盖实现级规则。

如果不同文档出现真实冲突，应先更新对应职责的事实来源文档，再同步修正引用文档，不通过复制规则建立多个定义源。
