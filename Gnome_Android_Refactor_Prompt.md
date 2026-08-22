# Gnome Android 客户端完整重构开发任务

你正在修改我的 Android 项目：

- GitHub 仓库：`eonewg/Gnome`
- 默认开发分支：`custom-memos`
- 项目名称：**Gnome**
- 当前本地旧版 / 现有工作目录：`D:\\dev\\code\\application\\Gnome`
- 所有审计、构建、测试和渐进式重构默认都应基于这个现有目录进行。
- 不要另建新的 Android 项目目录，不要复制一份新工程后脱离原仓库开发；除非为了临时实验，且实验结果最终必须合并回该仓库。
- Gnome 最初基于 `mudkipme/MoeMemosAndroid` 开发，但现在目标是逐渐演化成一个拥有独立架构、独立品牌、长期维护的 Memos Android 客户端。
- 不要把本任务理解为“继续给 Moe Memos 打补丁”。
- 也不要重新创建一个空 Android 项目从零重写。
- 应在当前 Gnome 已经可工作的基础上进行 **渐进式、可验证、可回滚的架构重构**。

---

# 一、开始前必须做的事情

不要看到本提示词后立即修改代码。

首先完整检查当前仓库 HEAD，包括但不限于：

- `AGENTS.md`
- `README.md`
- `README.zh-CN.md`
- `moe_memos_android_custom_prompt.md`
- `app/build.gradle`
- 根目录 `build.gradle`
- `AndroidManifest.xml`
- Room schema
- 所有 DAO / Entity
- `SyncingRepository`
- `LocalDatabaseRepository`
- `MemosV0Repository`
- `MemosV1Repository`
- `AccountService`
- `MemoService`
- `MemosViewModel`
- `MemoInputViewModel`
- `UserStateViewModel`
- Navigation
- Timeline/Home
- Editor
- Search
- Tags
- Stats
- Quick Memo
- Quick Settings Tile
- Widgets
- Tests
- Release / signing workflow

同时检查最新 commit，不允许以本提示词生成时的旧代码状态作为唯一依据。

必须以 **实际当前 HEAD** 为准。

如果仓库已经完成本提示词中的某些任务，不要重新实现，直接检查其质量并继续下一阶段。


## 本地工作目录约束

当前现有 Gnome 工程位于：

```text
D:\dev\code\application\moe-memos
```

开始任务时，首先确认当前 shell / workspace 是否位于该目录。

在 Windows PowerShell 环境下，可先检查：

```powershell
Get-Location
git status
git remote -v
git branch --show-current
git log -1 --oneline
```

预期仓库应为 `eonewg/Gnome`，开发分支以仓库当前实际状态为准，默认目标分支为 `custom-memos`。

整个重构必须直接作用于这个现有 Git 仓库。

禁止把“独立重构 Gnome”错误理解为：

```text
D:\dev\code\application\Gnome-New
```

然后从空项目重写。

正确方式是：

```text
D:\dev\code\application\moe-memos
        ↓
在现有 Git 历史中渐进重构
        ↓
最终演化为独立 Gnome 架构
```

目录名 `moe-memos` 本身只是本地文件夹名称，不要求在架构重构第一阶段强制改名；是否最终把本地目录重命名为 `Gnome` 属于开发环境整理，不应影响 Git 历史、Android applicationId、namespace 或 package 重构。

---

# 二、联网验证要求

本项目需要长期跟随最新版 Memos 和 Android。

遇到以下内容时必须主动查阅最新官方资料，而不是仅依赖模型记忆：

1. Memos 最新稳定版
2. Memos `/api/v1` 当前接口
3. Memos tag 解析语义
4. Memos attachment / memo / user API
5. Memos breaking changes
6. Android Room
7. WorkManager
8. Compose Architecture
9. Navigation
10. Android 16 / API 36 行为
11. Hilt
12. Kotlin / Compose / AndroidX 已使用版本的兼容性

优先官方来源：

- `github.com/usememos/memos`
- Memos 官方文档
- `developer.android.com`
- AndroidX release notes
- Kotlin / Square / Google 官方文档

不要为了“追最新”盲目升级依赖。

**架构重构与依赖升级分离。**

除非现有依赖存在明确 Bug、安全问题或是完成任务的必要条件，否则不要在重构过程中大规模升级库。

---

# 三、最终产品定位

Gnome 的产品定位是：

> 一个以 Memos 作为远程同步后端、以 Android 本地数据库作为主要运行数据源的 local-first / offline-first Android memo 客户端。

核心体验：

- 类似 flomo 的低摩擦记录
- App 打开立即可用
- 没网也可以记录
- 网络慢也不影响记录
- 保存 Memo 不需要等待 Memos Server
- 网络恢复后自动同步
- `#` 标签快速补全
- Quick Settings Tile 快速进入速记
- 正常 App 中可以浏览、搜索、编辑、删除、归档、置顶、查看标签和统计
- 支持附件
- 支持 Memos 当前稳定版
- 尽可能兼容合理范围内的旧 Memos
- 保持用户数据可靠性优先于一切

---

# 四、重构最高原则

## 1. Room 是唯一主要 Source of Truth

正常 Timeline / Search / Tags / Stats / Detail 页面：

不得把远程 API 作为 UI 的直接数据源。

正确数据流：

```text
Compose UI
    ↓
ViewModel
    ↓
Repository
    ↓
Room
```

远程 Memos 的职责是：

```text
Room
 ↕
SyncEngine
 ↕
Memos Server
```

读取逻辑：

```text
打开页面
↓
立即读取 Room
↓
立即显示

后台同步
↓
更新 Room
↓
Flow 自动更新 UI
```

禁止恢复成：

```text
打开页面
↓
请求服务器
↓
等待服务器
↓
显示数据
```

## 2. 所有关键写入先本地成功

创建 Memo：

```text
用户点击发布
↓
Room transaction
↓
Memo 本地写入成功
↓
Outbox 写入同步任务
↓
UI 立即认为保存成功
↓
后台 WorkManager 同步
```

编辑、删除、归档、置顶同理。

对于速记：

> 本地数据库写入成功就是用户操作成功。

服务器同步属于第二阶段。

## 3. 不允许用普通 Coroutine 代替可靠同步队列

当前如果仍存在类似：

```kotlin
CoroutineScope(SupervisorJob() + Dispatchers.IO)
```

然后：

```kotlin
scope.launch {
    pushMemo(...)
}
```

作为关键同步机制，必须逐步移除。

原因：

App 被系统杀死后 Coroutine 不具有持久性。

关键后台同步统一交给：

```text
Room Outbox
+
WorkManager
+
SyncWorker
+
SyncEngine
```

普通 Coroutine 只用于：

- UI 生命周期内操作
- 非关键临时任务
- 当前进程中的即时处理

---

# 五、Gnome 品牌身份迁移

Gnome 将作为独立客户端长期维护。

不需要继续保留 Moe Memos 的代码 namespace / applicationId。

目标统一为：

```text
applicationId = io.github.eonewg.gnome
namespace     = io.github.eonewg.gnome
package       = io.github.eonewg.gnome
```

完成完整源码 package 迁移：

```text
me.mudkip.moememos
↓
io.github.eonewg.gnome
```

同时逐步重命名：

```text
MoeMemosApp
→ GnomeApp

MoeMemosDatabase
→ GnomeDatabase

MoeMemosTheme
→ GnomeTheme

MoeMemosDesign
→ GnomeDesign

MoeMemosTokens
→ GnomeTokens

MoeMemosFileProvider
→ GnomeFileProvider

MoeMemosGlanceWidget
→ GnomeGlanceWidget
```

以及其他实际属于 Gnome 的 MoeMemos 命名。

Manifest 中不要硬编码包名。

例如：

```xml
android:authorities="${applicationId}.fileprovider"
```

Quick Memo task affinity 类似：

```xml
android:taskAffinity="${applicationId}.quickcapture"
```

检查：

- Provider
- Quick Settings Tile
- Shortcut
- Widget
- Activity
- Service
- Receiver
- Deep Link

全部是否仍存在旧包名。

---

# 六、关于旧 applicationId 数据

此次允许：

```text
me.mudkip.moememos
→
io.github.eonewg.gnome
```

因为当前 Gnome 尚未作为应用市场正式产品建立必须维持的升级链。

因此可以把新版视为新的 Gnome App 身份。

但是：

绝对不能因为修改 applicationId 而忽视数据安全。

实施前必须：

1. 明确哪些数据只存在于手机本地
2. 明确哪些已经同步至 Memos
3. 不允许静默丢失本地未同步 Memo
4. 在开发/测试阶段提供明确备份方案
5. 检查 DataStore / Token / Local attachment 的影响

如果现阶段决定 fresh install + Memos 重新同步是正式迁移方案，应在 README / Release Notes 中明确说明。

新的数据库文件建议直接使用清晰的 Gnome 名称，例如：

```text
gnome.db
```

不要继续使用 Moe Memos 品牌数据库名称。

---

# 七、目标源码架构

暂时保持单一 Gradle `:app` module。

不要为了“Clean Architecture”过度拆成十几个 module。

使用 package 建立清晰边界即可。

目标：

```text
io.github.eonewg.gnome
│
├── app
│   ├── GnomeApp
│   ├── MainActivity
│   └── navigation
│
├── core
│   ├── model
│   ├── common
│   ├── files
│   └── design
│
├── data
│   ├── local
│   │   ├── GnomeDatabase
│   │   ├── memo
│   │   ├── attachment
│   │   ├── tag
│   │   └── sync
│   │
│   ├── remote
│   │   └── memos
│   │       ├── common
│   │       ├── v0
│   │       ├── v1
│   │       └── MemosClientFactory
│   │
│   ├── account
│   └── repository
│
├── sync
│   ├── SyncEngine
│   ├── SyncScheduler
│   ├── SyncWorker
│   ├── ConflictResolver
│   └── SyncStatusRepository
│
├── feature
│   ├── timeline
│   ├── editor
│   ├── detail
│   ├── search
│   ├── tags
│   ├── stats
│   ├── account
│   └── settings
│
├── quickcapture
└── widget
```

不要求机械照搬目录名字。

重要的是依赖方向正确。

---

# 八、建立独立 Domain Model

Room Entity 不得继续直接充当 UI / Domain Model。

建立：

```text
Domain
Memo
Attachment
User
Account
SyncState
MemoVisibility
```

例如：

```kotlin
data class Memo(
    val id: String,
    val remoteId: String?,
    val content: String,
    val createdAt: Instant,
    val updatedAt: Instant,
    val visibility: MemoVisibility,
    val pinned: Boolean,
    val archived: Boolean,
    val attachments: List<Attachment>,
    val syncState: SyncState,
)
```

明确分离：

```text
Network DTO
    ↓ Mapper
Domain Model
    ↑ Mapper
Room Entity
```

禁止：

```text
Compose
→ MemoEntity

ViewModel
→ MemoEntity
```

最终 UI 不应该知道 Room schema。

---

# 九、重构 Repository

当前如果仍有：

```text
AbstractMemoRepository
LocalDatabaseRepository
MemosV0Repository
MemosV1Repository
SyncingRepository
```

不要一次全部删除。

渐进式替换。

最终应该是：

```text
MemoRepository
```

作为 App 统一访问入口。

例如：

```kotlin
interface MemoRepository {

    fun observeTimeline(...): Flow<List<Memo>>

    fun observeMemo(id: String): Flow<Memo?>

    suspend fun createMemo(...)
    suspend fun updateMemo(...)
    suspend fun deleteMemo(...)
    suspend fun archiveMemo(...)
    suspend fun restoreMemo(...)
    suspend fun setPinned(...)
}
```

实现：

```text
MemoRepositoryImpl
↓
LocalMemoDataSource
↓
Room
```

Repository 写完本地后：

```text
SyncScheduler.schedule(account)
```

UI 不需要知道：

- MemosV0
- MemosV1
- LocalRepository
- SyncingRepository

---

# 十、拆分 Local / Remote DataSource

## LocalMemoDataSource

只负责：

```text
Room
DAO
Transaction
Local FileStorage
```

不知道 Retrofit。

例如：

```text
observeMemos
getMemo
createMemo
updateMemo
softDeleteMemo
getDirtyMemos
upsertRemoteSnapshot
```

涉及 Memo + Resource + Tag 的写操作应尽量使用：

```kotlin
database.withTransaction { ... }
```

避免一半成功、一半失败。

## RemoteMemoDataSource

只负责：

```text
Memos HTTP API
DTO
API serialization
API compatibility
```

不知道 Room。

当前：

```text
MemosV0Repository
MemosV1Repository
```

应该逐渐演化成：

```text
MemosV0RemoteDataSource
MemosV1RemoteDataSource
```

现有已经验证的：

- pagination
- updateMask
- attachments
- createTime
- updateTime
- resource name parsing
- user stats
- auth

优先复用。

不要为了改名字重写已经工作的网络实现。

---

# 十一、Outbox 持久同步模型

新增 Room 表：

```text
sync_operations
```

建议模型：

```kotlin
@Entity(...)
data class SyncOperationEntity(
    val id: String,
    val accountKey: String,
    val entityType: SyncEntityType,
    val entityId: String,
    val operation: SyncOperationType,
    val createdAt: Instant,
    val attemptCount: Int,
    val lastAttemptAt: Instant?,
    val lastError: String?,
)
```

操作至少支持：

```text
UPSERT_MEMO
DELETE_MEMO

UPLOAD_ATTACHMENT
DELETE_ATTACHMENT
```

如果当前实现需要其他操作，再扩展。

关键原则：

> 一切必须在进程死亡后仍然知道“下一步应该同步什么”。

尤其要解决：

```text
附件本地删除
↓
App 被杀
↓
服务器附件尚未删除
```

这种目前仅靠内存队列容易遗失的操作。

---

# 十二、SyncEngine

从旧 `SyncingRepository` 中提取真正的同步算法。

目标：

```text
SyncEngine
├── pullRemote()
├── applyRemoteChanges()
├── resolveConflicts()
├── processOutbox()
└── reconcile()
```

不要让 SyncEngine：

- 管 UI
- 管 Compose state
- 管 Navigation
- 创建 ViewModel
- 直接弹 Snackbar

它只负责同步业务。

---

# 十三、ConflictResolver

把冲突算法独立出来。

当前 Gnome 已经采用：

```text
本地改变
+
服务器改变
↓
保留服务器版本
+
复制本地版本为新 Memo
```

如果当前代码仍然如此：

第一轮重构 **保留该语义**。

不要在架构重构过程中顺便改变冲突产品策略。

把规则提取成：

```text
ConflictResolver
```

并写完整单测。

重要目标：

> 永远不要为了自动解决冲突静默丢用户文字。

---

# 十四、WorkManager 同步

创建：

```text
SyncWorker
SyncScheduler
```

建议每个账号使用：

```text
unique work name:
gnome-sync:<accountKey>
```

避免同一账号出现多个同步 Worker 并发操作。

Constraints：

```text
NetworkType.CONNECTED
```

错误分类：

```text
Network
Timeout
Server5xx
Unauthorized
Forbidden
UnsupportedServer
BadRequest
MissingAttachment
Unknown
```

Retry 规则：

```text
Network / Timeout / 5xx
→ Result.retry()

401 / 403
→ 不无限 retry

明确客户端数据错误
→ 记录失败状态

成功
→ Result.success()
```

使用合理的 backoff。

用户手动点击“立即同步”仍然走统一 SyncScheduler / SyncEngine。

不要保留另一套手动同步算法。

---

# 十五、同步顺序

第一阶段不要急着设计超复杂增量同步。

为了保证正确性：

可以继续保留当前：

```text
拉取服务器完整 snapshot
↓
local/remote reconcile
↓
push local pending
```

行为。

等 SyncEngine 稳定、测试充分后再优化。

未来允许：

```text
foreground incremental refresh
+
periodic/full reconciliation
```

如果最新版 Memos 存在可靠 SSE / event stream：

它可以作为：

```text
“服务器可能发生变化”的通知机制
```

但不能代替：

```text
Room
Outbox
WorkManager
SyncEngine
```

作为数据可靠性基础。

---

# 十六、Tag Parser 必须统一

当前如果存在：

```kotlin
Regex("#([^\\s#]+)")
```

以及 Markdown AST 排除逻辑：

不要简单删掉。

首先查最新版 Memos 官方代码 / changelog，确认当前标签语义。

然后建立：

```text
MemosTagParser
```

整个 App 所有地方统一使用：

```text
Memo 保存时 tag index
# autocomplete
tag drawer
tag filter
stats
markdown tag rendering
```

禁止不同 feature 自己写不同 Regex。

至少建立测试：

```text
#数学
#408/计网
#中文标签
\#literal
`#code`
代码块中的 #tag
Markdown link 中 #tag
URL fragment
连续 #
Unicode
标点
换行
```

本地标签语义应尽量与当前 Memos Server 一致。

---

# 十七、Room Tag Index

不要每次需要标签时重新遍历全部 Memo 再解析正文。

新增：

```text
memo_tags
```

例如：

```text
accountKey
memoId
tag
```

Memo 正文变化时：

```text
database.withTransaction {
    updateMemo()
    deleteOldTags()
    insertNewTags()
}
```

这样直接支持：

```text
所有标签
标签使用次数
某标签 Memo
# autocomplete
最近标签
```

---

# 十八、搜索

禁止 Timeline 把所有 Memo 加载到内存后：

```kotlin
memo.content.contains(query)
```

作为长期搜索方案。

搜索应该进入 Room。

第一版可以：

```sql
LIKE
```

因为中文 memo substring 搜索很重要。

必要时再结合：

```text
Room FTS
```

不要为了“FTS 更高级”牺牲中文搜索体验。

搜索 Repository 可以暴露：

```kotlin
fun searchMemos(
    query: String,
    tag: String?,
    dateRange: ...
): Flow<List<Memo>>
```

搜索逻辑不应该放在 Composable。

---

# 十九、ViewModel / UDF

每个主要 feature 使用：

```text
Route
ViewModel
Screen
```

例如：

```text
TimelineRoute
TimelineViewModel
TimelineScreen
```

```text
EditorRoute
EditorViewModel
EditorScreen
```

ViewModel：

```text
StateFlow<UiState>
```

UI：

```text
collectAsStateWithLifecycle()
```

用户事件：

```text
onAction(...)
```

遵循 UDF。

例如：

```kotlin
data class TimelineUiState(
    val memos: List<Memo>,
    val selectedIds: Set<String>,
    val sort: MemoSort,
    val syncStatus: SyncStatus,
    val loading: Boolean,
)
```

禁止 Composable 自己承担大量：

- 数据操作
- 批量删除
- 批量改标签
- 同步逻辑
- Repository 调用

---

# 二十、MemosHomePage 重构

当前如果仍然是超大 `MemosHomePage.kt`：

拆成：

```text
TimelineRoute
TimelineScreen
TimelineTopBar
TimelineList
TimelineSelectionBar
TimelineDrawer
TimelineViewModel
```

不要为了拆文件而拆文件。

拆分边界以：

```text
状态责任
业务责任
UI 责任
```

为依据。

Home/Timeline 需要支持：

- Timeline
- selection
- batch operations
- search entry
- sync status
- drawer
- quick editor entry

但业务操作由 ViewModel 调用 Repository。

---

# 二十一、Editor 重构

当前 MemoInputPage / Components 已经承担太多责任。

目标：

```text
EditorRoute
EditorViewModel
EditorScreen
EditorTextField
EditorToolbar
TagAutocomplete
AttachmentPicker
VisibilityPicker
DraftManager
```

统一支持：

```text
普通新增 Memo
Quick Settings Tile
编辑 Memo
分享进入
Shortcut
```

不要存在两套真正的 Editor。

---

# 二十二、Quick Memo

保留 `QuickMemoActivity` 这种独立短生命周期入口是允许的。

它是 Android 系统入口，不强制为了“Single Activity”删除。

目标：

```text
MainActivity
    ↓
EditorRoute

QuickMemoActivity
    ↓
同一个 EditorRoute / EditorScreen
```

行为：

```text
点击 Quick Settings Tile
↓
迅速打开
↓
自动获得输入焦点
↓
键盘出现
↓
输入
↓
发布
↓
本地保存
↓
Activity finish
↓
返回之前 App
↓
后台同步
```

即使：

- 没网
- Memos Server 慢
- Cloudflare 暂时不可达

也应该能够完成记录。

---

# 二十三、# 标签补全

要求保持并优化当前体验：

输入：

```text
#
```

立即列出已有标签。

继续输入：

```text
#408
```

实时筛选。

排序可以逐渐变成：

```text
最近使用
+
使用频率
+
名称
```

Tag 数据来自本地数据库索引。

不要每次输入字符都遍历并 Markdown parse 全部 Memo。

---

# 二十四、Stats 重构

当前 Stats 功能应该保留。

不要推倒重写现有已经可工作的图表/统计 UI。

但是数据来源改成：

```text
StatsScreen
↓
StatsViewModel
↓
StatsRepository
↓
Room
```

不要：

```text
Stats Composable
↓
LocalMemos.current.memos
↓
扫描整个全局 snapshot
```

第一版可以继续复用现有纯 Kotlin：

```text
MemoStats
calculateMemoStats()
```

未来如果数据量 profiling 确实需要，再考虑 SQL 聚合。

现在不要提前创建大量冗余 daily/monthly statistics table。

---

# 二十五、AccountService 重构

当前如果 `AccountService` 同时负责：

```text
DataStore
账号
Token
Retrofit
OkHttp
Memos版本判断
Repository 创建
当前 Repository
数据清理
导出
```

必须拆分。

目标：

```text
AccountStore
TokenStore
AccountSession
MemosClientFactory
ServerCompatibilityChecker
AccountExportService
RemoteDataSourceFactory
```

ViewModel 不允许：

```text
AccountService.getRepository()
```

然后自己判断当前是什么 Repository。

---

# 二十六、Memos 版本兼容

不要让 Memos 版本差异泄漏到 UI。

正确边界：

```text
feature/*
↓
Domain / Repository
↓
RemoteDataSource
↓
MemosV0 / MemosV1
```

如果当前稳定版为 Memos 0.30.x 或更新：

必须以开发时最新稳定版重新验证。

建议逐渐建立：

```kotlin
data class ServerCapabilities(...)
```

例如：

```text
apiV1
attachments
updateMask
userStats
sse
...
```

不要永远完全依赖：

```text
if version <= X
```

未知更高版本：

不要因为版本号稍高就必然拒绝。

可以：

```text
未验证
↓
检查能力
↓
尝试兼容
↓
必要时给用户明确提示
```

已知 breaking change 除外。

---

# 二十七、Navigation

Navigation 不要作为第一阶段任务。

首先完成：

```text
Data
Sync
Repository
ViewModel
```

稳定后，再评估最新版 Android 官方 Navigation 推荐。

如果 Navigation 3 已稳定且适合当前项目：

可迁移为 typed navigation key。

例如：

```text
TimelineKey
EditorKey
MemoDetailKey(id)
TagKey(tag)
DateKey(date)
StatsKey
SettingsKey
```

逐步消灭：

```text
"memos/date/$date"
```

这种手工字符串拼接。

但 Navigation 迁移必须单独提交。

不要和 Sync 重构混在一个 commit。

---

# 二十八、CompositionLocal

逐渐减少业务型：

```text
LocalMemos
LocalUserState
LocalRootNavController
```

不要让任意 Composable 随时获取整个全局业务状态。

CompositionLocal 主要保留：

```text
Theme
Design tokens
必要 Android UI context
```

业务数据通过：

```text
UiState
parameter
callback
```

传递。

---

# 二十九、Room Migration

任何数据库 schema 修改都必须：

```text
明确 version
Migration
Schema export
Migration test
```

禁止：

```text
fallbackToDestructiveMigration()
```

用于正式用户数据。

必须测试：

```text
旧数据库
↓
升级
↓
Memo 仍在
↓
Resource 仍在
↓
Tag 正确
↓
pending sync 状态仍在
```

可以使用 Room 官方 migration testing 工具。

---

# 三十、数据库事务

以下操作尽量 transaction：

```text
Memo + attachments
Memo + tags
Memo + outbox
Remote snapshot merge
Delete memo + outbox
Archive + outbox
```

例如：

```text
create memo
+
insert tags
+
insert sync operation
```

必须是原子逻辑。

不能出现：

```text
Memo 保存成功
↓
Outbox 没写进去
```

否则这个 Memo 可能永远不会同步。

---

# 三十一、附件设计

附件需要同时考虑：

```text
localId
remoteId
localUri
remoteUrl
mimeType
filename
size
memoId
syncState
```

本地创建附件后：

```text
先保留本地文件
↓
Outbox: UPLOAD_ATTACHMENT
↓
上传成功
↓
记录 remoteId
↓
Memo 同步引用 remote attachment
```

不要假设附件永远已经上传。

需要测试：

```text
附件上传到一半断网
App 被杀
网络恢复
重新同步
```

---

# 三十二、同步状态 UI

保持当前已经增加的：

```text
Syncing
Unsynced
Synced
Error
```

体验。

但 UI 不要读取 SyncingRepository 内部字段。

改成：

```text
SyncStatusRepository
↓
Flow<SyncStatus>
↓
TimelineViewModel
↓
UI
```

例如：

```kotlin
sealed interface SyncStatus {
    data object Idle
    data object Syncing
    data class Pending(val count: Int)
    data class Failed(val pendingCount: Int, val reason: ...)
}
```

“刚刚同步完成”这种 2 秒 UI 动画可以继续留在 UI 层，因为它属于展示状态。

---

# 三十三、错误模型

逐渐停止使用：

```text
Exception("Memo not found")
字符串错误
```

定义 Domain Error。

例如：

```text
GnomeError
├── Network
├── Authentication
├── PermissionDenied
├── UnsupportedServer
├── NotFound
├── Validation
├── File
├── Database
└── Unknown
```

不要过度设计。

重点是：

UI 能知道：

```text
应该重试？
应该重新登录？
应该告诉用户？
应该静默？
```

---

# 三十四、测试要求

重构过程中单元测试优先级非常高。

必须逐步覆盖：

## Sync

```text
create offline
edit offline
delete offline
archive offline
restore offline
pin offline
process restart
retry
remote newer
local newer
both changed
remote deleted
local deleted
attachment failure
```

## Tag

```text
Memos-compatible parser
autocomplete
tag index
```

## Repository

```text
local-first
transaction
outbox
```

## Room Migration

```text
old → new
```

## Stats

继续保留现有测试。

## API

保留并扩展：

```text
resource name
pagination
updateMask
conversion
```

---

# 三十五、不要过度重构

明确禁止：

1. 不要重新创建空项目。
2. 不要为了 Clean Architecture 新建十几个 Gradle module。
3. 不要为了“现代化”把所有依赖一起升级。
4. 不要一次重写所有 UI。
5. 不要改变已经正常工作的产品行为而没有理由。
6. 不要删除现有功能。
7. 不要偷偷改变冲突策略。
8. 不要 destructive migration。
9. 不要让大重构导致数天无法编译。
10. 不要留下 TODO 作为核心实现。
11. 不要只写架构壳子而没有真正接入现有功能。
12. 不要复制新旧两套永久共存。

重构原则：

```text
建立新路径
↓
迁移一个 feature
↓
测试
↓
删除旧路径
```

而不是：

```text
先复制整个系统
↓
两套逻辑长期并存
```

---

# 三十六、提交粒度

整个重构必须分阶段。

建议：

```text
Phase 0
测试护栏

Phase 1
Gnome applicationId / namespace / package / branding

Phase 2
Domain Model

Phase 3
Local DataSource

Phase 4
Remote DataSource

Phase 5
MemoRepository

Phase 6
Room Outbox + Migration

Phase 7
SyncEngine + ConflictResolver

Phase 8
WorkManager Sync

Phase 9
移除 SyncingRepository

Phase 10
拆 AccountService

Phase 11
Timeline Route/ViewModel/Screen

Phase 12
Editor Route/ViewModel/Screen

Phase 13
Tag Parser + Tag Index

Phase 14
Room Search

Phase 15
Stats Data Layer

Phase 16
减少业务 CompositionLocal

Phase 17
Navigation 重构

Phase 18
Widget / Quick Capture 架构清理

Phase 19
性能 / Baseline Profile / 回归

Phase 20
删除 Moe Memos 遗留命名和死代码
```

实际 HEAD 如果已经完成部分阶段，可以调整。

每个阶段：

```text
修改
↓
format
↓
compile
↓
unit tests
↓
必要 instrumentation tests
↓
git diff review
↓
再进入下一阶段
```

---

# 三十七、每个阶段都必须可运行

不允许出现：

```text
“等全部重构完才会重新编译”
```

每个阶段都应该尽可能：

```bash
./gradlew :app:testDebugUnitTest
./gradlew :app:assembleDebug
```

通过。

如果当前 Windows 环境使用：

```text
gradlew.bat
```

则使用对应命令。

遵守仓库 AGENTS.md 中的构建约定。

---

# 三十八、完成后的验收条件

只有下面这些基本满足后，才能称为“Gnome 架构重构完成”。

```text
[ ] applicationId = io.github.eonewg.gnome

[ ] namespace = io.github.eonewg.gnome

[ ] Kotlin package 不再使用 me.mudkip.moememos

[ ] 核心 MoeMemos* 类完成 Gnome 命名

[ ] Room Entity 不出现在 Compose UI

[ ] UI 使用 Gnome Domain Model

[ ] Room 是 Timeline 的唯一主要读数据源

[ ] Search 读取 Room

[ ] Tags 读取 Room index

[ ] Stats 通过独立数据层读取数据

[ ] 创建 Memo 先本地成功

[ ] 编辑 Memo 先本地成功

[ ] 删除 Memo 有持久同步状态

[ ] attachment 删除不会因进程被杀而丢远程删除任务

[ ] 存在真正持久 Outbox

[ ] WorkManager 接管关键同步

[ ] App 被杀后 pending operation 可以恢复

[ ] SyncingRepository 已删除或退化为无需存在的旧兼容层后删除

[ ] MemosV0/V1 差异只存在 data.remote

[ ] AccountService 巨型职责已拆分

[ ] Timeline 有独立 ViewModel + UiState

[ ] Editor 有独立 ViewModel + UiState

[ ] Stats 有独立 ViewModel

[ ] 不再通过 LocalMemos 暴露整个全局 Memo List

[ ] tag parser 与当前 Memos 语义对齐

[ ] Quick Settings Tile 离线可正常记录

[ ] Quick Memo 与普通 Memo 使用同一 Editor 核心

[ ] 所有 Room schema 修改存在 migration test

[ ] Memos 当前最新稳定版完成 smoke test

[ ] 文本 Memo CRUD 正常

[ ] attachment CRUD 正常

[ ] archive / restore 正常

[ ] pin 正常

[ ] tag 正常

[ ] search 正常

[ ] stats 正常

[ ] widgets 正常

[ ] share intent 正常

[ ] Quick Settings Tile 正常

[ ] Release build 正常

[ ] Debug build 正常

[ ] Unit tests 正常
```

---

# 三十九、性能原则

Gnome 是速记 App。

优先指标不是理论上的完美架构，而是：

```text
启动快
输入快
发布快
滚动流畅
离线可靠
同步可靠
搜索快
```

不要：

```text
点击发布
↓
等待 HTTP
```

不要：

```text
输入 #
↓
重新扫描全部 Memo
```

不要：

```text
Stats recomposition
↓
重复解析全部 Markdown
```

不要：

```text
Timeline recomposition
↓
复杂业务重新执行
```

实际性能问题需要 profiling 后再优化。

---

# 四十、产品行为优先级

遇到设计取舍时按以下优先级：

```text
1. 不丢用户数据
2. 离线记录可靠
3. Memos 同步正确
4. 快速记录体验
5. 代码可维护性
6. UI 一致性
7. 性能优化
8. 理论架构纯度
```

如果“更漂亮的架构”会增加数据丢失风险：

不要做。

---

# 四十一、GPL 和 Moe Memos 来源

Gnome 虽然要成为独立品牌和独立架构，但项目源自 Moe Memos GPLv3。

保持：

```text
LICENSE
GPLv3
必要 attribution
README 中对 Moe Memos 的致谢
```

不要为了“独立品牌”删除合法来源声明。

代码中不需要继续使用 Moe Memos 命名表示来源。

品牌独立和 GPL 来源致谢是两件不同的事情。

---

# 四十二、实施方式

不要只输出一份计划然后停止。

你的工作方式应该是：

1. 审计当前 HEAD。
2. 对照本提示词判断哪些已经实现。
3. 给出简短的当前架构审计结果。
4. 建立具体执行 checklist。
5. 从 Phase 0 开始实际修改代码。
6. 每完成一个逻辑阶段就运行测试/构建。
7. 出现现有行为不明确时优先阅读代码和测试。
8. 只有代码无法回答的问题才需要询问用户。
9. 不要因为任务很大而停在“建议阶段”。
10. 如果一次上下文无法完成全部任务，在仓库中维护清晰的重构进度文档，使下一次 Agent 可以继续。

建议创建：

```text
docs/architecture.md
docs/refactor-plan.md
```

`architecture.md` 记录最终架构。

`refactor-plan.md` 记录：

```text
Done
In progress
Next
Deferred
Known risks
```

不要把临时 reasoning 或无价值日志写进去。

---

# 四十三、第一轮优先执行内容

不要第一轮就重构全部 UI。

优先完成：

```text
A. 当前 HEAD 审计

B. 测试护栏

C. Gnome identity migration

D. Domain Model

E. Local / Remote DataSource 边界

F. MemoRepository

G. sync_outbox

H. SyncEngine

I. WorkManager Sync

J. 核心同步测试
```

当这些已经稳定以后，再进入：

```text
Timeline
Editor
Search
Tags
Stats
Navigation
```

原因：

> 当前 Gnome 最重要的技术债不是 UI 长，而是数据、同步和 Repository 边界。

先把主梁重构正确，再重构表层。

---

# 四十四、最终目标

完成后，Gnome 的关系应该是：

```text
Moe Memos
    ↓
历史代码来源 / 参考 / GPL attribution

Memos
    ↓
远程同步协议和服务端

Gnome
    ↓
独立 Android 客户端
独立 package
独立 applicationId
独立数据模型
独立 Repository
独立 Sync Engine
独立 UI 架构
独立产品体验
```

而不再是：

```text
Moe Memos
+
大量 Gnome patch
```

目标是让未来开发者看到源码后，即使不知道 Moe Memos 的原始架构，也能够自然理解：

> 这是一个完整、独立设计的 Gnome Android 客户端，只是历史上从 Moe Memos 演化而来。

从现在开始按以上原则执行。
