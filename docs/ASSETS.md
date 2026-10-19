# 素材来源与权利声明

## 字体

| 素材 | 版本/版权 | 来源与许可 |
| --- | --- | --- |
| `app/src/main/assets/fonts/NotoSerifSC-Regular.otf` | Noto Serif SC Regular 2.003；© 2017-2024 Adobe；Noto 为 Google 商标 | [上游 Serif2.003](https://github.com/notofonts/noto-cjk/blob/Serif2.003/Serif/SubsetOTF/SC/NotoSerifSC-Regular.otf)，[OFL 全文](../licenses/noto-OFL.txt) |
| PDFBox 依赖内 `LiberationSans-Regular.ttf` | Liberation Sans 2.1.5；2010 Google / 2012 Red Hat | [Liberation Fonts](https://github.com/liberationfonts/liberation-fonts/tree/2.1.5)，[OFL 全文](../licenses/liberation-OFL.txt) |

Noto 文件的 Git blob SHA-1 为 `be55fbdf3fe53bfc99779389f3f0b875707bf496`，与对应上游文件一致，未改动字体数据。
Liberation 字体由 PDFBox AAR 间接带入，并非阅读字体列表中的第二款内置中文字体。OFL 字体及其修改版继续按 OFL 授权，不受本项目免费分发许可替代。

## 角色与装饰图片

以下图片从 Diana/Jiaran 主题素材包沿用，文件内容与该素材包对应文件一致；此处记录的是直接取得的来源，不等于已找到最初画师或获得其分发授权。

| 项目文件 | 素材包原文件 | 权利/授权状态 |
| --- | --- | --- |
| `brand/jiaran.png` | `diana-corner-cutout-v2.png` | 原作者/相关权利人所有；分发授权未核实 |
| `brand/candy.png` | `diana-candy-lollipop-v1.png` | 原作者/相关权利人所有；分发授权未核实 |
| `brand/star.png` | `diana-hand-star-reference-v2.png` | 原作者/相关权利人所有；分发授权未核实 |

三图在 `app/src/main/assets/brand/`。主题素材包的来源层级为 `diana-codex-theme / assets/theme-blueprint/assets/diana-brand/derived/`；该名称仅用于追溯，不表示相关品牌或角色权利人认可本项目。
现有阅读/书架角色衬底使用 jiaran.png，另外两张装饰图也保留在素材目录。

本项目不将这些图片声明为原创，不将它们重新许可为项目代码许可，也不提供未经确认的自由再分发授权。
**如有侵权，请通过本仓库 Issues 联系删除。** 请提供作品来源或权属线索，维护者将核实并及时处理。
这是一项联系处理说明，不能替代权利人授权；需要明确授权的使用者应自行向相应权利人确认。

## 原创矢量图与测试内容

应用启动图标位于 `res/drawable/ic_launcher_foreground.xml`，静奢线性图标位于 `ui/theme/AppIcons.kt`，为项目内通过 AI 工具生成和调整的矢量图形；根目录 LICENSE 仅在项目依法有权许可的范围内适用，不表示维护者手工绘制或独占全部输出。
经典图标使用 Compose Material Icons（Apache-2.0），不声明为项目原创。
测试中的短文本、程序生成的 EPUB/DOCX/图片等用于最小回归场景；本公开包不附带个人书库、真实阅读备份或旧截图生成目录。

详细文件哈希见 [assets.json](assets.json)。用户自行导入的书籍和字体不属于仓库授权范围。
