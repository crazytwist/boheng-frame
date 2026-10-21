---
name: boheng-pre-commit-review
description: >-
  博恒提交/PR 前审查清单。用户说 review、检查、准备 commit/PR，或全栈改动完成时使用。
  可配合 Codex Bugbot / Security Review 子任务。
---

# 提交前审查

## 快速清单

- [ ] 改动范围与需求一致，无无关文件
- [ ] 后端：模块依赖未破坏（flow → api only）
- [ ] app-api：`tenant-id`、PermitAll 意图正确，无敏感数据泄露
- [ ] 前端：api ts 与 VO 同步；dict/labels 无硬编码业务枚举
- [ ] 无 `.env`、密钥、本地-only 配置误提交
- [ ] 错误码与文案符合 `ErrorCodeConstants` 风格

## 深度审查（用户明确要求时）

1. **Bug 向**：请用户对本地改动跑 Bugbot（Task 描述 `Bugbot`，Diff `uncommitted changes`）
2. **安全向**（app-api、多租户、外部设备回调）：Security Review 子任务，Diff `uncommitted changes`

## 输出

- 列出发现的问题（按严重度）
- 无问题时简短说明「可提交」，不强制 commit
