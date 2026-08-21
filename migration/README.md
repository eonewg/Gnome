# flomo → Memos v0.30 迁移

本目录中的工具读取 flomo 导出的 HTML ZIP，并通过 Memos v0.30 官方 API 创建 Memo。

安全约束：

- 默认只做预览，不连接账号、不写入数据。
- 默认可见性为 `PRIVATE`。
- PAT 只从 `MEMOS_PAT` 环境变量或隐藏输入读取，不写入文件。
- 每创建一条就更新 `*.state.json`，因此可断点继续或只回滚本次创建的数据。
- 超过 Memos 8192 字节限制的正文会按段落无损拆分，并添加 `flomo 原记录分段 i/n` 标记。
- 全部写入后再次读取服务器，按“创建时间 + 正文哈希”做全量校验。
- 写入前读取服务器已有 Memo，并按“创建时间 + 正文哈希”跳过重复项。
- 如发现媒体引用会停止，而不是静默丢弃附件。

预览：

```powershell
python .\migration\import_flomo.py '.\migration\flomo-export.zip' --dry-run
```

建议先试导入 3 条：

```powershell
python .\migration\import_flomo.py '.\migration\flomo-export.zip' --limit 3 --yes
```

确认试导入正确后全量继续：

```powershell
python .\migration\import_flomo.py '.\migration\flomo-export.zip' --yes
```

回滚本工具创建的记录：

```powershell
python .\migration\import_flomo.py '.\migration\flomo-export.zip' --rollback --yes
```

执行写入或回滚时，工具会隐藏提示输入 PAT；令牌仅保存在当前进程内存中。
