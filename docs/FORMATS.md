# 增加一种阅读格式

格式层是唯一需要为「新文件类型」改代码的地方，UI 完全不用动。
下面用最近新增的**漫画包（CBZ/ZIP）**当完整范例。

## 一、四步走

1. `core/model/BookFormat.kt` 加枚举项（id、显示名、扩展名、MIME），并声明它属于哪一类：

   ```kotlin
   COMIC(id = "comic", displayName = "漫画", extensions = listOf("cbz", "zip"),
         mimeTypes = listOf("application/vnd.comicbook+zip", "application/zip"))

   val isPaged: Boolean get() = this == PDF || this == IMAGE || this == COMIC
   val isImageBased: Boolean get() = this == IMAGE || this == COMIC
   ```

2. 选择统一文档模型（`core/model/Document.kt`），它决定阅读器怎么渲染：

   | 模型 | 适用 | 阅读器表现 |
   | --- | --- | --- |
   | `ReflowableDocument` | 文字为主，可重排 | WebView，字号/行距/主题可调 |
   | `BitmapPagedDocument` | 每页要自己渲染成位图 | Compose 分页，可缩放 |
   | `ImagePagedDocument` | 一页 = 一个 URI 的图片 | Compose 分页，走图片加载器 |

漫画用 `BitmapPagedDocument`：页面在压缩包里，需要时按目标宽度解码。
注意数据模型仍是一页一张图，但**阅读界面走 `ComicFlowView`**（竖向连续流）：`ReaderViewModel`
把 `isComicFlow` 与 `comicChapterAtPage`（每章的第一页）交给界面，章节在流里表现为分隔标题，
所以漫画不需要"换章"这个动作，`goToTocEntry` 对分页文档直接落页即可。

3. 写 `core/format/<name>/<Name>Document.kt` + `<Name>Parser.kt`：

   - `Document` 负责：打开容器、列目录（`chapters` / `toc`）、按需产出内容、`close()`；
   - `Parser` 只做三件事：`format`、`canHandle()`、`parse()`（把异常包成 `ParseException`）。
   - 需要随机访问（zip / PDF）的格式必须用 `BookWorkspace.materialise()` 拿一份私有缓存副本，
     **不要**直接读用户的原文件，也不要复制到用户目录。

4. 注册到 `ParserFactory.createRegistry()`，并补测试。

5. 决定它默认进哪个书架：`ShelfKind.defaultFor(format)`（漫画包 → 漫画，文字与两可格式 → 书籍）。
   两可的格式不用特殊处理，用户在书架长按就能改归属；归属会自动影响分页文档的阅读方式
   （漫画书架里的分页文档用 `ComicFlowView` 连续流，书籍书架里的用逐页 `PagedReaderView`）。

## 二、漫画包的实现要点（可直接照抄）

- **章节规则**（来自真实资源包的形态）：
  - 根目录全是图 → 一章（书名取自文件名）；
  - 有子文件夹 → 每个文件夹一章，文件夹名即章节名；
  - 忽略 `__MACOSX/`、点文件、`Thumbs.db`。
- **自然排序**：`page2.png` 必须排在 `page10.png` 前面（`naturalOrder()` 比较数字段）。
- **目录映射**：分页文档里 `TocEntry.chapterIndex` 被当作**页码**用，
  所以漫画的 `toc` 要把每章映射到它的第一页（`chapterFirstPage`），否则点目录会跳错。
- **按需解码**：`BitmapFactory.Options.inSampleSize` 折半到覆盖目标宽度，再
  `createScaledBitmap`，用 `RGB_565`；封面单独限流（`MAX_COVER_BYTES`）。
- **线程**：`renderPage` / `open` 都是 `withContext(Dispatchers.IO)`；
  `ZipFile` 读取用 `synchronized(zipLock)` 保护（宿主可能并发预取下一页）。

## 三、测试清单（照抄 `ComicDocumentTest`）

1. 结构：扁平包 = 1 章；多文件夹 = 多章且章节顺序正确（`第1话` → `第2话` → `第10话`）。
2. 顺序：`page1, page2, page10` 的物理顺序与阅读顺序一致（用 `entryNameOf()` 断言）。
3. 渲染：`renderPage(0, 200, 800)` 返回宽度 200、保持比例的位图。
4. 脏数据：混入 `Thumbs.db` 等非图片条目时不影响页数。
5. 损坏包：抛 `ParseException` 而不是崩溃（可加一条损坏 zip 的用例）。

## 四、接入 UI（通常不用改）

- 书架：格式筛选芯片由 `BookFormat.displayName` 自动生成；封面走 `CoverProvider`。
- 阅读器：`BitmapPagedDocument` 走 `ReaderViewModel.loadPageBitmap`，
  非 PDF 的位图文档由文档自己决定页面比例（`renderPage` 的返回值说了算）。
- 「打开方式 / 分享」：在 `AndroidManifest.xml` 的 `VIEW` / `SEND` intent-filter 里加上对应 MIME。
- 单页图片阅读器（`ImageParser`）保持独立：它是一图一书，不是包。

## 五、还没做的格式（后续按同样步骤接）

MOBI / AZW3、FB2、CBR（RAR，需要解压库）、DjVu、以及扫描 PDF 的 OCR。
其中 CBR 建议先做 `RarFile` 抽象，复用本文件的章节规则与解码路径。
