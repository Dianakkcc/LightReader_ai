# 2.9.0 验证记录

日期：2026-10-19。版本2.9.0 / versionCode35，包名com.lightreader。

- Gradle8.11.1、AGP8.10.1、Kotlin2.1.20、KSP2.1.20-1.0.32；JDK17，compileSdk/targetSdk36，minSdk26。
- `:app:testDebugUnitTest`：**117项，0失败、0错误、0跳过**。包含本轮文件句柄失败路径、备份恢复匹配/查询次数、中英文资源和英文阅读设置回归；两项可选截图用例在设置LR_SCREENSHOT_DIR后实际执行。
- `:app:lintRelease`：**0错误，97警告、1提示**，`abortOnError=true`，没有新增baseline或关闭错误检查。剩余包括未使用资源、可更新依赖、格式/样式建议和上游库告警，原报告随交付保留；不宣称安全审计已全面完成。
- `:app:assembleRelease`成功，R8与资源压缩启用，APK **19,505,096 bytes**（18.60MiB）。
- `:app:compileDebugAndroidTestKotlin`成功：现有9项设备测试本轮仅编译，未连接手机或模拟器执行，未实测手机帧率。
- apksigner验证通过，v2签名有效，证书为CN=LightReader Release，指纹见下方；原Android Debug签名没有用于新APK。
- zipalign `-c -P16 4`通过；所有原生库LOAD段对齐均为16384。APK无INTERNET权限、非debuggable，内部版本与源码一致。
- 150项实际解析模块，其中90项运行时、60项仅debug/测试；与提交清单一致。APK中每份许可文件与源码副本逐字节一致。
- 生成9张真实生产Compose组件截图，使用SDK34原生图形模式和合成书架内容，并已查看书架、阅读设置样张；不是实机截图。1970日期来自测试时钟。

APK SHA-256：`d787f756bb217d5898bcf6dbd4acbb1864de9c92d007250f30a7d2d31c0e4ff8`

发布证书 SHA-256：`2e408eb33a5a45263b4ca1de6a08c3a03c771b6bdd2135622a2173364e0992b2`

旧2.8.1验证记录保留在[历史记录](HISTORY_VALIDATION_2.8.1.md)。本轮未push、未更换GitHub Release资产，远程CI是否成功须上传后检查。
