# Gnome 架构重构进度

依据 `Gnome_Android_Refactor_Prompt.md` 的渐进式重构计划。架构说明见
[architecture.md](architecture.md)。每个阶段完成后必须：
`修改 → format → compile（assembleDebug）→ unit tests → git diff review → commit`。

状态图例：**Done** / In progress / Next / Deferred。

## 阶段清单

### Phase 0 — 测试护栏
- **Done** 基线 `:app:testDebugUnitTest` 全绿（2026-08-22，HEAD 54afaa46，12 个测试类）。
- **Done** 建立本进度文档与 architecture.md。
- Deferred：CI 增加 push/PR 跑测试的 workflow（当前 CI 仅有 tag 触发的签名发布）。

### Phase 1 — Gnome identity（applicationId / namespace / package / branding）
- In progress `applicationId` / `namespace` = `io.github.eonewg.gnome`。
- In progress 源码包 `me.mudkip.moememos` → `io.github.eonewg.gnome`（main/test/androidTest 全量迁移）。
- In progress 类重命名：`MoeMemosApp→GnomeApp`、`MoeMemosDatabase→GnomeDatabase`、
  `MoeMemosTheme→GnomeTheme`、`MoeMemosDesign→GnomeDesign`、`MoeMemosTokens→GnomeTokens`、
  `MoeMemosFileProvider→GnomeFileProvider`、`MoeMemosGlanceWidget→GnomeGlanceWidget`、
  `MoeMemosGlanceWidgetReceiver→GnomeGlanceWidgetReceiver`、
  `MeoMemosGlanceWidgetConfigurationActivity→GnomeGlanceWidgetConfigurationActivity`、
  `MoeMemosException→GnomeException`。
- In progress Manifest：`authorities/taskAffinity` 改用 `${applicationId}` 占位符；shortcuts.xml 指向新包。
- In progress 数据库文件名 `moememos_database_localfirst` → `gnome.db`；SecureTokenStorage alias、
  widget work name、glance widget info xml 同步去 Moe Memos 化。
- In progress `settings.gradle` rootProject.name = Gnome；themes、fastlane 品牌文案修正。
- In progress README / README.zh-CN 增加「新应用身份，fresh install + 重新登录同步」迁移说明。
- 说明：applicationId 变更 = 新应用身份，旧 `me.mudkip.moememos` App 的本地数据不迁移
  （用户可并用或卸载）；本地账号（Local）数据不迁移，可用旧版导出 ZIP 后新版导入。
  此为文档化的正式迁移方案（提示词第六节允许）。

### Phase 2 — Domain Model
- Next：`core/model` 下建立独立 domain 模型（Memo / Attachment / User / Account /
  SyncState / MemoVisibility）与 Entity↔Domain mapper，先供 sync 层使用。
- Deferred：UI 全面切换到 domain 模型（随 Phase 11/12 逐 feature 迁移）。

### Phase 3 — Local DataSource
- Next：`data/local` 收敛为 LocalMemoDataSource（DAO + FileStorage + withTransaction），
  多行写操作原子化。

### Phase 4 — Remote DataSource
- Next：`MemosV0/V1Repository` 迁入 `data/remote/memos`（移动不改写），保持已验证的
  分页 / updateMask / 流式上传 / resource name 解析实现。

### Phase 5 — MemoRepository
- Next：统一 UI 访问入口 `MemoRepository`（observeTimeline / create / update / delete /
  archive / restore / setPinned），写后自动 `SyncScheduler.schedule`。
  UI 不再感知 LocalDatabaseRepository / SyncingRepository。

### Phase 6 — Room Outbox + Migration
- **Done** `sync_operations` 表（id/accountKey/entityType/entityId/operation/payload/
  createdAt/attemptCount/lastAttemptAt/lastError），操作：MEMO+ATTACHMENT × UPSERT+DELETE；
  唯一索引 (accountKey, entityType, entityId, operation) 以 REPLACE 合并重复入队。
- **Done** gnome.db v1→v2 显式 Migration + schema export（`2.json`，与 Migration SQL 逐列核对一致）。
- **Done** MigrationTestHelper 迁移测试（androidTest，本机无设备仅编译验证，待真机执行）。

### Phase 7 — SyncEngine + ConflictResolver
- **Done** `sync/ConflictResolver`：纯函数决策表（APPLY_REMOTE / MARK_SYNCED / PUSH_LOCAL /
  DUPLICATE / DELETE_REMOTE）+ memoEquivalent/hasRemoteChanged/资源签名，含完整单测。
- **Done** `sync/SyncEngine`：从 SyncingRepository 提取的唯一同步算法
  （拉全量 snapshot → 逐行 reconcile → push pending → drain outbox）；
  remote snapshot 合并与冲突复制改为事务写入。
- **Done** 修复继承自原实现的缺陷：第二遍本地扫描使用过期快照，会把本次同步刚推送到
  服务器的行（如冲突复制的新 Memo）立即本地清除、等下次同步才拉回。现以
  knownRemoteIds（快照 + 本次新建）判定。
- **Done** SyncEngineTest（JVM，内存 Fake DAO/远端）：离线 create/edit/delete、
  双改冲突复制、远端删除清理、outbox 失败保留 attemptCount 并在重试后清空、
  幂等 no-op 等场景。

### Phase 8 — WorkManager Sync
- **Done** `sync/SyncWorker` + `sync/SyncScheduler`；unique work `gnome-sync:<accountKey>`，
  ExistingWorkPolicy.APPEND_OR_REPLACE，Constraints CONNECTED，指数退避 30s 起。
- **Done** 错误分类：IOException/408/429/5xx → Result.retry()；401/403（GnomeException
  含 accessTokenInvalid）/其他 4xx → Result.failure()，不无限重试。
- **Done** 移除 `SyncingRepository.operationScope` fire-and-forget 推送：写入路径
  （create/update/delete/archive/restore/createResource/deleteResource）全部改为
  `database.withTransaction { 实体写入 + outbox 入队 }`，随后 `SyncScheduler.schedule`。
  手动"立即同步"（`sync()`）与 Worker 共用同一 SyncEngine，单一算法。
- **Done** 账号移除时 purge outbox（`SyncOperationDao.deleteAllForAccount`）。
- Deferred：非当前账号的后台同步（Worker 目前跳过非当前账号的 work，待 Phase 10
  AccountService 拆分出 RemoteDataSourceFactory 后支持）。附件上传/删除链路的
  端到端测试需真机（涉及本地文件与 Uri）。

### Phase 9 — 移除 SyncingRepository
- Deferred（Phase 5/8 稳定、UI 全部经 MemoRepository 后执行）。

### Phase 10 — 拆分 AccountService
- Deferred：AccountStore / TokenStore / AccountSession / MemosClientFactory /
  ServerCompatibilityChecker / AccountExportService / RemoteDataSourceFactory。

### Phase 11 — Timeline Route/ViewModel/Screen
- Deferred：拆 `MemosHomePage.kt`（1035 行）→ TimelineRoute/Screen/TopBar/List/SelectionBar/
  Drawer/ViewModel（StateFlow<UiState>）。

### Phase 12 — Editor Route/ViewModel/Screen
- Deferred：统一 Editor（普通新增 / Quick Tile / 编辑 / 分享 / Shortcut 同一核心），
  EditorViewModel 持有编辑状态与草稿。

### Phase 13 — Tag Parser + Tag Index
- Deferred：`MemosTagParser` 统一全 App 标签语义（先核对最新 Memos server 行为）；
  `memo_tags` Room 索引表 + 事务维护；autocomplete / drawer / filter / stats 全部走索引。

### Phase 14 — Room Search
- Deferred：搜索进 Room（首版 LIKE，保证中文 substring），Repository 暴露 searchMemos。

### Phase 15 — Stats Data Layer
- Deferred：StatsViewModel + 数据层读取，复用现有 calculateMemoStats。

### Phase 16 — 减少业务 CompositionLocal
- Deferred：移除 LocalMemos / LocalUserState / LocalRootNavController 全局暴露。

### Phase 17 — Navigation 重构
- Deferred：单独提交，评估 Navigation 3 typed key。

### Phase 18 — Widget / Quick Capture 架构清理
- Deferred。

### Phase 19 — 性能 / Baseline Profile / 回归
- Deferred。

### Phase 20 — 删除 Moe Memos 遗留命名与死代码
- In progress（Phase 1 已完成代码级命名；fastlane 元数据等外围遗留随发版流程清理）。

## Known risks

- applicationId 变更后，旧版本用户需手动安装新版并重新登录；本地（Local）账号数据无法
  自动迁移——README 已说明，Release Notes 发版时需再次强调。
- gnome.db 为全新数据库，不存在旧 schema 升级路径；v1→v2 起建立 Migration 纪律。
- Migration test 为 androidTest，本机无模拟器时只能保证编译，需在有设备环境执行。
- WorkManager 替换 fire-and-forget 后，后台同步时序变化需真机 smoke test
  （离线创建 → 杀进程 → 联网 → 自动同步）。
