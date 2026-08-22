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
  Phase 10 已建立 domain 契约 `interface MemoRepository`（core.model 出参），
  `MemoRepositoryImpl` 同时实现两者，旧 entity 接口仅作迁移 adapter。
  剩余 entity 调用方：ArchivedMemoListViewModel、ExploreList、ResourceListPage——
  随 Phase 17/18 页面迁移逐步收窄后移除（MemosViewModel / MemoDetailPage /
  ArchivedMemoPage / UserStateViewModel 已随 Phase 16 迁走）。

### Phase 10 — 拆分 AccountService
- **Done**（4 commits：c03428ed / 4d54ed0e / 2a3d8e79 / 985db918 + b1a6d936 微调）
  AccountService（原 27KB）瘦身为 ~125 行门面，职责拆入 `data/account/`：
  - `AccountStore`：账号持久化 + SecureTokenStorage 令牌合并 + 当前账号；
  - `MemosClientFactory`：Retrofit/OkHttp 客户端构建（token 拦截器）；
  - `RemoteDataSourceFactory`：Account → MemosRemote（V0/V1/Local）；
  - `ServerCompatibilityChecker`：登录/同步版本检测（LoginCompatibility /
    SyncCompatibility sealed 类）；
  - `AccountSession`：当前账号的活 repository + httpClient 生命周期、
    `getSyncRepository(accountKey)` 按 key 现场重建（Worker 路径）；
  - `AccountExportService`：本地账号 ZIP 导出。
  门面方法 100% 保持原语义（切换/新增/删除账号顺序：持久化写入 → session 重建 → purge）；
  mutex 顺序 门面 → session，`AccountSession.refresh` 不 await 初始化（防死锁）。

### 建立最终 MemoRepository contract（Phase 10 附加项）
- **Done**（commit 55f25e43）`data/repository/MemoRepository` 接口：全 core.model
  出参（Memo/Attachment/MemoVisibility），禁止 entity/旧 data.model 泄漏；
  `observeTimeline()` / `getMemo` / `createMemo` / `updateMemo` / `createAttachment` /
  `deleteAttachment` 等。`MemoRepositoryImpl : AbstractMemoRepository(), MemoRepository`
  双实现共存（同名签名一处 override；erasure 冲突处改名 listArchived/createAttachment/
  cacheAttachmentFile）。新 ViewModel（Timeline/Editor）只依赖该契约。
  说明：entity 路径 createMemo 的 tags 形参在实现中被忽略（标签只在 content 中），
  domain 契约省略该参，行为等价。

### Phase 11 — Timeline Route/ViewModel/Screen
- **Done**（commit b1a6d936 域迁移 + c1e60036 拆分）：
  - `feature/timeline/`：TimelineUiState（memos/tags/sortOrder/selection/batch/
    syncStatus/syncAlert）、TimelineViewModel（StateFlow + collectAsStateWithLifecycle，
    经 `MemoRepository.observeTimeline()` 取 domain 数据；reapply guard 与
    手动批量操作后状态修补保持原语义；sortOrder 持久化到 SavedStateHandle）、
    TimelineScreen（Scaffold/选择栏/对话框，纯 state + callback）、TimelineRoute
    （hiltViewModel 接线 + 编辑器 bottom sheet + IME 机制原样保留）。
  - MemoSortOrder / orderMemosForTimeline / memoMatchesDate 移入 feature.timeline；
    MemosCard / MemosList 全面转 core.model.Memo（DomainRepresentable 桥接旧组件）。
  - `MemosHomePage.kt`（1035 行）删除；MemosNavigation 的 MEMOS 路由指向 TimelineRoute。
  - 过渡期：Search/Tag/Date/Stats/Detail 等 legacy 页面仍经 LocalMemos(MemosViewModel)
    `.domainMemos` 取数，Phase 16 收口。

### Phase 12 — Editor Route/ViewModel/Screen
- **Done**（commit ca3dce56 组件解耦 + 4de19454 迁移）：
  - `feature/editor/`：EditorUiState（text/visibility/attachments/tags + 派生
    canSubmit/hasUnsavedChanges）、EditorViewModel（唯一编辑核心：草稿 DataStore
    语义、tag-only 草稿丢弃、SavedStateHandle 文本进程恢复、附件上传/删除、
    提交走 domain 契约本地事务 → Submitted 事件，从不等待 HTTP）、
    EditorScreen（FullScreen/BottomSheet 双呈现）、EditorRoute（统一入口）。
  - 四个入口全部切换：Navigation INPUT/SHARE/EDIT、TimelineRoute bottom sheet、
    QuickMemoActivity（Quick Settings Tile）、QuickMemoLaunchPage（冷启动）。
  - 编辑器组件与 ResourceEntity/MemoInputViewModel 解耦（MemoInputEditor 收
    domain Attachment + 删除回调；InputImage 收 ResourceRepresentable）。
  - `MemoInputPage.kt` / `MemoInputViewModel.kt` 删除。
  - hashtag 自动补全（本地持续 + 远端 listTags 一次合并）、可见性、附件、
    保存确认对话框、焦点/键盘、quick activity finish 行为保持。

### Phase 13 — Tag Parser + Tag Index
- **Done**（3 commits：b3057797 / f0df3eb8 / f99ea7f2）：
  - `core/tag/MemosTagParser` 统一全 App 标签语义，按最新 Memos ADR 0001
    "Tag Syntax and Recognition"（XID_Continue + `-`/`+`/`&` 扩展、apostrophe
    joiner、slash 层级隐式祖先、RGI emoji、无左边界）；raw 全文扫描 +
    introducer 位置 AST 校验，抵御 lexer token 切分（如 `#tag's`）。
  - `memo_tags` Room 表（accountKey/memoId/tag 复合主键 + (accountKey, tag) 索引），
    schema v3 + `MIGRATION_2_3`（回填解析结果）+ androidTest MigrationTest；
    所有写路径维护索引（upsert/替换/本地创建/删除/归档）。
  - TagDao：frequency DESC + tag ASC 聚合、`ESCAPE '\'` 前缀匹配、IN 子查询精确匹配。
  - Editor autocomplete 改走 Room 索引（EditorViewModel 订阅 observeTagsFlow）。

### Phase 14 — Room Search
- **Done**（commit 57a67e98）：
  - `feature/search/`：SearchRoute/ViewModel/UiState/Screen；`observeSearch`
    `LIKE '%' || :q || '%'`（CJK 友好）+ archived/tag/date 过滤 + `ESCAPE '\'`。
  - MemosList 内存 `content.contains` / tag contains 过滤移除；不用 FTS。

### Phase 15 — Stats Data Layer
- **Done**（commit 47f89652）：`feature/stats/` StatsRoute/ViewModel/UiState/Screen 复用
  `MemoStats.calculateMemoStats`（泛化为 `List<MemoStatsInput>`），数据来自
  Repository/Room Flow；不建统计表；视觉与交互保持。

### Phase 16 — 减少业务 CompositionLocal
- **Done**（commit b348b183）：
  - 删除 LocalMemos / LocalUserState / LocalRootNavController / LocalArchivedMemos
    与 MemosViewModel / UserStateViewModel（ManualSyncResult 移入 feature/timeline）。
  - 卡片交互收进 `MemoCardActions`（open/edit/pin/archive/delete/updateContent/
    cacheResource/downloadAndCache）由宿主页面提供；MemosCard/MemosList/
    MemoContent/MemoImage/Attachment 纯组件化；卡片点击恢复跳 MEMO_DETAIL
    （参数化过渡期曾丢失）。
  - 新 VM：DrawerViewModel（抽屉统计/热力图/标签走 Room 流）、DateMemoViewModel、
    MemoDetailViewModel（SavedStateHandle memoId + 活 memo 流）、QuickMemoViewModel、
    AccountSessionViewModel（账户会话门面）+ 共享 `data/service/MemoActions`
    （单 memo 写操作统一委托 MemoRepository）。
  - Login 组装迁入 AccountService；EditorRoute 以 navController 参数取代
    CompositionLocal；MainActivity/QuickMemoActivity 不再提供 VM。
  - 验证：129 单测全绿，assembleDebug + assembleRelease 通过。

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
- Phase 12 起 Timeline/Editor 的列表刷新完全依赖 Room flow（旧实现提交后还有一次
  手动 refreshLocalSnapshot 兜底）。reapply guard 在 sync 进行中会跳过快照重放，
  理论上极端时序下（WorkManager 在 Room invalidation 送达前把 syncing 置真）新建
  memo 要等本次同步结束才显示；实测路径下 invalidation 先到，真机 smoke 时留意。
- Timeline/Editor 新架构需真机回归：时间线排序/多选/批量打标签删除/下拉刷新/
  版本确认弹窗、编辑器草稿、分享入口、Quick Settings 冷启动捕获。
