# 面板性能说明与验证边界

## 已定位的代码开销

用户反馈集中在目录、设置等附加面板，正文相对流畅。2.8.0 对实际代码路径做了以下处理：

| 原开销 | 处理 |
| --- | --- |
| 组合阶段读取动画值，每帧重组整个 ReaderScreen | 保存动画 State，仅在 graphicsLayer 读取；结束后卸载/恢复正文 |
| Column.verticalScroll 一次创建和布局全部设置 | LazyColumn 分项按需布局 |
| 滑杆每次移动都写 DataStore 并触发阅读参数更新 | 本地草稿更新，松手后提交 |
| AppSettings 任意字段变化重建主导航相关组合 | 仅观察并去重主题字段 |
| 字体文件及漫画图片头在 UI 路径处理 | 放到后台；ZIP 流式解码、按目标尺寸采样 |
| 条漫目标高度 Int.MAX_VALUE 使采样失效 | 用仅按宽度适配的约定，限制解码并发 |

相关代码：ReaderScreen、ReaderPanels、SettingsScreen、MainActivity、ComicFlowView、ImageLoader、ImageSampling。

## 证据范围

本地回归覆盖动画不逐帧重组内容、设置能滚动到末尾、图片头读取不在主线程和采样算法；不能据此宣称手机始终满帧。
2.8.0 的 106 项本地测试通过；9 项设备测试编译完成，本轮没有可运行的手机/模拟器。当前验证见 VALIDATION.md。

旧版本曾记录模拟器掉帧与窗口建立开销；“某品牌手机稳定掉帧一定由系统限帧造成”未经用户手机测量，不能当作结论。请求较高显示刷新率也不保证应用帧率。
Chrome/Blink 验证只验证 JS 与排版，Robolectric 只验证可模拟行为，两者都不是手机 GPU/合成器性能测量。

## 复现与测量

记录机型、Android/App/WebView 版本、屏幕刷新率、书籍格式和可共享的小样本。
分开测量面板打开、面板滚动、滑杆拖动和关闭恢复；在相同文档、相同刷新率和相同构建类型下比较。
使用 Android Studio Profiler / Perfetto 分析主线程、Compose 帧和 WebView 合成；调试版本的耗时不能直接代替 release。

```bash
adb shell dumpsys gfxinfo com.lightreader reset
# 在测试设备复现一次目标操作
adb shell dumpsys gfxinfo com.lightreader framestats
```

FrameStats 只是线索，对 WebView/合成行为需结合系统跟踪。未采集到真实数据前不填写提升百分比、FPS 或具体机型结论。
