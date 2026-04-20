---
name: release_note_creator
description: Create release notes for the project
---

# Release Note Creator

## Instructions
1. 阅读 `/skills/release_note_creator/references/range_determination.md` 了解如何确定变更范围
2. 与上一步确定的前序tag进行比较，确定变更范围
3. 阅读 `/skills/release_note_creator/references/release_note_guideline.md` 文件，了解如何撰写更新日志
4. 如版本号、前序tag或更新日志范围存在歧义，再与用户确认
5. 在无歧义时直接撰写更新日志

## tips
- 使用markdown格式
- 不允许使用 `gh` 命令
- 所有`scripts/`下的脚本都支持传递`--help`参数查看使用方法
