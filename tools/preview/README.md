# Blink 排版验证台（不需要手机）

用**真实的 Blink 内核**（Chrome headless shell，与 Android WebView 同源）加载 App 真正生成的阅读页面，
测量分页几何并截图。用来在没有设备/模拟器的情况下验证：

- 翻页步长是否等于屏幕宽度、每页落点是否精确（"翻页不匹配"类问题）
- 正文左右/上下留白是否生效（"文字贴边"类问题）
- 双页模式是否真的排成两栏
- 滚动模式引擎是否正常启动（曾因变量未声明整体失效）
- 视觉效果（可直接看截图）

## 用法

```powershell
# 1) 生成页面（用 App 的真实排版流水线）
cd <项目根>
./gradlew :app:testDebugUnitTest --tests "*ReaderPreviewFixture*"

# 2) 测量 + 截图
powershell -ExecutionPolicy Bypass -File tools/preview/run-preview.ps1

# 3) 章界横幅：把"拉出下一章/上一章标题"拉到一半停住，截图并回读它的计算样式
powershell -ExecutionPolicy Bypass -File tools/preview/run-banner.ps1
```

脚本会查找 Windows 常见的 Chrome 安装位置，也可用 `-Chrome <可执行文件路径>` 指定 Chrome / chrome-headless-shell。浏览器需自行安装，不随仓库分发。

此验证台只验证 Blink 排版与脚本，不是 Android 真机或性能测量。

## 输出

- 控制台：每个场景的 `pages / stride / margins / page1 box`，以及桥接回调是否触发、脚本错误
- `shots/*.png`：手机、平板双栏、滚动模式的第一页与翻页后截图

## 说明

- `harness.js` 模拟 Android 侧的 `LrBridge`，随后走几页并记录真实 transform 与字符偏移。
- `pre-harness.js` 在引擎之前注入，用来捕获引擎启动期间的 JS 异常（曾靠它抓到
  `shortChapter is not defined` 这种会让整个引擎失效的错误）。
- `harness-clean.js` 只测量第一页，用于截图评审。
- `harness-banner.js` + `run-banner.ps1` 抓住章界横幅拉到一半的状态（不松手），输出它的
  `opacity / background-image / border-radius / box-shadow` 与截图——"横幅带颜色"这类问题
  用肉眼比较截图、再对一下计算样式就能定位（曾抓到 8 位十六进制颜色写成 `#AARRGGBB`）。
- `pages/` 与 `shots/` 是生成物，已在 `.gitignore` 中忽略。
