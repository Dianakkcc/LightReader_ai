# 第三方许可全文索引

这些文件取自具体版本的发布归档或上游许可文件，保留原始版权与条款。来源和文件 SHA-256 见 [sources.json](sources.json)；模块到许可文件的映射见 [../docs/dependencies.json](../docs/dependencies.json)。

## 上游原文

| 文件 | 内容 |
| --- | --- |
| [Apache-2.0.txt](Apache-2.0.txt) | Apache 2.0 完整条款 |
| [pdfbox-LICENSE.txt](pdfbox-LICENSE.txt) | PDFBox 上游完整许可，含独立资源条款 |
| [pdfbox-NOTICE.txt](pdfbox-NOTICE.txt) | PDFBox 原 NOTICE |
| [pdfbox-resource-notices.txt](pdfbox-resource-notices.txt) | Adobe 字形表与 Unicode 数据版权头 |
| [noto-OFL.txt](noto-OFL.txt) | Noto Serif OFL 1.1 |
| [NotoSerifSC-copyright.txt](NotoSerifSC-copyright.txt) | Noto 字体版权及精确来源 |
| [liberation-OFL.txt](liberation-OFL.txt) | Liberation 字体版权与 OFL 1.1 |
| [bouncycastle-1.72.txt](bouncycastle-1.72.txt) | Bouncy Castle 1.72 原许可文本（从 HTML 提取正文） |
| [bouncycastle-1.78.1.txt](bouncycastle-1.78.1.txt) | 测试用 Bouncy Castle 1.78.1 |
| [Unicode-2017.txt](Unicode-2017.txt) | 对应年代 Unicode 数据许可 |
| [robolectric-MIT.txt](robolectric-MIT.txt) | Robolectric 原文，含 Apache 部分 |
| [asm-BSD.txt](asm-BSD.txt) | ASM BSD 原文 |
| [ARTWORK-NOTICE.txt](ARTWORK-NOTICE.txt) | 角色素材的来源与未核实授权说明 |
| [RUNTIME-DEPENDENCIES.txt](RUNTIME-DEPENDENCIES.txt) | release 运行时坐标 |
| [RUNTIME-ARCHIVE-NOTICES.txt](RUNTIME-ARCHIVE-NOTICES.txt) | 运行时归档中提取的原始许可/声明 |

`notices/` 保存从 Maven JAR/AAR（含 classes.jar）提取的原 LICENSE/NOTICE；每项坐标对应路径见 JSON。JUnit、Rhino、ICU、javax.annotation 等的上游原文也在此目录。

`asm-BSD.txt` 来自 Maven Central 的 ASM 9.7.1 原始 sources.jar 版权/许可头，与发布 POM 的 BSD-3-Clause 一致。构建与测试依赖的源码包仅用于核对，不随仓库重复分发。

2.9.0新增DataStore重打包protobuf4.28.2的[BSD许可](protobuf-4.28.2-BSD.txt)，具体嵌入模块映射见依赖JSON。
