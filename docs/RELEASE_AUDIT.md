# 发布审查逐项处理

本轮从2.8.1 AI开发方式披露版建立独立2.9.0工作副本，旧源码、APK和资料保留。业务改动仍由AI生成和修改，维护者没有被描述为手写代码作者。

| 原分析项目 | 处理结果 |
|---|---|
| 调试证书用于公开发行 | 已改用独立4096位RSA发布证书CN=LightReader Release，并在构建配置中拒绝Android Debug发布签名。按维护者选择只提供正式签名包，旧用户需先备份换装。 |
| .github / .gitignore / .gitattributes遗漏 | 完整源码目录和ZIP均包含三个点文件及GitHub模板，CI加入严格lint和源码核对。另给Git上传步骤，避免再次依赖网页文件夹拖拽。远程仓库本轮未改，必须实际提交后才能补齐。 |
| Release空说明、无校验值 | 提供完整2.9.0发布说明、APK与源码SHA-256、证书公钥和指纹。建议使用新v2.9.0标签，保留旧版本历史，不静默替换旧签名资产。 |
| 英文界面混杂中文 | 将227处主要界面字面量接入资源，书架/主题标签动态读取；中英文293项键一致，补英文阅读设置和资源回归。书籍内容、第三方许可原文及技术日志不作翻译承诺。 |
| 日志常开 | d/w/e统一仅在BuildConfig.DEBUG启用，移除逐资源送达日志，正式包不执行引擎诊断探针。 |
| WebView资源请求runBlocking | 接口改为明确的WorkerThread同步本地I/O，在WebView请求线程直接读资源，移除runBlocking与8秒协程超时。资源读取仍受文档大小和存储速度影响，不宣称实机性能已量化提升。 |
| lint不阻断 | abortOnError=true，错误阻断发布；升级AGP/Kotlin/Compose检查器，桥接注册改为明确类型，最终0错误，无新增屏蔽规则。 |
| PDF文件描述符泄漏 | 构造渲染器失败时关闭描述符，覆盖直接打开和缓存两条路径，补成功/失败/关闭再失败三项资源所有权测试。另修TXT流对ParcelFileDescriptor的托管。 |
| 依赖升级 | jsoup1.18.3→1.23.2；Room2.6.1→2.8.5；DataStore1.1.1→1.1.7；AGP8.7.3→8.10.1；Kotlin2.0.21→2.1.20；KSP配套升级；Compose BOM2024.12.01→2025.05.01。按已验证兼容组合锁定，并非宣称每个依赖均为最新。 |
| PDFBox版本旧 | PDFBox-Android上游最新发布仍是2.0.27.0，保留该Android坐标，不能直接换桌面PDFBox。其内嵌资源及上游许可继续保留。 |
| gradlew换行 | .gitattributes固定LF，源码核对脚本检查实际字节；CI执行chmod +x。 |
| targetSdk35 | compileSdk/targetSdk均升至36，补API36平台并通过构建；没有实际向Google Play提交或宣称审核通过。 |
| 备份导入O(n²) | 一次读书架建立URI/文件名索引，唯一文件名才允许回退匹配；100条未匹配备份只读一次书架的回归通过。 |
| 缺截图、About信息 | 补9张实际Compose组件截图，README展示其中4张并标注测试环境；提供topics建议，远程About未自动修改。 |
| allowBackup与隐私说明 | 保留系统备份，README和应用内说明明确系统/文件提供程序行为；换签名迁移依赖手动JSON与原书，不假设云备份可恢复。 |
| immutable release | 在发布指南说明发布前核对附件及可选设置。本轮未更改远程设置。 |
| ABI拆分可减半 | 2.8.1原生库压缩合计仅60292 bytes，拆分收益很小，保留单个通用APK。新包也通过16KiB原生库对齐检查。 |
| 角色图片 | 按维护者此前明确要求保留，来源与未核实授权说明沿用，不把图片声明为原创。 |

依赖许可随实际升级重新核对；DataStore新增重打包protobuf从内嵌RuntimeVersion常量确认4.28.2，并附上对应v28.2标签的BSD许可。测试对象改为显式释放ViewModel、数据库与DataStore，Room测试使用原生SQLite，避免Windows/Robolectric环境的遗留线程串扰。

另取消数据库缺少迁移时自动清空书架的回退：未来迁移配置有误时保留数据并报错。未改数据库结构版本。

参考：[APK签名工具](https://developer.android.com/tools/apksigner)、[AGP8.10兼容表](https://developer.android.com/build/releases/agp-8-10-0-release-notes)、[Room发布记录](https://developer.android.com/jetpack/androidx/releases/room)、[DataStore发布记录](https://developer.android.com/jetpack/androidx/releases/datastore)、[jsoup发布记录](https://jsoup.org/news/)、[PDFBox-Android最新发布](https://github.com/TomRoush/PdfBox-Android/releases/latest)。
