# 代码规范（团队协作版）

目标：任何同事打开任何一个文件，都能在 30 秒内知道**它负责什么、边界在哪、怎么改**。
规范不是为了好看，是为了减少 review 时的来回和上线后的回归。

## 一、目录与文件

- 一个文件一个主题：`ReaderScript.kt` 只放阅读引擎 JS，`AppIcons.kt` 只放图标。
  文件超过 ~500 行且包含两个以上主题时，拆文件。
- 包名 = 职责，不按类型堆：
  `core/format/<format>/` 放解析器与文档模型；`ui/<feature>/` 放屏幕与它的 ViewModel。
- 新增格式时建 `core/format/<name>/`，不要把格式细节泄漏到 `ui/`。

## 二、命名

| 对象 | 规则 | 例 |
| --- | --- | --- |
| 类 / 接口 | 大驼峰、名词 | `ComicDocument`、`ReaderBridge` |
| 函数 | 小驼峰、动词开头 | `loadChapter()`、`renderPage()` |
| 布尔 | `is/has/can/should` 开头 | `hasNextChapter`、`isPagedDocument` |
| 常量 | 全大写 + 下划线，放 `companion object` | `MAX_COVER_BYTES` |
| Composable | 大驼峰、名词短语 | `ReaderBottomBar`、`ThemeCard` |
| UiState 字段 | 名词、可空表示"还没有" | `chapter: ChapterContent?` |

## 三、注释与文档

- **注释用中文**（与用户语言一致），标识符、日志里的键名保持英文。
- 每个对外类型（被其它包用到的类/接口/枚举）必须有 KDoc：**说明它负责什么、边界是什么**，
  而不是复述代码。例：

  ```kotlin
  /**
   * 漫画压缩包阅读器：根目录图片算一章，子文件夹按名字自然排序各算一章。
   * 页面直接从压缩包里按需解码，不会把整包读进内存。
   */
  ```

- 「为什么这么做」值得写；「这行做了什么」不值得写。
  反面例子：`// 把 i 加一`；正面例子：`// 惰性加载在多栏排版下会判定图片不在视口，封面页会永远空白`。
- 踩过的坑写在代码旁边一句话 + 交接文档里一段话，并补一条测试锁死
  （本项目已有：桥接同名递归、严格模式未声明变量、图片懒加载、空文本节点定位）。

## 四、Kotlin / 协程

- 只在 ViewModel 里持有协程作用域；数据层函数是 `suspend`，不自己 `launch`。
- 阻塞 IO（文件、zip、BitmapFactory）一律 `withContext(Dispatchers.IO)`。
- 可失败的外部输入用 `ParseException`（带 `ParseErrorReason`）表达，UI 负责显示；
  不要在解析层弹 toast / 打日志当错误处理。
- 集合变换优先 `map/filter/associate`；超过 3 步或含副作用时拆成具名函数。
- 可空值用 `?:` / `?.let` 明确兜底，不用 `!!`（除非是 `findViewById` 这类不可能失败的场景，本项目没有）。

## 五、Compose

- 屏幕 = 无状态 Composable + `viewModel.state.collectAsStateWithLifecycle()`；
  子组件只接收数据与 `onXxx` 回调，不读 ViewModel。
- 传给 Composable 的回调要 `remember`（否则每次重组都换实例，WebView/AndroidView 会重建）。
- 颜色 / 形状 / 字体 / 图标只能从 `MaterialTheme` 与 `ui/theme` 取，禁止 `Color(0xFF...)` 写在界面里。
- 列表用 `LazyXxx` + `key`；`Column` 里放长列表是 review 必打回项。
- 图标统一用 `LocalAppIcons.current.xxx`，不要直接写 `Icons.Outlined.*`（否则静奢风格会漏掉）。

## 六、数据与持久化

- 新增设置项：`AppSettings`/`ReaderSettings` 加带默认值的字段 → `SettingsStore` 读写 → 设置界面加控件。
  **必须给默认值**，否则老用户升级后数据缺失。老字段改名要在 `fromId()` 里保留兼容映射。
- Room 结构变更必须写 Migration，并补一条迁移测试。
- 任何"用户数据"（进度、书签、设置）都要能通过 `DataTransferRepository` 导出/导入。

## 七、测试

- 新功能至少补一条单元测试；修 bug 必须先写出能复现的测试再修。
- 解析类测试用真实文件（现场生成 zip/EPUB 也可以），不要 mock 到只剩理想路径。
- 引擎 JS 的测试在 `ReaderEngineTest`（Rhino 真跑），排版/手势的测试在 `tools/preview`（真 Blink）。
- 提交前跑：

  ```bash
  ./gradlew testDebugUnitTest        # 全量单测
  ./gradlew assembleDebug            # 出包
  ```

## 八、提交与评审

- 提交信息：`模块: 做了什么`（例：`reader: 章末改为下拉拉出下一章标题`），一次提交只做一件事。
- 改版本号：每次交付递增 `versionCode` 与 `versionName`，并在 `app/build.gradle.kts` 顶部的变更表里
  写一行中文说明（本项目约定，方便对比 APK）。
- 评审清单：
  1. 依赖方向有没有倒过来（UI 直接读数据库 / format 里引用 UI）？
  2. 有没有在热路径（滚动、拖动、每帧）里做重建 / 解码 / 解析？
  3. 新增设置项有默认值吗？老数据还能读吗？
  4. 有没有把"为什么"写进注释，并补上测试？
  5. 主题相关改动有没有同时照顾经典与静奢两种风格？
