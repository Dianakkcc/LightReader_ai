# 第三方组件、许可与版权声明

本项目原创部分使用自定义免费分发许可；以下第三方内容继续遵守各自原许可，不被统一替换。版本来自版本目录和实际 Gradle 解析结果，而非只抄直接依赖列表。

## 正式运行时主要组件

| 组件 | 当前版本 | 许可 | 用途/来源 |
| --- | --- | --- | --- |
| AndroidX / Jetpack Compose | BOM 2024.12.01；Compose 1.7.6 / Material3 1.3.1 | Apache-2.0 | [AndroidX](https://android.googlesource.com/platform/frameworks/support/)，UI、导航、生命周期与基础组件 |
| Room / DataStore / WebKit / DocumentFile | 2.6.1 / 1.1.1 / 1.12.1 / 1.0.1 | Apache-2.0 | 数据库、偏好、WebView 兼容与 SAF |
| Kotlin / kotlinx | stdlib 2.0.21；coroutines 1.9.0；serialization 1.7.3 | Apache-2.0 | [Kotlin](https://github.com/JetBrains/kotlin)、并发与 JSON；传递旧兼容模块见完整清单 |
| jsoup | 1.18.3 | MIT | [jsoup](https://github.com/jhy/jsoup/tree/jsoup-1.18.3)，HTML/XML 解析；版权 Jonathan Hedley |
| commonmark-java / GFM tables | 0.24.0 | BSD-2-Clause | [commonmark-java](https://github.com/commonmark/commonmark-java/tree/commonmark-parent-0.24.0)，Markdown；版权 Atlassian |
| PDFBox-Android | 2.0.27.0 | Apache-2.0，含独立资源条款 | [v2.0.27.0](https://github.com/TomRoush/PdfBox-Android/tree/v2.0.27.0)，文字层与目录；页面绘制用 Android PdfRenderer |
| Bouncy Castle | bcpkix / bcutil / bcprov jdk15to18 1.72 | MIT 风格的 Bouncy Castle 许可 | [r1rv72](https://github.com/bcgit/bc-java/tree/r1rv72)，PDFBox 的传递依赖 |
| Okio | okio-jvm 3.4.0 | Apache-2.0 | [Okio](https://github.com/square/okio/tree/parent-3.4.0)，DataStore 传递依赖 |
| Noto Serif SC | 2.003 | SIL OFL-1.1 | 内置阅读字体；© 2017-2024 Adobe，Noto 商标归 Google |

运行时另有 AndroidX、JetBrains annotations、Guava listenablefuture 等模块，完整 **87 项 release 坐标**见 [docs/DEPENDENCIES.md](docs/DEPENDENCIES.md)。BOM 是版本对齐元数据，不能把 BOM 版本当作全部组件的实际版本。

## PDFBox 内部内容

不仅携带 Apache 代码，还包含早期 PDFBox/FontBox 贡献（BSD 风格）、Adobe AFM 指标、CMap 与字形表、Unicode Scripts/Bidi 数据，以及 **Liberation Sans 2.1.5**（OFL-1.1）。
分别保留 [完整上游 LICENSE](licenses/pdfbox-LICENSE.txt)、[NOTICE](licenses/pdfbox-NOTICE.txt)、[资源版权头](licenses/pdfbox-resource-notices.txt)、[Unicode 许可](licenses/Unicode-2017.txt) 和 [Liberation OFL](licenses/liberation-OFL.txt)。不能仅用一句“PDFBox = Apache”覆盖全部内容。

## Debug、测试与构建

| 组件 | 版本 | 许可 | 范围 |
| --- | --- | --- | --- |
| JUnit | 4.13.2 | EPL-1.0 | 测试；原文从 JAR 保留 |
| Robolectric | 4.14.1 | MIT，含部分 Apache-2.0 内容 | JVM Android 测试；完整原文含独立说明 |
| Rhino | 1.7.15 | MPL-2.0，附 V8/Sun 等 NOTICE | 测试中执行 JS；不进入正式 APK |
| AndroidX Test / Compose UI Test / coroutines-test | 解析版本见清单 | Apache-2.0 | JVM/设备界面与协程测试 |
| Gradle / Wrapper | 8.11.1 | Apache-2.0 | 构建；[官方源码](https://github.com/gradle/gradle/tree/v8.11.1) |
| AGP / KSP / Room compiler | 8.7.3 / 2.0.21-1.0.28 / 2.6.1 | Apache-2.0 | 构建插件/代码生成；不随源码包分发工具二进制 |
| Kotlin 编译插件 | 2.0.21 | Apache-2.0 | 构建；与语言版本一致 |

测试的 Hamcrest、ASM、ICU、Conscrypt、javax.annotation 等传递依赖见完整清单，各自原文能从归档取得的已保存。测试清单不会把这些模块误算进 release APK。
开发机使用的 JDK、Android SDK、Chrome 和 CI Actions 是外部工具，不作为 App 自研内容；仓库只含 Wrapper JAR，不含它们的安装包。
GitHub Actions 使用 actions/checkout、actions/setup-java、actions/upload-artifact、gradle/actions、android-actions/setup-android，工作流引用其上游 action，不复制源码，分别遵守其仓库许可。

## 分发与来源

完整原文、归档声明和来源索引见 [licenses/README.md](licenses/README.md)。运行时所需副本已置于 `app/src/main/assets/licenses/`，可从设置页离线查看。构建时排除重复 META-INF 许可并不取消这些要求。
项目未修改第三方库的上游源码；按 Maven 发布物引用，Android 构建会做常规编译/R8 优化。字体 Noto 文件已与精确上游 Git blob 核对一致。

## 图片与其他作品

角色/装饰图片保留原样，直接来源为 Diana/Jiaran 主题素材包，原始画师及分发授权未核实，权利归原作者/相关权利人。它们不适用项目原创许可。
**如有侵权，请通过本仓库 Issues 联系删除。** 此声明不等于取得授权。
文件名、哈希、字体及自绘图标的具体说明见 [docs/ASSETS.md](docs/ASSETS.md)。
