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
- **Done**（commit 1eaad649）`applicationId` / `namespace` = `io.github.eonewg.gnome`；
  源码包 main/test/androidTest 全量迁移；类重命名（GnomeApp/GnomeDatabase/GnomeTheme/…）；
  Manifest `${applicationId}` 占位符；数据库文件名 `gnome.db`；
  `settings.gradle` rootProject.name = Gnome；README 双语「新应用身份」迁移说明。
- 说明：applicationId 变更 = 新应用身份，旧 `me.mudkip.moememos` App 的本地数据不迁移
  （用户可并用或卸载）；本地账号（Local）数据不迁移，可用旧版导出 ZIP 后新版导入。
  此为文档化的正式迁移方案（提示词第六节允许）。

### Phase 2 — Domain Model
- **Done**（commit b9e82596）`core/model`：Memo（本地稳定 id + remoteId）、Attachment、
  MemoVisibility、SyncState（SYNCED / PENDING_CREATE / PENDING_UPDATE / PENDING_DELETE，
  由 Room flags 推导）；mapper：MemoEntity/ResourceEntity ↔ Domain、远程快照 → Domain、
  Visibility 双向桥接；MemoMapperTest 5 用例。
- Deferred：UI 全面切换到 domain 模型（随 Phase 11/12 逐 feature 迁移；
  Account/User 领域模型按需再迁）。

### Phase 3 — Local DataSource
- **Done**（commit a8e64c36）`data/local/LocalMemoDataSource` 成为 memos/resources/
  sync_operations 三表的唯一访问点：查询、SyncEngine 单行回写原语、带 outbox 的
  用户意图复合事务写；文件删除以「返回待删 URI、事务外执行」的方式留在上层。
  `TransactionRunner` 移至 data/local（RoomTransactionRunner 生产实现）；
  `SyncFileStore` 改收 URI 字符串保持 engine JVM 可测；`fileUriToPath` 用
  java.net.URI 解码（含 opaque 形式）。SyncEngine/SyncingRepository 全部改走 datasource。

### Phase 4 — Remote DataSource
- **Done**（commit d86e5c60）`RemoteRepository` → `data/remote/RemoteDataSource`；
  `MemosV0/V1Repository` → `data/remote/memos/MemosV0/V1RemoteDataSource`（git mv 保留历史，
  纯移动不改写）；StreamingRequestBodies 随迁。分页 / updateMask / 流式上传 /
  resource name 解析 / 鉴权 / 版本兼容逻辑未动，remote 层不感知 Room。

### Phase 5 — MemoRepository
- **Done**（commit d3fa1a8d）`data/repository/MemoRepository` 取代 SyncingRepository 与
  LocalDatabaseRepository（已删除），成为 AbstractMemoRepository 唯一实现：
  远端账号 = LocalMemoDataSource 事务 + outbox + SyncScheduler + SyncEngine（手动同步
  与 Worker 共用、互斥串行）；本地账号 = 纯持久化（无 outbox、deleteMemo 硬删，
  保持 LocalDatabaseRepository 原语义）。新增 `observeTimeline(): Flow<List<core.model.Memo>>`
  供 UI 渐进迁移。AccountService 双模式接线；worker 入口更名
  getSyncRepository/MemoRepositoryHandle；移除死代码 provideLocalDatabaseRepository。

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
- **Done**（commit 10595cde，同步核心架构验收）：非当前账号的后台同步已支持——
  `AccountService.getSyncRepository(accountKey)` 仅凭 accountKey + DataStore +
  加密 Token + Room 现场重建一次性 repository，当前账号复用活实例（handle.ownsLifecycle
  标记所有权，Worker 用毕 close）。
- 附件上传/删除链路的端到端测试需真机（涉及本地文件与 Uri；JVM 侧已用 Robolectric
  覆盖「先上传附件拿 remoteId 再被 memo 引用」的顺序测试）。

### Phase 9 — 移除 AbstractMemoRepository 旧抽象
- Partial：SyncingRepository / LocalDatabaseRepository 已随 Phase 5 删除；
  剩余 AbstractMemoRepository 接口（entity 出参）待 UI 全部迁到 domain 模型
  （observeTimeline）后移除，随 Phase 11/12 推进。

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

## 已完成：同步核心架构验收（2026-08-22，commit 10595cde）

对 Outbox + SyncEngine + WorkManager 做了七项专项验收，全部落地：

1. 调度竞态：policy 保持 APPEND_OR_REPLACE；`SyncScheduler.schedule` 对仍 ENQUEUED
   的链去重（该 run 尚未读库、写已先落库，跳过安全；RUNNING/结束链照常 append），
   避免突发写堆积全量 reconcile；`processOutbox` 改循环 drain（每轮重读表，
   一轮零删除即停——卡住的 op 等下一次调度，不空转）；outbox 排序加 rowid 决胜，
   REPLACE 合并后的 op 排到队尾。
2. 幂等：唯一索引 + REPLACE + 「处理时读行终态」实现 coalescing；新增 7 个引擎测试
   （10 次编辑合 1 op、update→delete 仅 1 次 delete、delete→restore 仅 1 次 update、
   create→delete 零网络调用、drain 循环轮内重试、附件 404 幂等成功）。
3. Worker 进程恢复：见 Phase 8 新增条目。
4. 错误状态：`SyncRetryPolicy` 抽取 + 4 单测；改用原始 retrofit code
   （`rawStatusCode()`）判定——sandwich 的 StatusCode 枚举对未映射码（507 等）会抛异常；
   401 → accessTokenInvalid → Worker 终止不重试、outbox 保留（`auth failure parks the
   outbox` 测试）；失败 op 持久化 lastError/attemptCount，成功才删除。
5. 附件顺序：上传先于 memo push（Robolectric 顺序测试）；`detachResource` 改为
   先入队 MEMO UPSERT（解除引用）再 ATTACHMENT DELETE。
6. 事务纯度：三处事务内文件 IO 移出（engine.applyRemoteMemo、updateMemo、deleteResource），
   崩溃窗口仅留孤儿文件。
7. 状态来源：unsyncedCount 改由 `observeUnsyncedCount` Room Flow 派生；
   syncing/errorMessage 保持进程内瞬态，持久失败明细在 outbox lastError/attemptCount。

## Known risks

- applicationId 变更后，旧版本用户需手动安装新版并重新登录；本地（Local）账号数据无法
  自动迁移——README 已说明，Release Notes 发版时需再次强调。
- gnome.db 为全新数据库，不存在旧 schema 升级路径；v1→v2 起建立 Migration 纪律。
- Migration test 为 androidTest，本机无模拟器时只能保证编译，需在有设备环境执行。
- WorkManager 替换 fire-and-forget 后，后台同步时序变化需真机 smoke test
  （离线创建 → 杀进程 → 联网 → 自动同步）。
- MemoRepository 编排层（依赖 FileStorage/SyncScheduler 具体类型）暂无 JVM 直测；
  其各组成部分（datasource 事务语义、engine 同步算法）已有测试覆盖。可后补
  Robolectric 测试（robolectric 已引入 testImplementation）。
