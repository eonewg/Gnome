# Moe Memos Android 定制开发提示词

你现在正在修改一个已有的 Android 项目：**Moe Memos Android**。

目标不是重写客户端，而是在现有 Moe Memos 的基础上增加我需要的功能。请先完整阅读项目结构、README、现有 Compose 页面、编辑器、新建 Memo 流程、认证/API 层、标签相关实现，再决定修改方案。

**优先复用现有代码和架构，不要重复造轮子，不要为了实现需求大规模重构。**

---

## 一、最终目标

我要把 Moe Memos 作为日常使用的完整 Memos Android 客户端，同时获得类似 flomo 的“极低摩擦速记体验”。

最终必须同时满足：

1. 正常打开 App，可以浏览、查看、编辑、删除、搜索和管理已有 Memos。
2. 在 Android 任意界面下拉通知栏，通过快捷设置磁贴迅速进入新建 Memo 页面。
3. 在新建和编辑 Memo 时，输入 `#` 可以自动列出并补全已有标签。
4. **原生兼容开发时 Memos 官方最新稳定版**，不能要求降级服务端或额外部署兼容代理。

---

## 二、硬性要求：兼容最新版 Memos

不要只按照 Moe Memos Android 当前声明支持的旧版 Memos API 实现。

开发开始前，先检查：

1. Memos 官方 GitHub Releases
2. Memos 官方最新版 API Reference
3. Memos 官方源码中的 `proto/api/v1`
4. Moe Memos Android 当前 API / DTO / Repository 实现
5. Moe Memos Android 与新版 Memos 兼容性相关的 Issue / PR

然后以**开发时 Memos 官方最新稳定版**为主要兼容目标。

截至本需求提出时，目标版本至少应覆盖：

```text
Memos v0.30.0
```

但不要把 `0.30.0` 写死为永久目标。

如果实际开发时已经存在更新的正式稳定版，应优先兼容当时最新稳定版。

### 不允许通过兼容代理绕过

最终必须：

```text
Moe Memos Android
        ↓
直接访问
        ↓
最新版 Memos API
```

不要要求用户：

- 降级 Memos
- 部署 Mortis
- 部署 API 转换代理
- 运行额外兼容层
- 修改 Memos 服务端

需要真正修改 Moe Memos Android 的 API / DTO / Mapper / Repository，使其原生支持最新版 Memos。

如果当前项目保留旧版 API，可以继续保留，但最新版 Memos 必须成为一等支持目标。

### API 兼容性检查

不要认为“能够登录”就代表兼容完成。

至少检查：

#### 认证

确认最新版 Memos 官方推荐的认证方式。

重点验证 Personal Access Token：

```text
Authorization: Bearer <token>
```

要求：

- 可以连接自建 Memos
- Server URL 正确处理
- PAT 正确保存和注入请求
- 401 / 403 不崩溃
- Token 失效有合理错误反馈
- 不依赖浏览器 Session Cookie
- 不降低 Token 存储安全性

#### API Base URL

以最新版官方定义为准检查：

```text
/api/v1
```

不要继续调用已经删除或语义变化的旧接口。

#### Resource Name

重点检查新版 API 是否使用：

```text
memos/{memo}
users/{user}
attachments/{attachment}
```

等 resource name。

不要默认所有 ID 都是 `Int` / `Long`。

全面检查：

- Memo ID
- User ID
- Creator
- Attachment
- Relation
- Reaction
- Comment
- Tag

不要在代码各处使用脆弱的：

```text
split("/")
substring()
toInt()
```

解析 resource name。

如需解析，封装为统一的数据层逻辑。

#### Memo CRUD

必须真实验证最新版：

- 创建 Memo
- 获取 Memo 列表
- 获取单个 Memo
- 编辑 Memo
- 删除 Memo

重点检查 PATCH、update mask、resource name 等新版机制。

不能出现：

```text
新建正常，但编辑失败
```

#### 分页

检查最新版是否使用：

```text
pageSize
pageToken
nextPageToken
```

不要继续假设一定是：

```text
offset
limit
```

验证：

- 首次加载
- 加载更多
- 下拉刷新
- 大量 Memo
- 搜索分页

#### Filter / Search

检查最新版 `filter` / 搜索接口。

确保：

- 时间线
- 搜索
- 用户 Memo
- 标签筛选
- 可见性筛选

均正常。

#### Visibility

至少正确支持：

```text
PRIVATE
PROTECTED
PUBLIC
```

如果最新版新增 enum，不要因为未知值导致崩溃。

#### Attachment

真实验证：

- 图片上传
- 文件上传
- Memo 附件关联
- 查看附件
- 编辑带附件 Memo

不能只保证纯文本 Memo 可用。

#### Relation / Comment / Reaction

如果 Moe Memos 当前已经支持这些功能，则检查最新版对应 API。

如某个非核心功能确实无法兼容，要明确说明，不要伪装成已支持。

#### 错误响应

API 层应：

- 正确处理 HTTP 状态码
- 尽可能解析服务端错误
- 网络异常不能导致 App 崩溃
- 未知字段不能导致整体 JSON 解析失败

### 版本能力隔离

如果不同 Memos 版本存在 API 差异，优先封装在：

```text
API
DataSource
Repository
Mapper
```

等数据层。

不要把大量：

```text
if (version >= ...)
```

散落到 Compose UI 中。

如果有必要，可设计：

```text
ServerCapabilities
```

或等价能力模型。

### 向后兼容优先级

在合理成本内保留现有旧版支持。

但优先级为：

```text
最新版 Memos 完整可用
>
旧版 Memos 兼容
```

不要为了维护很旧的 API 而牺牲最新版支持。

---

## 三、功能一：通知栏「速记」Quick Settings Tile

增加 Android `Quick Settings Tile`。

磁贴名称：

```text
速记
```

用户将它添加到 Android 快捷设置区域后：

### 单击磁贴

直接打开现有的**新建 Memo 编辑界面**。

要求：

- 不先打开首页
- 不经过额外确认页面
- 直接进入可输入状态
- 自动聚焦正文输入框
- 尽可能自动弹出软键盘
- 复用现有新建 Memo 编辑器
- 不另写第二套编辑器
- 正常支持现有标签、附件、可见性等能力
- 发布成功后正常结束当前速记流程
- 返回行为尽量回到用户之前正在使用的 App / 页面

目标：

```text
任意 App
↓
下拉通知栏
↓
点击「速记」
↓
新建 Memo 编辑页
↓
键盘已可输入
↓
输入
↓
发布
```

### Android 实现

请根据实际项目正确实现：

- `TileService`
- Manifest 注册
- `android.permission.BIND_QUICK_SETTINGS_TILE`
- exported 配置
- Android 各版本兼容
- Activity / Navigation / Intent / Deep Link 跳转
- App 冷启动、后台、前台三种状态

优先复用项目已有：

- Navigation
- Home Screen Quick Action
- Share Sheet
- 新建 Memo Intent / Deep Link

不要为了 Tile 再创建完整的第二套新建流程。

### 长按磁贴

遵循 Android Quick Settings 标准行为。

不要为了自定义长按而违反平台规范。

---

## 四、功能二：`#` 标签自动补全

这是核心功能。

在**新建 Memo 和编辑已有 Memo**的正文编辑器中，实现 hashtag autocomplete。

### 基础交互

用户输入：

```text
#
```

立即弹出当前 Memos 实例已有标签的候选列表。

例如：

```text
#考研
#408
#数学
#数据结构
#计算机网络
#想法
```

正文：

```text
今天复习了 #
```

显示全部候选。

继续输入：

```text
今天复习了 #数
```

实时过滤为类似：

```text
#数学
#数据结构
```

点击 `#数学` 后：

```text
今天复习了 #数学 
```

要求：

- 替换当前正在输入的 hashtag
- 自动补一个空格
- 保持输入框 focus
- 光标移动到插入标签之后
- 不跳到正文末尾

### 标签数据来源

不要写死。

优先寻找 Moe Memos / Memos API 已有的标签接口和数据源。

要求：

- 标签来自当前登录的 Memos 实例
- 包含用户已有标签
- 不要每输入一个字符都请求服务器
- 优先复用 Repository / ViewModel / cache
- 必要时进入编辑页时加载一次
- 离线时尽量使用本地缓存

如果当前项目没有独立标签 API，再考虑从本地 Memo 数据中提取。

但必须先检查最新版 Memos 是否已有正式标签接口。

### 标签匹配规则

必须根据**当前光标位置**识别当前正在编辑的 hashtag。

例如：

```text
今天学习了 #数| 学
```

`|` 为光标。

应识别当前 token：

```text
#数
```

不要简单找全文最后一个 `#`。

需要正确处理：

- 文本开头 `#`
- 空格后 `#`
- 换行后 `#`
- 中文上下文
- 英文上下文
- 正文中间编辑
- 多个标签
- 删除 `#`
- 删除部分标签
- 光标移动
- 已有标签修改

### 候选框

视觉风格保持 Moe Memos 原有 Compose 风格。

可根据项目选择：

- Dropdown
- Popup
- Surface + LazyColumn
- 现有项目组件

要求：

- 输入 `#` 立即显示
- 输入字符实时过滤
- 删除 `#` 后消失
- 光标离开 hashtag 后消失
- 点击候选后正确替换当前 token
- 不丢失正文内容
- 不破坏滚动和焦点
- 长标签列表应可滚动

### 新标签

候选只是辅助，不是白名单。

即使没有：

```text
#新想法
```

用户仍然可以直接输入并发布。

发布成功后，该标签后续应能够进入候选数据源。

### 嵌套标签

如果最新版 Memos 支持：

```text
#408/计网
#project/android
```

应尽量完整支持。

不要只保留最后一级。

### 排序

如果已有数据允许，优先：

1. 最近使用
2. 使用频率
3. 其他

如果无法低成本实现，不要引入复杂统计系统。

优先保证：

- 数据正确
- 匹配正确
- 插入正确

---

## 五、保留完整 Moe Memos 客户端

不要把项目改成单纯速记工具。

必须保留项目已有完整功能，包括但不限于：

- Memo 时间线
- 查看 Memo
- 新建 Memo
- 编辑 Memo
- 删除 Memo
- 搜索
- 标签
- Markdown
- 图片 / 附件
- 登录和服务器配置
- 分享菜单
- 桌面小组件
- Home Screen Quick Action
- 其他已有能力

原则：

```text
新增功能，不破坏已有功能
```

---

## 六、修改前必须先分析项目

先阅读和定位：

- 项目模块结构
- README
- App Navigation
- MainActivity
- 新建 Memo 页面
- 编辑 Memo 页面
- 正文编辑 Compose Component
- Memo ViewModel
- Repository
- API Client
- DTO / Domain Model / Mapper
- 标签 Repository / API / UI
- Local Database / Cache
- AndroidManifest
- Home Screen Quick Action
- Share Sheet
- 登录 / Token 存储
- 版本检测

如果新建和编辑共用编辑器，就在那里实现标签补全。

如果目前是两个编辑器，优先抽取可复用的 autocomplete 逻辑，但不要借机做无关的大规模重构。

---

## 七、UI 原则

保持 Moe Memos 原有设计语言。

不要：

- 重做整个编辑页面
- 修改无关页面
- 引入明显不同的 UI 风格
- 添加复杂动画
- 为标签候选引入大型第三方 UI 库

速记入口的重点是：

```text
快
```

标签补全的重点是：

```text
准确 + 低摩擦
```

---

## 八、代码质量要求

- Kotlin / Compose 风格与当前项目一致
- 尽量使用现有依赖
- 不引入不必要依赖
- 不硬编码服务器 URL
- 不绕过现有认证层
- 不新增不安全 Token 存储
- 不复制完整编辑器
- 不使用 WebView 实现
- 不在 UI 层直接到处调用 HTTP API
- 不做与需求无关的大规模重构
- 新增复杂逻辑时尽可能补测试

---

## 九、测试要求

### Quick Settings Tile

至少验证：

1. 安装后可添加「速记」磁贴
2. App 未运行时点击
3. App 后台运行时点击
4. App 前台运行时点击
5. 直接进入新建 Memo
6. 输入框自动获得焦点
7. 软键盘行为合理
8. 发布成功
9. 返回行为正常
10. 当前项目最低 Android 版本正常

### 标签自动补全

测试：

```text
#
#数
今天学习 #数
今天学习了#数
#408
#数据结构
第一行
#数学
#408/计网
```

以及：

- 中文标签
- 英文标签
- 数字标签
- 嵌套标签
- 多个标签
- 光标在正文中间
- 修改已有标签
- 删除 `#`
- 删除部分标签
- 点击候选
- 新建 Memo
- 编辑已有 Memo
- 没有任何已有标签
- 标签 API 网络失败
- 离线状态
- 长文本
- 长标签列表

特别检查：

```text
补全标签后光标位置不能错误跳到正文末尾
```

### 最新版 Memos 端到端测试

必须使用真实最新版 Memos 实例验证：

```text
连接服务器
→ PAT 登录成功

刷新时间线
→ Memo 正常显示

新建 Memo
→ Web 端立即可见

编辑 Memo
→ 服务端内容正确变化

删除 Memo
→ 服务端正确删除

搜索
→ 返回正确结果

输入 #
→ 获取真实已有标签
→ 标签自动补全

创建 #新标签
→ 发布成功
→ 后续成为候选

带图片 Memo
→ 上传成功
→ Android / Web 均可查看

修改可见性
→ PRIVATE / PROTECTED / PUBLIC 正常

重新启动 App
→ 数据正常加载
```

不能只验证“App 可以启动”或“可以登录”。

---

## 十、工作流程

不要一上来直接写代码。

请按以下顺序：

1. 阅读仓库结构和 README
2. 检查当前 Moe Memos 支持的 Memos 版本
3. 查阅最新版 Memos 官方 API / proto / 源码
4. 对比当前 Android API 实现，列出真正存在的兼容差异
5. 找到新建 / 编辑 Memo 的完整调用链
6. 找到标签数据来源
7. 找到现有 Quick Action / Share Sheet 可复用入口
8. 给出简短、具体的修改方案
9. 再开始修改
10. 完成最新版 Memos API 适配
11. 完成 Quick Settings Tile
12. 完成 `#` 标签自动补全
13. 运行项目已有 test / lint / build
14. 修复本次修改造成的问题
15. 做关键端到端验证
16. 最后汇报修改结果

如果仓库实际结构与你预想不同：

**以实际代码为准，不要强行套用预设架构。**

---

## 十一、信息优先级

如果资料冲突，优先级：

```text
最新版 Memos 官方源码 / proto
>
最新版 Memos 官方 API Reference
>
最新版 Memos Web 客户端实际行为
>
Moe Memos 当前代码
>
旧版文档
```

不要凭印象猜 API。

遇到字段、接口、认证、分页、filter、resource name 等问题时，先查官方定义。

---

## 十二、禁止事项

不要：

- 要求 Memos 降级
- 使用 Mortis 等代理作为最终方案
- 仅修改版本检查就宣称“支持最新版”
- 只验证登录成功
- 把 Memos 0.30+ 当作旧版 API 强行解析
- 为通知栏速记重写第二套编辑器
- 为标签补全重写整个编辑页面
- 为了实现需求重写整个 Moe Memos
- 在 UI 中散落大量版本判断
- 无理由删除现有功能

正确方向：

```text
保留 Moe Memos 现有架构和 UI
+
更新必要的 API / DTO / Mapper / Repository
+
增加 Quick Settings Tile
+
给现有编辑器增加 # 标签自动补全
+
验证最新版 Memos
```

---

## 十三、最终验收场景

### 场景 A：突然想到一句话

```text
手机正在使用任意 App
→ 下拉通知栏
→ 点击「速记」
→ 新建 Memo 页面直接出现
→ 键盘可以立即输入
→ 输入内容
→ 发布
→ 返回原来的使用场景
```

整个过程尽可能少点击。

### 场景 B：正常管理 Memos

```text
打开 Moe Memos
→ 浏览 Memo
→ 查看
→ 编辑
→ 删除
→ 搜索
→ 管理标签
```

完整客户端能力保持正常。

### 场景 C：快速添加已有标签

```text
编辑 Memo
→ 输入 #
→ 自动列出当前服务器已有标签
→ 继续输入进行过滤
→ 点击候选
→ 正确插入标签
→ 光标位置正确
→ 继续输入
```

### 场景 D：最新版 Memos

```text
最新版 Memos 服务端
→ Android 客户端直接连接
→ 浏览 / 新建 / 编辑 / 删除 / 搜索 / 标签 / 附件全部正常
```

这四个场景全部通过后，才算完成本次需求。
