# 验证记录

日期：2026-10-07。版本：2.8.1 / versionCode 33。

## 本次已完成

- `:app:testDebugUnitTest`：106 项，0 failures、0 errors、0 skipped。
- `:app:assembleRelease`：成功，默认输出 unsigned APK；R8/资源压缩开启。
- `:app:assembleDebugAndroidTest`：设备测试包编译成功，包含既有 9 项设备测试。
- 解析依赖导出及清单比对：87 个 release 模块、60 个仅 debug/测试模块；坐标及配置范围一致。
- APK 许可文件与源码中的 assets 逐字节一致；角色图片也保持原样。
- Noto Serif SC 2.003 与上游精确 Git blob 相同；Gradle Wrapper JAR 与官方 SHA-256 相同，分发 ZIP 也配置了校验值。
- 检查 Markdown 本地链接、素材/许可索引和提交目录，未发现私人路径、密钥或个人阅读文件。

列数保存测试最初因未等待首份设置到位而超时；已使用可区分默认 UI 状态的初始设置并等待消费，随后全量通过。

## 环境与范围

使用 JDK 17、SDK Platform 35、Build Tools 35.0.0、Gradle 8.11.1，复用本机依赖缓存。
本地验证使用仅配置仓库镜像与 Robolectric 下载源的环境 init 文件，未禁用 Java 编译任务；该本机文件不提交。
现有源码仍有编译警告（弃用 API 等），本次没有为文档整理扩大到全面警告清理。

## 尚未验证

- 没有连接可用手机/模拟器，没有运行 connectedDebugAndroidTest；“编译成功”不是“9 项设备测试通过”。
- 没有运行 GitHub Actions 或在完全空白的联网机器构建；CI 首次 push 后需查看实际结果。
- 没有实测手机帧率、覆盖安装新版本或正式发布签名。
- 许可查看入口已编译、文件完整性已核对，尚未在手机上手动点击验证。
- 第三方角色图案的原始画师和分发授权未核实；保留并单独说明不等于已取得授权。

这份记录报告实际检查，不代表法律授权审计或所有设备/文件格式的完整兼容性保证。
