---
name: boheng-fullstack-feature
description: >-
  博恒 Codex Agent 全栈功能开发流程（Java 模块 + web）。在新功能、平台能力、
  app-api/管理端 API 与 Vue 联调时使用；先 plan 再 implement，禁止跳过契约步骤。
---

# 博恒全栈功能开发

## 何时使用

- 新功能或跨 `boheng-module-*` + `web` 的改动
- 设备/流程 app-api 与前端管理页一起改
- 用户要求「前后端一起」或「按规范流程做」

## 流程（必须按序）

### 1. 范围与契约（先不写业务代码）

- 列出：后端模块、Controller 路径、VO 字段、错误码是否需要新增
- 列出：`web/src/api/*.ts`、Vue 文件路径
- 外部兼容（如清华 PascalCase）只在 app 层 VO 体现

产出：简短 plan（可写在回复中；若仓库已有 `.specify/` 则写入对应 feature 目录）

### 2. Implement 顺序

1. `*-api` 契约（若跨模块）
2. 实现模块 Service / Mapper
3. Controller + VO
4. `web/src/api`
5. Vue 视图 / 字典 / labels

### 3. 验证

- 说明如何本地验证（端口、tenant-id、示例请求）
- 不声称已 `mvn compile` 除非实际执行过

### 4. 提交前

- 触发 **boheng-pre-commit-review** skill 的检查项
- 动过 `controller.app` 或 `@PermitAll` 时做安全向自查

## 与 Spec Kit

若项目已 `specify init`，优先使用 `/speckit-specify`、`/speckit-plan`、`/speckit-tasks`、`/speckit-implement`、`/speckit-converge` 替代上述 plan 步骤，制品落在 `.specify/`。

## 禁止

- 跳过 API 层直接改 Vue 写死 URL
- flow 模块依赖 wms 实现 JAR
- 未请求时 git commit
