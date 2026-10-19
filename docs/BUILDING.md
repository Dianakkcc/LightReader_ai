# 构建、测试与发布

## 固定版本

| 工具 | 版本 |
| --- | --- |
| JDK | 17 |
| Gradle Wrapper | 8.11.1 |
| Android Gradle Plugin | 8.10.1 |
| Kotlin / Compose 编译插件 | 2.1.20 |
| KSP | 2.1.20-1.0.32 |
| compileSdk / targetSdk | 36 / 36 |
| minSdk | 26 |
| Android Build Tools | 35.0.0（项目显式指定） |

Android Studio 推荐 Meerkat Feature Drop 2024.3.2 或兼容以上版本。配置 SDK 并在 Gradle 设置中选 JDK 17；本地 `local.properties` 由 Studio 生成，不应提交。也可通过 `ANDROID_HOME` 指定 SDK。
依赖来自 Google Maven、Maven Central 和 Gradle Plugin Portal。仓库不包含 SDK、JDK、Maven 缓存、模拟器或浏览器二进制。

## 常用命令

```powershell
.\gradlew.bat :app:assembleDebug
.\gradlew.bat :app:testDebugUnitTest
.\gradlew.bat :app:assembleRelease
.\gradlew.bat :app:assembleDebugAndroidTest
.\gradlew.bat :app:connectedDebugAndroidTest
```

Linux/macOS 将 `.\gradlew.bat` 换为 `./gradlew`。首次构建联网下载依赖；缓存完整后可尝试 `--offline`，缓存不全时离线构建会失败。Gradle 分发包配置了官方 SHA-256 校验。

设备测试用独立设备/模拟器，避免测试安装卸载影响个人数据。阅读引擎还有 Rhino 单元测试与 `tools/preview` 的 Blink 排版验证，后者需要单独提供 Chrome 可执行文件。

## 正式签名

默认 `assembleRelease` 输出经过 R8/资源压缩的未签名 APK；附带的 CI 配置仅上传 unsigned 产物；是否已远程成功运行须在 Actions 中核对。Debug 使用开发机的默认调试签名，只用于测试。

发布时提供四个环境变量：

| 变量 | 内容 |
| --- | --- |
| `LR_KEYSTORE_PATH` | 自己的签名库路径 |
| `LR_KEYSTORE_PASSWORD` | 签名库密码 |
| `LR_KEY_ALIAS` | 密钥别名 |
| `LR_KEY_PASSWORD` | 密钥密码 |

四项一起设置后再运行 `assembleRelease`，输出 `app-release.apk`。密码应通过私有环境配置或 CI Secrets 注入；不要写进仓库、截图、日志、命令历史或提交密钥文件。
用 SDK Build Tools 的 `apksigner verify --verbose --print-certs` 验证输出。覆盖升级要求 applicationId 相同、签名相同且 versionCode 不降低；本版从旧调试证书迁移到专用证书时需按 [MIGRATION.md](MIGRATION.md) 先导出数据再换装。已有个人测试包的签名并未随源码公开；其他人自行构建的包通常不能直接覆盖它。

维护者应自行保管并备份签名密钥。仓库没有自动上传、创建 Release 或推送代码的步骤。

## GitHub Actions

`.github/workflows/android.yml` 在 push/PR 时配置 JDK 17 和 SDK 36，运行本地测试、严格 lint、编译设备测试与 release，并上传测试报告和 unsigned APK。它不运行需要设备的测试，也不持有发布密钥。
工作流已整理为可提交配置，本地验证不代表已在 GitHub 执行。首次 push 后检查 Actions 结果。

## 版本与 Release

每次发布更新 `app/build.gradle.kts` 的 `versionCode` / `versionName` 与 CHANGELOG。
为正式 APK验证签名与覆盖安装，附上版本说明、源码标签和 APK 校验值。发布签名 APK 时必须保留 assets 中许可文件；不要删掉 APK 的许可查看入口。

## 更新第三方清单

版本目录在 `gradle/libs.versions.toml`。更新依赖后运行：

```powershell
.\gradlew.bat --init-script tools/licenses/inventory.init.gradle.kts :app:exportThirdPartyInventory
python tools/licenses/check_inventory.py
```

导出到 `build/audit/dependencies.tsv`。检查脚本对比仓库里的已记录坐标，不把本地路径写入提交文件；有新坐标时查对应 POM、发布归档及上游 LICENSE/NOTICE，人工更新 JSON/Markdown 清单与许可全文，再更新 APK 里的运行时副本。
POM 声明不能替代归档内的嵌套素材许可。PDFBox 内字体、CMap、AFM、字形表和 Unicode 数据尤其需要分别保留声明。

## 2.9.0 固定依赖升级

Room 2.8.5、jsoup 1.23.2、Compose BOM 2025.05.01；Kotlin 2.1.20 与 KSP 2.1.20-1.0.32配套使用。PDFBox-Android 保留其上游最新发布2.0.27.0，不能直接替换为桌面版坐标。完整实际解析版本以DEPENDENCIES.md为准。

Gradle 配置在提供签名环境变量时会拒绝Android Debug证书，避免再次将调试证书误用于正式发布。新证书公钥指纹与APK校验值随发布包提供。
