# 架构说明（LightReader）

这份文档写给第一次接手这个项目的工程师：读完它应该能在不看代码的情况下说出
「一次点击打开一本书，数据经过了哪些层」，并知道每一层在哪里、边界是什么。

## 一、总体分层

```
        ┌──────────────────────────── UI（Compose） ────────────────────────────┐
        │ ui/library   书架：书籍/漫画两架、网格/列表、导入、筛选、长按菜单     │
        │ ui/reader    阅读器：重排版 WebView、PDF/图片分页、漫画连续流、目录/搜索 │
        │ ui/settings  设置：阅读参数、字体管理、配色 / 底色 / 整体风格           │
        │ ui/theme     Material 主题、阅读配色、图标集（经典 / 静奢）             │
        └───────────────┬──────────────────────────────────────────────────────┘
                        │ 只依赖 ViewModel 暴露的 UiState + 事件方法
        ┌───────────────▼──────────── ViewModel（状态机） ──────────────────────┐
        │ LibraryViewModel / ReaderViewModel / SettingsViewModel               │
        │ 持有 UiState（StateFlow）、协程作用域、把「用户意图」翻译成仓库调用     │
        └───────────────┬──────────────────────────────────────────────────────┘
                        │
        ┌───────────────▼──────────────── Data（仓库） ────────────────────────┐
        │ ReadingRepository（进度/书签）、LibraryRepository（书架）、            │
        │ SettingsStore（DataStore）、BookImporter、BookWorkspace（缓存目录）   │
        │ Room（AppDatabase）+ DataStore Preferences                            │
        └───────────────┬──────────────────────────────────────────────────────┘
                        │
        ┌───────────────▼────────── Format（格式适配层，与 UI 解耦） ──────────┐
        │ DocumentParser 接口 → ParserFactory/ParserRegistry 注册表             │
        │ epub / comic / pdf / txt / docx / rtf / html / markdown / image      │
        │ 统一输出：OpenedDocument（ReflowableDocument | PagedDocument）        │
        └──────────────────────────────────────────────────────────────────────┘
```

依赖方向永远是**从上到下**：`format` 不知道 UI 的存在，`data` 不知道具体格式，
ViewModel 不碰 Android View，UI 不直接访问数据库或 zip。

## 二、两条数据流

### 1）导入一本书

```
用户选文件（SAF）
  → BookImporter 读元数据（DocumentMetadata）+ 封面缩略图（CoverProvider.loadCover）
  → LibraryRepository 写 Room（books 表：URI、标题、作者、格式、大小、封面路径）
  → 书架刷新（Flow → LibraryViewModel.UiState → Compose）
```

导入保留原书位置，读取元数据并生成封面；需要随机访问的格式会建立私有缓存副本。URI 通过
`ContentResolver.takePersistableUriPermission` 持久化授权。

**书架归属**：`books.shelfKind`（`book` / `comic`）决定条目进哪个书架，导入时由
`ShelfKind.defaultFor(format)` 给出（漫画包 → 漫画，其余 → 书籍），PDF / 图片这类两可的格式
在书架长按菜单里改。归属还会决定分页文档怎么读（见下）。老库通过 `MIGRATION_2_3`
补上该列，并把已经在架上的漫画包自动归到漫画书架。

### 2）打开一本书阅读

```
书架点击 → AppNavHost → ReaderViewModel(bookId)
  → ParserRegistry.open()：FormatDetector 判定格式 → 对应 Parser → OpenedDocument
        ├── ReflowableDocument（EPUB/TXT/DOCX/RTF/HTML/MD）
        │     → loadChapter(index) → ChapterContent(html/text/css)
        │     → ChapterHtml.build() 组装完整页面（书 CSS + 阅读器 CSS + 正文 + 引擎 JS）
        │     → WebView.loadDataWithBaseURL("https://reader.local/", …)
        │     → WebViewClient.shouldInterceptRequest 拦截 reader.local 下的书内资源、字体和主题图案请求交给宿主
        └── PagedDocument（PDF / 图片 / 漫画）
              → loadPageBitmap(page, width, height) → Bitmap → Compose Image
              · PDF / 图片 → PagedReaderView（一页一屏，左右翻页）
              · 漫画     → ComicFlowView（LazyColumn 竖向连续流，章节=流里的分隔标题）
              ↑ 走哪条由 `book.shelfKind` 决定：漫画包总是连续流，PDF/图片被移到漫画书架后
                也走连续流（页高先按 `pageAspect` 预留，解码后按真实位图比例校正）
                           外部改页码（目录 / 进度条 / 恢复进度）必须滚到该页：LazyColumn 只在
                           首次组合时取起始索引，所以要显式 scrollToItem；手动滚动会先上报
                           自己的页码，因此两者不会打架（见 ComicFlowJumpTest）
  → WebView / Compose 通过 ReaderBridge 回报位置、章末、章首、几何
  → ReaderViewModel 折算百分比 → ReadingRepository 落盘（去抖 0.7s）
```

## 三、阅读引擎协议（重要）

重排版阅读面是一个 WebView，宿主与页面之间只有两种通信方式：

| 方向 | 机制 | 用途 |
| --- | --- | --- |
| 页面 → 宿主 | `window.LrBridge.*`（`addJavascriptInterface`） | `onReady`、`onLocation`、`onReachEnd`、`onReachStart`、`onMetrics` |
| 宿主 → 页面 | `evaluateJavascript("window.LrReader.xxx()")` | `nextPage/prevPage/goToOffset/scrollToId/highlight/setStyle/setNextChapter/setPreviousChapter/refresh/cancelGesture` |

约定（违反会导致真机 bug，历史上有过两次）：

1. **桥接方法的参数名不能和方法名相同**（`fun onLocation(...)` 里不能再用 `onLocation` 作回调名），
   否则 Kotlin 解析到方法自己 → 无限递归 → 主线程 100% CPU。回归测试：`ReaderBridgeShadowingTest`。
2. 引擎 JS 在**严格模式**下运行，任何「赋值给未声明变量」都会整段脚本失效，
   `ReaderScriptDeclarationTest` 会静态扫描并拦截。
3. 引擎只做**几何**：分页、翻页动画、位置换算、章末/章首手势。任何业务（进度、下一章是谁）
   都在 Kotlin 侧，页面永远不决定"跳到哪一章"。
4. 页面资源全部走虚拟主机 `https://reader.local/`；应用**没有 INTERNET 权限**，
   `shouldInterceptRequest` 是唯一出口（`epub/`、`media/`、`font/`、`brand/` 四条前缀）。

## 四、线程与生命周期

- 所有文件 / zip / 图片解码在 `Dispatchers.IO`；UI 状态只在主线程更新（`StateFlow` + `MutableStateFlow.update`）。
- 解析结果 `OpenedDocument` 由 ViewModel 持有，`onCleared()` 里 `close()`，
  ViewModel 作用域取消时不会留下打开的 zip。
- 阅读位置写入 DataStore/Room 做**去抖**（滚动停止 0.7s、翻页立即），避免拖动滑杆时刷盘。
- 长任务（TXT 索引、EPUB 权重扫描、PDF 目录）都是「先让用户读上，再后台补数据」的模型：
  `prepareXxx()` 先给出可用状态，后台完成后用 `_state.update` 修正派生字段。

## 五、UI 约定

- 每个屏幕 = `XxxScreen`（纯 UI）+ `XxxViewModel`（状态与事件），屏幕之间只通过 `AppNavHost` 传 id/URI。
- 颜色、形状、字体、图标**只能**来自 `ui/theme`：
  `AppPalette`（书架配色）、`BackgroundTone`（画布底色）、`ThemeStyle`（经典 / 静奢，含整套 `AppIconSet`）、
  `ReaderTheme`/`ReaderPalette`（正文纸色）。
  新增主题 = 在枚举里加一项 + 在 `ThemeMapping` 里补映射，**不要**在界面里写死颜色。
  **一个画布色拥有整套 surface 角色**：`surface`、`surfaceContainer*`、`surfaceDim/Bright` 全部由
  `background` 推导（列表行、顶栏与页面同色，卡片与面板只差几个百分点）。只改 `background`
  会让设置页出现"一块粉一块白"的色差——这是真机上报过的问题。
- 工具栏是覆盖式的：呼出/收起时正文不重排；面板打开时工具栏收起，一次只开一个面板。

## 六、测试策略

| 层次 | 手段 | 位置 |
| --- | --- | --- |
| 格式解析 | Robolectric + 真实文件（EPUB/图片/漫画 zip 现场生成） | `app/src/test/**/format` |
| 阅读引擎 JS | Rhino 在 JVM 里真跑生成的 JS，断言翻页步长、章末/章首手势 | `ReaderEngineTest` |
| 端到端流程 | 真实 ViewModel + 假文档（真时钟） | `ReaderChapterFlowTest` |
| 界面行为 | Compose 仪器化测试（真机/模拟器）：书架、设置入口、漫画流跳转 | `app/src/androidTest` |
| 静态防回归 | 扫描生成的 JS / 桥接方法体 | `ReaderScriptDeclarationTest`、`ReaderBridgeShadowingTest` |
| 排版与手势 | Chrome headless（同源 Blink）加载真实页面测量并截图 | `tools/preview/` |
| 真机 | 模拟器 AVD + 真实书籍/漫画包，看 logcat 与截图 | 见交接文档 |

## 七、扩展点

| 想做的事 | 改哪里 |
| --- | --- |
| 增加一种书籍格式 | `BookFormat` 加枚举 + 写 `DocumentParser` + 注册到 `ParserFactory`（见 [FORMATS.md](FORMATS.md)） |
| 增加一套配色 / 纸色 | `AppPalette` / `BackgroundTone` / `ThemePreference` + `ThemeMapping` |
| 增加一套图标风格 | `ui/theme/AppIcons.kt` 的 `AppIconSet` + `ThemeStyle` |
| 改阅读手势 | `ui/reader/ReaderScript.kt`（引擎）+ `ReaderEngineTest` + `tools/preview` |
| 改排版默认值 | `ReaderSettings` 默认参数（旧数据由 `SettingsStore` 补默认值） |
