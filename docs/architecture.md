# Gnome 架构文档

本文件记录 Gnome 的目标架构与当前实际状态。重构进度与阶段清单见 [refactor-plan.md](refactor-plan.md)。
重构需求全文见仓库根目录 `Gnome_Android_Refactor_Prompt.md`。

## 产品定位

Gnome 是以 Memos 为远程同步后端、以 Android 本地 Room 数据库为主要数据源的
local-first / offline-first memo 客户端。核心体验：打开即用、离线可记录、保存不等服务器、
网络恢复后自动同步。

## 最高原则

1. **Room 是唯一主要 Source of Truth**。Timeline / Search / Tags / Stats / Detail 一律读 Room；
   远程数据只能通过 SyncEngine 写入 Room，再由 Flow 通知 UI。
   数据流：`Compose UI → ViewModel → Repository → Room`；同步：`Room ↔ SyncEngine ↔ Memos Server`。
2. **关键写入先本地成功**。创建/编辑/删除/归档/置顶：Room transaction 成功即视为用户操作成功，
   同步任务写入 Outbox，由 WorkManager 后台执行。
3. **不使用普通 Coroutine 承担持久同步**。进程可被随时杀死，关键同步必须由
   Room Outbox + WorkManager + SyncWorker + SyncEngine 承载。

## 目标包结构（单 `:app` module，用 package 划界）

```text
io.github.eonewg.gnome
├── app            # GnomeApp / MainActivity / navigation
├── core           # model / common / files / design
├── data
│   ├── local      # GnomeDatabase、DAO、Entity、FileStorage
│   ├── remote     # memos v0 / v1 远程数据源（唯一允许感知 Memos 版本差异的地方）
│   ├── account    # 账号、Token、客户端工厂
│   └── repository # MemoRepository 等 UI 统一入口
├── sync           # SyncEngine / SyncScheduler / SyncWorker / ConflictResolver / SyncStatusRepository
├── feature        # timeline / editor / detail / search / tags / stats / account / settings
├── quickcapture   # QuickMemoActivity
└── widget
```

依赖方向：`feature → (repository / sync status) → (data.local / data.remote) / sync`。
feature 不感知 MemosV0/V1、Room schema、Repository 实现细节。

## 数据与同步模型

```text
写路径（用户操作）:
  ViewModel → MemoRepository → [Room transaction: 实体写入 + Outbox 任务] → 立即返回
  → SyncScheduler.schedule(accountKey) → WorkManager(SyncWorker) → SyncEngine

同步（SyncEngine.reconcile）:
  1. 拉取服务器完整 snapshot（第一阶段保留全量 reconcile，暂不做增量）
  2. 逐条 local/remote reconcile（ConflictResolver：双方都改 → 保留服务器版本 +
     复制本地版本为新 Memo，绝不静默丢用户文字）
  3. push 本地 pending（含附件上传）
  4. 消费 Outbox 任务

同步触发:
  - 后台：WorkManager unique work `gnome-sync:<accountKey>`，约束 NetworkType.CONNECTED，
    指数退避；网络错误/超时/5xx 重试，401/403 不无限重试
  - 手动“立即同步”：SyncScheduler.syncNow → 同一个 SyncEngine（同一套算法，非第二套实现）
```

错误分类：Network / Timeout / Server5xx / Unauthorized / Forbidden / UnsupportedServer /
BadRequest / MissingAttachment / Unknown。

## 数据库

- 文件名 `gnome.db`（applicationId 变更后 fresh install，无旧数据迁移负担；
  对旧版 Moe Memos 身份 App 的迁移方案为「重新登录 + 全量同步」，见 README）。
- Schema 变更纪律：bump version + 显式 Migration + schema export + Migration test，
  禁止 `fallbackToDestructiveMigration()`。
- 表：`memos`、`resources`、`sync_operations`（Outbox）；后续 `memo_tags`（标签索引）。

## 冲突策略（第一阶段保持既有语义）

本地与服务器同时修改同一 Memo：服务器版本保留原身份，本地修改复制为一条新 Memo 推送到服务器。
规则提取在 `sync/ConflictResolver`，有完整单测。

## 品牌与许可证

- applicationId / namespace / package：`io.github.eonewg.gnome`。
- 用户可见文案品牌统一 Gnome；代码命名统一 Gnome 前缀；不再使用 Moe Memos 命名。
- 项目源自 MoeMemosAndroid（GPLv3），LICENSE 与 README 致谢保留，不因品牌独立而删除。

---

## 附：重构前（2026-08 基线，commit 54afaa46）的架构审计摘要

作为重构起点记录，重构完成后此节仅作历史参考。

- 包名 `me.mudkip.moememos`，applicationId/namespace 同；DB `moememos_database_localfirst` v1
  （仅 `memos` + `resources` 两表，无 Migration 定义，bump version 会崩溃）。
- `MemoEntity` 直接充当 UI 模型（Compose/ViewModel 直接消费 Room Entity）；
  domain `Memo` 仅在远程层使用。
- Repository：`AbstractMemoRepository`（返回 `ApiResponse<MemoEntity>`）→
  `LocalDatabaseRepository`（本地账号）/ `SyncingRepository`（978 行，本地写入 +
  `CoroutineScope(SupervisorJob()+Dispatchers.IO)` fire-and-forget 推送 + 全量 reconcile）。
  离线写入靠 `needsSync/isDeleted/lastSyncedAt` 行标记（持久），但推送任务不持久：
  进程死亡后丢失，仅能等下次全量 sync 补偿；附件远程删除任务可能彻底丢失。
- 无 Outbox 表；WorkManager 仅用于 widget 30 分钟周期刷新；同步只在
  打开 App / 下拉刷新 / 下一次编辑时触发。
- `AccountService` 巨型类：DataStore 账号、SecureTokenStorage、Retrofit/OkHttp 工厂、
  版本兼容策略（V0 ≥0.21.0；V1 0.27.0–0.30.0，更高版本需用户确认）、Repository 装配、
  本地账号 ZIP 导出。
- 标签：无索引表，每次全量 Markdown AST 解析；存在 3 处不同 Regex。
- 搜索：内存 substring 过滤整个 memo 列表。Stats：直接读 `LocalMemos.current.memos`。
- UI：业务 CompositionLocal（`LocalMemos`/`LocalUserState`/`LocalRootNavController`）全局暴露；
  `MemosHomePage.kt` 1035 行；编辑器文本状态在 Composable 内、每个入口各自实例化 ViewModel。
- 优点（重构中保留）：离线写入语义、冲突复制策略、V0/V1 网络实现
  （分页 / updateMask / 流式上传 / resource name 解析）、SyncStatus UX、Glance widgets。
