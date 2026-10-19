# 发布核对

1. 版本号与 CHANGELOG 一致；本版 2.9.0 / 35，不与历史独立分支2.8.2混用。
2. 配置四项 LR_KEYSTORE_PATH / LR_KEYSTORE_PASSWORD / LR_KEY_ALIAS / LR_KEY_PASSWORD，使用已保存的专用发布密钥。不要每个版本生成新证书。
3. 执行 `:app:testDebugUnitTest :app:lintRelease :app:assembleRelease :app:compileDebugAndroidTestKotlin`；lint 错误必须修复，不生成新 baseline 掩盖错误。
4. `apksigner verify --verbose --print-certs` 应通过，证书为已公布指纹，不能是 Android Debug。再检查实际包名、版本、权限与 zipalign。
5. 生成 APK 的 SHA-256，将完整数值写入 SHA256SUMS.txt 与 Release 说明。
6. 检查源码 ZIP 内三个点文件真实存在；排除密钥、本机配置、build、.gradle、个人资料与APK。
7. 换证书用户按 MIGRATION.md 操作。未在真实设备执行的测试必须据实写明。
8. 新建版本 Release、粘贴完整说明、附 APK/校验值/公开证书，确认后发布。不静默替换旧版本资产。

当前 compileSdk/targetSdk 为36；未实际提交Google Play，API等级达标不等于商店审核已通过。通用 APK 不代表按 ABI 拆包能减半，体积判断以实际 lib 文件占比为准。
