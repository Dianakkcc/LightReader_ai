package com.lightreader.ui.reader

import com.lightreader.core.model.IndentMode
import com.lightreader.core.model.ReaderSettings
import com.lightreader.core.model.TextAlignment
import com.lightreader.core.model.ThemePreference
import com.lightreader.ui.theme.ReaderTheme
import com.lightreader.ui.theme.readerPalette
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.mozilla.javascript.Context
import org.mozilla.javascript.Scriptable

/**
 * Runs the generated reading engine on a fake DOM (Rhino), because the engine is plain ES5.
 *
 * This is what proves the promises that matter on a real device: a page turn lands on an exact
 * page boundary (never half way), the animation is a compositor transform instead of a repaint,
 * a WebView without CSS columns degrades to smooth continuous scrolling, swipes and taps behave,
 * and the end of a chapter asks the host for the next one.
 */
class ReaderEngineTest {

    private val domStub = """
        var timeouts = [];
        var window = this;
        window.innerWidth = 400;
        window.innerHeight = 700;
        window.addEventListener = function () {};
        window.setTimeout = function (fn) { timeouts.push(fn); return timeouts.length; };
        window.clearTimeout = function () {};
        window.requestAnimationFrame = function (fn) { timeouts.push(fn); return 1; };
        window.cancelAnimationFrame = function () {};

        var NodeFilter = { SHOW_TEXT: 4 };

        function Element(width, height) {
          this.clientWidth = width;
          this.clientHeight = height;
          this.scrollLeft = 0;
          this.scrollTop = 0;
          this.offsetHeight = height;
          this.style = {
            transform: '',
            transition: '',
            removeProperty: function () {},
            setProperty: function () {}
          };
          this.classList = {
            add: function () {},
            remove: function () {},
            contains: function () { return false; }
          };
          this._listeners = {};
          this.children = [];
        }
        Element.prototype.addEventListener = function (type, fn) {
          (this._listeners[type] = this._listeners[type] || []).push(fn);
        };
        Element.prototype.removeEventListener = function () {};
        Element.prototype.dispatch = function (type, event) {
          var handlers = this._listeners[type] || [];
          for (var i = 0; i < handlers.length; i++) handlers[i](event);
        };
        Element.prototype.getBoundingClientRect = function () {
          return { left: 0, top: 0, width: this.clientWidth, height: this.clientHeight };
        };
        Element.prototype.querySelectorAll = function () { return []; };
        Element.prototype.scrollBy = function (options) {
          this.scrollTop = Math.max(0, this.scrollTop + options.top);
        };

        var view = new Element(400, 700);
        var content = new Element(400, 700);
        var hint = new Element(200, 40);
        hint.textContent = '';
        // Chapter boundary banners: 76px tall, one pinned to each edge.
        var edgeNext = new Element(400, 76);
        var edgePrev = new Element(400, 76);
        edgeNext.textContent = '';
        edgePrev.textContent = '';

        /** Adds a laid out child so the engine can measure real column positions. */
        function addChild(left, right, top, bottom) {
          var child = new Element(right - left, bottom - top);
          child.getBoundingClientRect = function () {
            return { left: left, top: top, right: right, bottom: bottom,
                     width: right - left, height: bottom - top };
          };
          content.children.push(child);
          return child;
        }

        var fakeText = {
          nodeType: 3,
          nodeValue: '',
          __lrStart: 0,
          parentElement: null,
          parentNode: null
        };

        var document = {
          readyState: 'complete',
          getElementById: function (id) {
            if (id === 'lr-view') return view;
            if (id === 'lr-hint') return hint;
            if (id === 'lr-edge-next') return edgeNext;
            if (id === 'lr-edge-prev') return edgePrev;
            return content;
          },
          addEventListener: function () {},
          caretRangeFromPoint: function () { return { startContainer: fakeText, startOffset: 12 }; },
          createRange: function () {
            return {
              setStart: function () {},
              setEnd: function () {},
              toString: function () { return ''; },
              getBoundingClientRect: function () {
                return { left: 0, top: 0, width: 120, height: 20 };
              }
            };
          },
          createTreeWalker: function () {
            var done = false;
            return {
              nextNode: function () {
                if (done) return null;
                done = true;
                return fakeText;
              }
            };
          },
          createTextNode: function (text) { return { nodeType: 3, nodeValue: text }; }
        };

        function textOf(length) {
          var builder = '';
          for (var i = 0; i < length; i++) builder += '字';
          return builder;
        }

        function pump() {
          for (var round = 0; round < 200; round++) {
            var pending = timeouts;
            timeouts = [];
            for (var i = 0; i < pending.length; i++) pending[i]();
            if (timeouts.length === 0) break;
          }
        }

        function touchEvent(x, y) {
          return {
            touches: [{ clientX: x, clientY: y }],
            changedTouches: [{ clientX: x, clientY: y }],
            preventDefault: function () {}
          };
        }
    """.trimIndent()

    @Test
    fun `page turns land exactly on page boundaries using a transform`() {
        val result = runEngine(
            paged = true,
            supportsColumns = true,
            contentExtent = 400 * 5,
            viewScrollHeight = 700,
            body = """
                var out = [];
                pump();
                out.push('mode=' + LrReader.metrics().mode);
                out.push('pages=' + LrReader.metrics().pages);
                LrReader.nextPage(); out.push('t1=' + LrReader.metrics().transform);
                out.push('page1=' + LrReader.metrics().page);
                LrReader.nextPage(); out.push('page2=' + LrReader.metrics().page);
                LrReader.prevPage(); out.push('pageBack=' + LrReader.metrics().page);
                out.push('scrollTop=' + view.scrollTop);
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals(
            "mode=columns|pages=5|t1=translate3d(-400px, 0px, 0px)|page1=1|page2=2|pageBack=1|scrollTop=0",
            result,
        )
    }

    @Test
    fun `last page is clamped and reports the end of the chapter`() {
        val result = runEngine(
            paged = true,
            supportsColumns = true,
            contentExtent = 400 * 3,
            viewScrollHeight = 700,
            body = """
                var out = [];
                pump();
                for (var i = 0; i < 8; i++) LrReader.nextPage();
                // Let the "one more turn" nudge settle back onto the last page.
                pump();
                out.push('page=' + LrReader.metrics().page);
                out.push('transform=' + LrReader.metrics().transform);
                out.push('endCalls=' + window.__end);
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals("page=2|transform=translate3d(-800px, 0px, 0px)|endCalls=1", result)
    }

    @Test
    fun `a webview without columns degrades to continuous scrolling`() {
        val result = runEngine(
            paged = true,
            supportsColumns = false,
            contentExtent = 400,
            viewScrollHeight = 5000,
            body = """
                var out = [];
                pump();
                out.push('mode=' + LrReader.metrics().mode);
                out.push('overflow=' + view.style.overflowY);
                out.push('transform=' + content.style.transform);
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals("mode=scroll|overflow=auto|transform=none", result)
    }

    @Test
    fun `swipe turns the page and a small drag springs back`() {
        val result = runEngine(
            paged = true,
            supportsColumns = true,
            contentExtent = 400 * 4,
            viewScrollHeight = 700,
            body = """
                var out = [];
                pump();
                view.dispatch('touchstart', touchEvent(320, 300));
                view.dispatch('touchmove', touchEvent(140, 305));
                view.dispatch('touchend', touchEvent(140, 305));
                out.push('forward=' + LrReader.metrics().page);
                view.dispatch('touchstart', touchEvent(120, 300));
                view.dispatch('touchmove', touchEvent(300, 305));
                view.dispatch('touchend', touchEvent(300, 305));
                out.push('back=' + LrReader.metrics().page);
                view.dispatch('touchstart', touchEvent(200, 300));
                view.dispatch('touchmove', touchEvent(190, 301));
                view.dispatch('touchend', touchEvent(190, 301));
                out.push('small=' + LrReader.metrics().page);
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals("forward=1|back=0|small=0", result)
    }

    @Test
    fun `continuous scrolling stays native and never prompts on its own`() {
        val result = runEngine(
            paged = false,
            supportsColumns = false,
            contentExtent = 400,
            viewScrollHeight = 1400,
            body = """
                var out = [];
                pump();
                out.push('mode=' + LrReader.metrics().mode);
                out.push('overflow=' + view.style.overflowY);
                view.scrollTop = 750;
                view.dispatch('scroll', {});
                pump();
                out.push('scrollTop=' + view.scrollTop);
                out.push('endCalls=' + window.__end);
                out.push('prompt=' + (edgeNext.style.opacity || '0'));
                out.push('locations=' + (window.__locations > 0 ? 'yes' : 'no'));
                return out.join('|');
            """.trimIndent(),
        )

        // Reaching the last line must neither change the chapter nor pop anything up: the reader
        // pulls the next chapter label in by hand.
        assertEquals(
            "mode=scroll|overflow=auto|scrollTop=750|endCalls=0|prompt=0|locations=yes",
            result,
        )
    }

    @Test
    fun `pulling the next chapter label completely in continues, a short pull does not`() {
        val result = runEngine(
            paged = false,
            supportsColumns = false,
            contentExtent = 400,
            viewScrollHeight = 1400,
            body = """
                var out = [];
                pump();
                view.scrollTop = 750;
                view.dispatch('scroll', {});
                pump();
                // A small tug only reveals part of the label.
                view.dispatch('touchstart', touchEvent(200, 600));
                view.dispatch('touchmove', touchEvent(200, 560));
                out.push('partial=' + window.__end);
                out.push('partialLabel=' + (edgeNext.textContent.indexOf('下一章') >= 0 ? 'yes' : 'no'));
                var partway = parseFloat(edgeNext.style.opacity || '0');
                out.push('partiallyRevealed=' + (partway > 0 && partway < 1 ? 'yes' : 'no'));
                view.dispatch('touchend', touchEvent(200, 560));
                pump();
                out.push('released=' + window.__end);
                out.push('hidden=' + (parseFloat(edgeNext.style.opacity || '0') === 0 ? 'yes' : 'no'));
                // Pulling the whole 76px label into view is the deliberate confirmation.
                view.dispatch('touchstart', touchEvent(200, 700));
                view.dispatch('touchmove', touchEvent(200, 610));
                pump();
                out.push('full=' + window.__end);
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals(
            "partial=0|partialLabel=yes|partiallyRevealed=yes|released=0|hidden=yes|full=1",
            result,
        )
    }

    @Test
    fun `the chapter handover fades the page out instead of freezing on the reload`() {
        val result = runEngine(
            paged = false,
            supportsColumns = false,
            contentExtent = 400,
            viewScrollHeight = 1400,
            body = """
                var out = [];
                pump();
                view.scrollTop = 750;
                view.dispatch('scroll', {});
                pump();
                view.dispatch('touchstart', touchEvent(200, 700));
                view.dispatch('touchmove', touchEvent(200, 610));
                pump();
                out.push('end=' + window.__end);
                // Switching chapter rebuilds the whole document in the host, which costs a few
                // frames. The page has to fade out to cover that reload, and the fade must be the
                // only animation left (the old code also animated a transform that nobody saw).
                out.push('fade=' + (content.style.opacity === '0' ? 'yes' : 'no'));
                out.push('fadeOnly=' + (content.style.transition.indexOf('opacity') >= 0 ? 'yes' : 'no'));
                out.push('noTransform=' + (content.style.transition.indexOf('transform') < 0 ? 'yes' : 'no'));
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals("end=1|fade=yes|fadeOnly=yes|noTransform=yes", result)
    }

    @Test
    fun `pulling down at the top of a chapter goes back to the previous one`() {
        val result = runEngine(
            paged = false,
            supportsColumns = false,
            contentExtent = 400,
            viewScrollHeight = 1400,
            body = """
                var out = [];
                pump();
                view.scrollTop = 0;
                view.dispatch('touchstart', touchEvent(200, 500));
                view.dispatch('touchmove', touchEvent(200, 540));
                out.push('partial=' + window.__start);
                out.push('partialLabel=' + (edgePrev.textContent.indexOf('上一章') >= 0 ? 'yes' : 'no'));
                var partway = parseFloat(edgePrev.style.opacity || '0');
                out.push('partiallyRevealed=' + (partway > 0 && partway < 1 ? 'yes' : 'no'));
                view.dispatch('touchend', touchEvent(200, 540));
                pump();
                out.push('released=' + window.__start);
                view.dispatch('touchstart', touchEvent(200, 400));
                view.dispatch('touchmove', touchEvent(200, 500));
                pump();
                out.push('full=' + window.__start);
                return out.join('|');
            """.trimIndent(),
            previousChapterTitle = "上一章 标题",
        )

        assertEquals(
            "partial=0|partialLabel=yes|partiallyRevealed=yes|released=0|full=1",
            result,
        )
    }

    @Test
    fun `turning past the last page asks once before it continues`() {
        val result = runEngine(
            paged = true,
            supportsColumns = true,
            contentExtent = 400,
            viewScrollHeight = 700,
            body = """
                var out = [];
                pump();
                out.push('pages=' + LrReader.metrics().pages);
                LrReader.nextPage();
                pump();
                out.push('first=' + window.__end);
                out.push('prompt=' + (edgeNext.textContent.indexOf('下一章') >= 0 ? 'yes' : 'no'));
                out.push('page=' + LrReader.metrics().page);
                LrReader.nextPage();
                pump();
                out.push('second=' + window.__end);
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals("pages=1|first=0|prompt=yes|page=0|second=1", result)
    }

    @Test
    fun `locations are reported through the bridge`() {
        val result = runEngine(
            paged = true,
            supportsColumns = true,
            contentExtent = 400 * 3,
            viewScrollHeight = 700,
            body = """
                var out = [];
                pump();
                out.push('ready=' + window.__ready);
                out.push('offset=' + window.__lastOffset);
                out.push('chapter=' + window.__lastChapter);
                out.push('fraction=' + (window.__lastFraction > 0 ? 'positive' : 'zero'));
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals("ready=1|offset=12|chapter=3|fraction=positive", result)
    }

    @Test
    fun `pulling past the bottom continues with the next chapter`() {
        val result = runEngine(
            paged = false,
            supportsColumns = false,
            contentExtent = 400,
            viewScrollHeight = 1400,
            body = """
                var out = [];
                pump();
                out.push('mode=' + LrReader.metrics().mode);
                // Small pull: hint shows but the chapter must not change.
                view.scrollTop = 700;
                view.dispatch('touchstart', touchEvent(200, 600));
                view.dispatch('touchmove', touchEvent(200, 580));
                view.dispatch('touchend', touchEvent(200, 580));
                pump();
                out.push('smallPull=' + window.__end);
                // Deliberate pull: the host is asked for the next chapter exactly once.
                view.dispatch('touchstart', touchEvent(200, 600));
                view.dispatch('touchmove', touchEvent(200, 460));
                view.dispatch('touchend', touchEvent(200, 460));
                pump();
                out.push('pull=' + window.__end);
                out.push('reset=' + (view.scrollTop === 700 ? 'yes' : 'no'));
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals("mode=scroll|smallPull=0|pull=1|reset=yes", result)
    }

    @Test
    fun `cancelled gesture snaps back to the current page`() {
        val result = runEngine(
            paged = true,
            supportsColumns = true,
            contentExtent = 400 * 4,
            viewScrollHeight = 700,
            body = """
                var out = [];
                pump();
                LrReader.nextPage();
                var onPage = LrReader.metrics().transform;
                view.dispatch('touchstart', touchEvent(50, 300));
                view.dispatch('touchmove', touchEvent(200, 300));
                var mid = LrReader.metrics().transform;
                LrReader.cancelGesture();
                out.push('mid=' + (mid !== onPage ? 'dragged' : 'same'));
                out.push('after=' + (LrReader.metrics().transform === onPage ? 'snapped' : 'off'));
                out.push('page=' + LrReader.metrics().page);
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals("mid=dragged|after=snapped|page=1", result)
    }

    @Test
    fun `two page mode lays the text out in two columns per screen`() {
        val result = runEngine(
            paged = true,
            supportsColumns = true,
            contentExtent = 800 * 4,
            viewScrollHeight = 900,
            viewWidth = 800,
            viewHeight = 900,
            twoUp = true,
            body = """
                var out = [];
                pump();
                out.push('pages=' + LrReader.metrics().pages);
                out.push('stride=' + LrReader.metrics().stride);
                LrReader.nextPage();
                out.push('transform=' + LrReader.metrics().transform);
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals("pages=4|stride=800|transform=translate3d(-800px, 0px, 0px)", result)
    }

    @Test
    fun `page stride follows the measured column layout, not the requested one`() {
        // The browser stretched the columns (440 instead of the requested 400). Paging must follow
        // what the layout actually did, otherwise every page drifts a little further out of place.
        val result = runEngine(
            paged = true,
            supportsColumns = true,
            contentExtent = 1220,
            viewScrollHeight = 700,
            childrenJs = """
                addChild(0, 340, 0, 700);
                addChild(440, 780, 0, 700);
                addChild(880, 1220, 0, 700);
            """.trimIndent(),
            body = """
                var out = [];
                pump();
                out.push('pages=' + LrReader.metrics().pages);
                out.push('stride=' + LrReader.metrics().stride);
                LrReader.nextPage();
                out.push('p1=' + LrReader.metrics().transform);
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals(
            "pages=3|stride=440|p1=translate3d(-440px, 0px, 0px)",
            result,
        )
    }

    @Test
    fun `a chapter that fits on one screen still needs the deliberate pull`() {
        val result = runEngine(
            paged = false,
            supportsColumns = false,
            contentExtent = 400,
            viewScrollHeight = 700, // nothing to scroll: a cover page or a short chapter
            body = """
                var out = [];
                pump();
                out.push('prompt=' + (edgeNext.style.opacity || '0'));
                view.dispatch('touchstart', touchEvent(200, 600));
                view.dispatch('touchmove', touchEvent(200, 560));
                out.push('early=' + window.__end);
                view.dispatch('touchmove', touchEvent(200, 500));
                out.push('afterPull=' + window.__end);
                return out.join('|');
            """.trimIndent(),
        )

        assertEquals("prompt=0|early=0|afterPull=1", result)
    }

    @Test
    fun `reading stylesheet uses the user settings`() {
        val css = ReaderCss.build(
            settings = ReaderSettings(
                fontSizeSp = 22f,
                lineHeightMultiplier = 2f,
                paragraphSpacingEm = 0.8f,
                marginDp = 24,
                alignment = TextAlignment.JUSTIFY,
                indentMode = IndentMode.ON,
                useSerif = true,
                theme = ThemePreference.PAPER,
            ),
            palette = readerPalette(ReaderTheme.PAPER),
            paged = true,
            fontScale = 1f,
            bookCss = null,
            horizontalPaddingPx = 24,
        )

        assertTrue("字号应生效", css.contains("font-size: 22.00px"))
        assertTrue("行距应生效", css.contains("line-height: 2.00"))
        assertTrue("首行缩进应生效", css.contains("text-indent: 2em"))
        assertTrue("两端对齐应生效", css.contains("text-align: justify"))
        assertTrue("衬线字体应生效", css.contains("Noto Serif CJK SC"))
        assertFalse("不应出现未替换的占位符", css.contains("$"))
    }

    @Test
    fun `system font scale is applied to the reading stylesheet`() {
        val css = ReaderCss.build(
            settings = ReaderSettings(fontSizeSp = 20f),
            palette = readerPalette(ReaderTheme.NIGHT),
            paged = false,
            fontScale = 1.3f,
            bookCss = null,
            horizontalPaddingPx = 20,
        )
        assertTrue("无障碍字号缩放应生效", css.contains("font-size: 26.00px"))
    }

    private fun runEngine(
        paged: Boolean,
        supportsColumns: Boolean,
        contentExtent: Int,
        viewScrollHeight: Int,
        body: String,
        viewWidth: Int = 400,
        viewHeight: Int = 700,
        twoUp: Boolean = false,
        childrenJs: String = "",
        previousChapterTitle: String? = "上一章",
    ): String {
        val context = Context.enter()
        try {
            context.optimizationLevel = -1
            val scope: Scriptable = context.initStandardObjects()
            context.evaluateString(scope, domStub, "dom.js", 1, null)
            val columnWidth = if (supportsColumns) "'360px'" else "'auto'"
            context.evaluateString(
                scope,
                """
                view.clientWidth = $viewWidth;
                view.clientHeight = $viewHeight;
                view.scrollHeight = $viewScrollHeight;
                view.scrollWidth = $contentExtent;
                content.clientWidth = $viewWidth;
                content.clientHeight = $viewScrollHeight;
                content.scrollWidth = $contentExtent;
                content.scrollHeight = $viewScrollHeight;
                fakeText.nodeValue = textOf(2000);
                window.getComputedStyle = function () {
                  return { columnWidth: $columnWidth, columnCount: 'auto' };
                };
                window.__ready = 0;
                window.__end = 0;
                window.__start = 0;
                window.__locations = 0;
                window.__lastOffset = -1;
                window.__lastChapter = -1;
                window.__lastFraction = 0;
                $childrenJs
                window.LrBridge = {
                  onLocation: function (chapter, offset, fraction) {
                    window.__locations++;
                    window.__lastOffset = offset;
                    window.__lastChapter = chapter;
                    window.__lastFraction = fraction;
                  },
                  onReady: function () { window.__ready++; },
                  onReachEnd: function () { window.__end++; },
                  onReachStart: function () { window.__start++; }
                };
                """.trimIndent(),
                "setup.js",
                1,
                null,
            )
            context.evaluateString(
                scope,
                ReaderScript.build(
                    chapterIndex = 3,
                    paged = paged,
                    initialOffset = 0,
                    horizontalPaddingPx = 20,
                    verticalPaddingPx = 24,
                    nextChapterTitle = "下一章",
                    previousChapterTitle = previousChapterTitle,
                    twoUp = twoUp,
                ),
                "reader.js",
                1,
                null,
            )
            val value = context.evaluateString(
                scope,
                "(function () { $body })()",
                "assertions.js",
                1,
                null,
            )
            return Context.toString(value)
        } finally {
            Context.exit()
        }
    }
}
