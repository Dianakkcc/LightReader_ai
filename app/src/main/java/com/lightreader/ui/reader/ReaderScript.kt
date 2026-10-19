package com.lightreader.ui.reader

/**
 * The reading engine running inside the WebView.
 *
 * Rules that came out of real device feedback:
 *
 * * **Geometry is measured in fractional CSS pixels** (`getBoundingClientRect`), never in the
 *   rounded `clientWidth`. A rounded page width drifts by a fraction of a pixel per turn, which is
 *   exactly the "page does not fit after turning" artefact on high density screens.
 * * **The page count comes from the content itself** - the union of the element rects - instead of
 *   `scrollWidth`, which reports a trailing column gap and would create a phantom blank page.
 * * **Page turns move the content with `transform`**, so a page can never stop half way and the
 *   animation runs on the compositor.
 * * **Continuous scrolling uses the platform scroller**, with margins applied to the content (the
 *   reader surface is fixed positioned, so body padding would have no effect at all), and pulling
 *   past the bottom asks the host for the next chapter.
 */
object ReaderScript {

    fun build(
        chapterIndex: Int,
        paged: Boolean,
        initialOffset: Int,
        horizontalPaddingPx: Int,
        verticalPaddingPx: Int,
        nextChapterTitle: String?,
        previousChapterTitle: String?,
        twoUp: Boolean,
    ): String = """
        (function () {
          'use strict';
          /**
           * Stage reporting for the host log. A reading surface that never reports "ready" is
           * invisible from the outside, so every bootstrap step says where it got to.
           */
          function diag(stage, extra) {
            try {
              // Kept on the page as well: the host can read it back through the evaluation
              // channel even when the JavaScript bridge itself is not reachable.
              window.__lrDiag = window.__lrDiag || [];
              window.__lrDiag.push(stage + (extra === undefined || extra === null ? '' : ':' + extra));
            } catch (error) {
              // Diagnostics must never break reading.
            }
            if (!window.LrBridge || !LrBridge.onMetrics) {
              try {
                window.__lrDiag.push('no-bridge');
              } catch (innerError) {
                // ignore
              }
              return;
            }
            try {
              LrBridge.onMetrics(JSON.stringify({
                stage: stage,
                ready: ready,
                w: Math.round(viewportWidth()),
                h: Math.round(viewportHeight()),
                state: document.readyState,
                extra: extra === undefined || extra === null ? null : String(extra)
              }));
            } catch (error) {
              try {
                window.__lrDiag.push('bridge-error:' + error);
              } catch (innerError) {
                // ignore
              }
            }
          }
          window.addEventListener('error', function (event) {
            diag('script-error', event.message + ' @' + event.lineno + ':' + event.colno);
          });
          var REQUESTED_PAGED = $paged;
          var CHAPTER = $chapterIndex;
          var H_PAD = $horizontalPaddingPx;
          var V_PAD = $verticalPaddingPx;
          var PULL_THRESHOLD = 72;
          /** How close to the end still counts as "at the end"; fractional layout needs slack. */
          var BOTTOM_SLACK = 8;
          var NEXT_TITLE = '${escapeJs(nextChapterTitle.orEmpty())}';
          var PREV_TITLE = '${escapeJs(previousChapterTitle.orEmpty())}';
          var TWO_UP = $twoUp;

          var view = document.getElementById('lr-view');
          var content = document.getElementById('lr-content');
          /** Chapter labels that slide in from the edges while the reader pulls past a boundary. */
          var edgeNext = document.getElementById('lr-edge-next');
          var edgePrev = document.getElementById('lr-edge-prev');

          var paged = REQUESTED_PAGED;
          var columnMode = false;
          /** Distance between two adjacent columns, measured from the real layout. */
          var columnStride = 1;
          /** Distance between two pages = columns per page x column stride. */
          var stride = 1;
          var columnsPerPage = 1;
          var requestedColumnWidth = 0;
          var requestedGap = 0;
          var pages = 1;
          var page = 0;
          var pendingOffset = $initialOffset;
          var lastSentOffset = -1;
          var endReported = false;
          var startReported = false;
          var textIndex = [];
          var totalChars = 1;
          var cachedOffset = 0;
          var ready = false;
          var hasNext = NEXT_TITLE.length > 0;
          var hasPrev = PREV_TITLE.length > 0;
          var shortChapter = false;
          var pullThreshold = PULL_THRESHOLD;
          var settleTimer = null;
          var gesture = null;
          var scrollGesture = null;
          var pull = 0;
          var pulling = false;
          /**
           * When the reader turns past the last page this holds the time of the prompt. The first
           * attempt only *asks* ("one more turn to continue"); the chapter changes on the second,
           * deliberate turn. Reaching the end of a chapter must never yank the reader out of the
           * text on its own - that is what made the reader unusable.
           */
          var endPromptAt = 0;
          var startPromptAt = 0;
          var END_PROMPT_WINDOW = 6000;
          /** How far the finger has to reveal the edge label before the chapter changes. */
          var EDGE_REVEAL = 1;

          function viewportWidth() {
            var rect = view.getBoundingClientRect();
            return Math.max(1, rect.width || view.clientWidth);
          }

          function viewportHeight() {
            var rect = view.getBoundingClientRect();
            return Math.max(1, rect.height || view.clientHeight);
          }

          function layoutReady() {
            return viewportWidth() > 40 && viewportHeight() > 40;
          }

          // ------------------------------------------------------------ text index

          function buildTextIndex() {
            textIndex = [];
            var walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT, null);
            var cursor = 0;
            var node = walker.nextNode();
            while (node) {
              var value = node.nodeValue ? node.nodeValue : '';
              // Whitespace between block elements is not a position a reader can be put back to:
              // indexing it made the engine "restore" onto the blank line after a full page
              // picture, which is a page further on - the chapter looked like it had jumped.
              var length = value.replace(/\s+/g, '').length > 0 ? value.length : 0;
              if (length > 0) {
                node.__lrStart = cursor;
                textIndex.push({ node: node, start: cursor, end: cursor + length });
                cursor += length;
              } else {
                node.__lrStart = undefined;
              }
              node = walker.nextNode();
            }
            totalChars = Math.max(1, cursor);
          }

          function nodeForOffset(offset) {
            var low = 0;
            var high = textIndex.length - 1;
            while (low <= high) {
              var middle = (low + high) >> 1;
              var entry = textIndex[middle];
              if (offset < entry.start) high = middle - 1;
              else if (offset > entry.end) low = middle + 1;
              else return entry;
            }
            return high >= 0 ? textIndex[high] : null;
          }

          // ------------------------------------------------------------ layout

          function baseViewStyles() {
            view.style.position = 'fixed';
            view.style.top = '0';
            view.style.left = '0';
            view.style.right = '0';
            view.style.bottom = '0';
          }

          function naturalMode() {
            baseViewStyles();
            view.style.overflowY = 'auto';
            view.style.overflowX = 'hidden';
            view.style.webkitOverflowScrolling = 'touch';
            content.style.transition = 'none';
            content.style.transform = 'none';
            content.classList.remove('lr-turning');
            content.style.removeProperty('--lr-page-h');
            content.style.removeProperty('column-width');
            content.style.removeProperty('column-gap');
            content.style.removeProperty('height');
            content.style.removeProperty('margin-top');
            // The surface is fixed positioned, so the side margins have to live on the content.
            content.style.paddingLeft = H_PAD + 'px';
            content.style.paddingRight = H_PAD + 'px';
            content.style.paddingTop = V_PAD + 'px';
            content.style.paddingBottom = V_PAD + 'px';
            paged = false;
            columnMode = false;
            stride = viewportHeight();
          }

          function applyColumnLayout() {
            baseViewStyles();
            view.style.overflowY = 'hidden';
            view.style.overflowX = 'hidden';
            content.style.transition = 'none';

            var width = viewportWidth();
            var height = viewportHeight();
            // Side margins are container padding, not column geometry: Chromium makes the columns
            // fill the whole content box, so padding is the only way to inset the text in paged
            // mode. The column gap then separates the columns inside that inset area.
            var innerGap = H_PAD * 2;
            columnsPerPage = (TWO_UP && width >= 560) ? 2 : 1;
            content.style.paddingLeft = H_PAD + 'px';
            content.style.paddingRight = H_PAD + 'px';
            var available = Math.max(80, width - H_PAD * 2);
            var columnWidth = columnsPerPage === 2
              ? Math.max(60, (available - innerGap) / 2)
              : available;
            requestedColumnWidth = columnWidth;
            requestedGap = innerGap;

            content.style.setProperty('--lr-page-h', Math.max(1, height - V_PAD * 2) + 'px');
            content.style.paddingTop = '0px';
            content.style.paddingBottom = '0px';
            content.style.height = Math.max(1, height - V_PAD * 2) + 'px';
            content.style.marginTop = V_PAD + 'px';
            content.style.columnWidth = columnWidth + 'px';
            content.style.columnGap = innerGap + 'px';
            content.style.columnFill = 'auto';
            content.style.transform = 'translate3d(0px, 0px, 0px)';
          }

          /**
           * Reads the column geometry back from the DOM instead of trusting the requested values:
           * a browser may stretch the columns so that they fill the container exactly, which makes
           * the used column width differ from the requested one. Deriving the page stride from the
           * measurement is what keeps every page aligned on every screen ratio.
           */
          function measureColumns() {
            var rect = content.getBoundingClientRect();
            var lefts = [];
            var children = content.children;
            for (var i = 0; i < children.length && lefts.length < 4; i++) {
              var childRect = children[i].getBoundingClientRect();
              if (childRect.width <= 0 && childRect.height <= 0) continue;
              var left = Math.round((childRect.left - rect.left) * 100) / 100;
              var known = false;
              for (var j = 0; j < lefts.length; j++) {
                if (Math.abs(lefts[j] - left) < 1) known = true;
              }
              if (!known) lefts.push(left);
            }
            lefts.sort(function (a, b) { return a - b; });
            columnStride = (lefts.length >= 2 && lefts[1] - lefts[0] > 20)
              ? lefts[1] - lefts[0]
              : requestedColumnWidth + requestedGap;
            stride = Math.max(1, columnsPerPage * columnStride);
          }

          /** Page count from measured column indexes rather than an estimated width. */
          function measurePages() {
            var rect = content.getBoundingClientRect();
            var maxLeft = 0;
            var maxRight = 0;
            var children = content.children;
            for (var i = 0; i < children.length; i++) {
              var childRect = children[i].getBoundingClientRect();
              if (childRect.width <= 0 && childRect.height <= 0) continue;
              var left = childRect.left - rect.left;
              var right = childRect.right - rect.left;
              if (left > maxLeft) maxLeft = left;
              if (right > maxRight) maxRight = right;
            }
            if (maxRight <= 0) maxRight = content.scrollWidth;
            var lastColumnByLeft = Math.round(maxLeft / columnStride) + 1;
            var lastColumnByRight = Math.ceil(maxRight / columnStride);
            var totalColumns = Math.max(1, Math.max(lastColumnByLeft, lastColumnByRight));
            pages = Math.max(1, Math.ceil(totalColumns / columnsPerPage));
          }

          /**
           * Right (paged) or bottom (scrolling) edge of the content, measured from the union of
           * the element rects. A fragmented paragraph reports the union of its fragments, so the
           * last column that actually holds text is what we count.
           */
          function contentExtent() {
            var rect = content.getBoundingClientRect();
            var maxRight = 0;
            var maxBottom = 0;
            var children = content.children;
            for (var i = 0; i < children.length; i++) {
              var childRect = children[i].getBoundingClientRect();
              if (childRect.width <= 0 && childRect.height <= 0) continue;
              var right = childRect.right - rect.left;
              var bottom = childRect.bottom - rect.top;
              if (right > maxRight) maxRight = right;
              if (bottom > maxBottom) maxBottom = bottom;
            }
            if (maxRight <= 0) maxRight = content.scrollWidth;
            if (maxBottom <= 0) maxBottom = content.scrollHeight;
            var viewOverflow = view.scrollWidth;
            if (viewOverflow > maxRight) maxRight = viewOverflow;
            return paged ? maxRight : maxBottom;
          }

          function measure() {
            if (!layoutReady()) return false;
            if (!REQUESTED_PAGED) {
              naturalMode();
              shortChapter = view.scrollHeight <= viewportHeight() + 4;
              // Kept for the metrics: a short chapter is one that simply cannot scroll. There is no
              // passive prompt any more - the reader pulls the chapter label in by hand.
              pullThreshold = shortChapter ? 40 : 52;
              ready = true;
              return true;
            }
            paged = true;
            applyColumnLayout();
            measureColumns();
            var extent = contentExtent();
            var computed = window.getComputedStyle ? window.getComputedStyle(content) : null;
            var declared = computed
              ? ((computed.columnWidth && computed.columnWidth !== 'auto') ||
                 (computed.columnCount && computed.columnCount !== 'auto'))
              : false;
            var columns = declared || extent > viewportWidth() * 1.05;
            if (!columns) {
              // A WebView that will not build columns is not worth fighting: read continuously.
              naturalMode();
              ready = true;
              return true;
            }
            columnMode = true;
            paged = true;
            measurePages();
            ready = true;
            page = Math.max(0, Math.min(pages - 1, page));
            applyPosition(pageTop(page), false);
            return true;
          }

          function applyPosition(pixels, animate) {
            if (!paged) {
              view.scrollTop = pixels;
              return;
            }
            // The composited layer is only promoted while the page is actually moving: a chapter
            // laid out in columns can be several screens wide, and keeping that as a permanent
            // GPU texture is what makes phones stutter.
            if (animate) {
              content.classList.add('lr-turning');
              content.style.transition = 'transform 260ms cubic-bezier(0.22, 0.61, 0.36, 1)';
            } else {
              content.classList.remove('lr-turning');
              content.style.transition = 'none';
            }
            content.style.transform = 'translate3d(' + (-Math.round(pixels * 1000) / 1000) + 'px, 0px, 0px)';
          }

          function pageTop(index) {
            return Math.max(0, Math.min(pages - 1, index)) * stride;
          }

          function goToPage(index, animate) {
            page = Math.max(0, Math.min(pages - 1, index));
            applyPosition(pageTop(page), animate !== false);
            if (animate !== false) scheduleSettle();
            report();
          }

          function scheduleSettle() {
            if (settleTimer) window.clearTimeout(settleTimer);
            settleTimer = window.setTimeout(function () {
              settleTimer = null;
              content.classList.remove('lr-turning');
              if (paged) applyPosition(pageTop(page), false);
            }, 330);
          }

          function snapBack() {
            content.style.transition = 'transform 200ms ease-out';
            content.style.transform = paged ? 'translate3d(0px, 0px, 0px)' : 'none';
            if (paged) applyPosition(pageTop(page), false);
          }

          // ------------------------------------------------------------ position

          function currentOffset() {
            var x = Math.min(H_PAD + 6, viewportWidth() - 2);
            var y = Math.min(V_PAD + 6, viewportHeight() - 2);
            var range = null;
            try {
              range = document.caretRangeFromPoint(x, y);
            } catch (error) {
              range = null;
            }
            if (!range || !range.startContainer) return cachedOffset;
            var node = range.startContainer;
            if (node.nodeType !== 3) return cachedOffset;
            var start = typeof node.__lrStart === 'number' ? node.__lrStart : 0;
            cachedOffset = start + range.startOffset;
            return cachedOffset;
          }

          function pixelsForOffset(offset) {
            if (!offset || offset <= 0) return 0;
            var entry = nodeForOffset(offset);
            if (!entry) return 0;
            var localOffset = Math.max(0, Math.min(entry.end - entry.start, offset - entry.start));
            var range = document.createRange();
            range.setStart(entry.node, localOffset);
            range.setEnd(entry.node, localOffset);
            var rect = range.getBoundingClientRect();
            if (rect.width === 0 && rect.height === 0 && entry.node.parentElement) {
              rect = entry.node.parentElement.getBoundingClientRect();
            }
            if (paged) {
              var contentLeft = content.getBoundingClientRect().left;
              return Math.max(0, rect.left - contentLeft);
            }
            var contentTop = content.getBoundingClientRect().top;
            return Math.max(0, rect.top - contentTop + view.scrollTop - V_PAD);
          }

          function report() {
            if (!ready) return;
            var offset = currentOffset();
            if (Math.abs(offset - lastSentOffset) < 6 && lastSentOffset >= 0) return;
            lastSentOffset = offset;
            if (window.LrBridge) {
              var fraction = Math.max(0, Math.min(1, offset / totalChars));
              LrBridge.onLocation(CHAPTER, offset, fraction, pages, page);
            }
          }

          function reportEnd() {
            // The host owns the chapter list, so the engine reports the end even when it does not
            // know about a next chapter (its knowledge can lag behind a background index).
            if (endReported) return;
            endReported = true;
            if (!window.LrBridge) return;
            var state = '';
            try {
              state = JSON.stringify({
                mode: paged ? 'paged' : 'scroll',
                page: page,
                pages: pages,
                offset: currentOffset(),
                chars: totalChars,
                scrollTop: Math.round(view.scrollTop),
                scrollHeight: view.scrollHeight,
                clientHeight: view.clientHeight,
                short: shortChapter,
                ready: ready
              });
            } catch (error) {
              state = 'n/a';
            }
            LrBridge.onReachEnd(state);
          }

          /** Mirror of [reportEnd]: the reader pulled backwards past the beginning of the chapter. */
          function reportStart() {
            if (startReported) return;
            startReported = true;
            if (!window.LrBridge) return;
            var state = '';
            try {
              state = JSON.stringify({
                mode: paged ? 'paged' : 'scroll',
                page: page,
                pages: pages,
                offset: currentOffset(),
                chars: totalChars,
                scrollTop: Math.round(view.scrollTop),
                scrollHeight: view.scrollHeight,
                clientHeight: view.clientHeight,
                ready: ready
              });
            } catch (error) {
              state = 'n/a';
            }
            LrBridge.onReachStart(state);
          }

          // ------------------------------------------------------------ chapter edges

          /**
           * Both chapter boundaries work the same way: the label of the chapter that is coming
           * slides in from the edge the reader is pulling towards, and the chapter only changes
           * once that label is *completely* revealed - a deliberate, visible gesture instead of a
           * popup prompt.
           */
          function edgeHeight(edge) {
            var height = edge ? edge.offsetHeight : 0;
            return height > 0 ? height : 84;
          }

          function edgeProgress(amount, edge) {
            return Math.max(0, Math.min(1, amount / edgeHeight(edge)));
          }

          /** [progress] 0 hides the banner behind its edge, 1 has it fully on screen. */
          function applyEdge(edge, progress) {
            if (!edge) return;
            var clamped = Math.max(0, Math.min(1, progress || 0));
            edge.style.opacity = String(clamped <= 0 ? 0 : Math.min(1, clamped * 1.35));
            edge.style.transform = 'translate3d(0, ' + ((1 - clamped) * 100) + '%, 0)';
          }

          function hideEdges() {
            applyEdge(edgeNext, 0);
            applyEdge(edgePrev, 0);
          }

          /**
           * Hands the chapter over. The host answers by rebuilding the page and reloading the
           * document, which costs a few frames; fading the page out over ~110ms hides that reload
           * instead of letting the reader see a freeze. The new page fades back in through the
           * `lr-enter` animation in the stylesheet.
           */
          function handOver(edge, report) {
            gesture = null;
            scrollGesture = null;
            pulling = false;
            pull = 0;
            endPromptAt = 0;
            startPromptAt = 0;
            applyEdge(edgeNext, edge === edgePrev ? 0 : 1);
            applyEdge(edgePrev, edge === edgePrev ? 1 : 0);
            content.classList.remove('lr-turning');
            content.style.transition = 'opacity 110ms linear';
            content.style.opacity = '0';
            report();
          }

          function reportEdge(stage, progress) {
            diag(stage, 'p=' + Math.round((progress || 0) * 100) / 100 + ' h=' + edgeHeight(edgeNext));
          }

          /**
           * The reader tried to turn past the last page. First attempt: the next chapter label
           * slides in and stays for a moment. Second attempt within [END_PROMPT_WINDOW]: hand over.
           */
          function promptContinue() {
            var now = Date.now();
            if (endPromptAt > 0 && now - endPromptAt < END_PROMPT_WINDOW) {
              handOver(edgeNext, reportEnd);
              return;
            }
            endPromptAt = now;
            applyEdge(edgeNext, 1);
            reportEdge('prompt-next', 1);
            if (stride > 0) {
              var target = -(Math.round((pageTop(page) + stride * 0.08) * 100) / 100);
              content.classList.add('lr-turning');
              content.style.transition = 'transform 220ms cubic-bezier(0.22, 0.61, 0.36, 1)';
              content.style.transform = 'translate3d(' + target + 'px, 0px, 0px)';
              window.setTimeout(function () {
                if (endPromptAt > 0) applyPosition(pageTop(page), true);
              }, 240);
            }
            window.setTimeout(function () {
              if (endPromptAt > 0 && Date.now() - endPromptAt >= END_PROMPT_WINDOW) {
                endPromptAt = 0;
                hideEdges();
              }
            }, END_PROMPT_WINDOW + 80);
          }

          /** Same idea at the other end: turning back past the first page offers the previous chapter. */
          function promptPrevious() {
            var now = Date.now();
            if (startPromptAt > 0 && now - startPromptAt < END_PROMPT_WINDOW) {
              handOver(edgePrev, reportStart);
              return;
            }
            startPromptAt = now;
            applyEdge(edgePrev, 1);
            reportEdge('prompt-previous', 1);
            if (stride > 0) {
              var target = Math.round((pageTop(page) - stride * 0.08) * 100) / 100;
              content.classList.add('lr-turning');
              content.style.transition = 'transform 220ms cubic-bezier(0.22, 0.61, 0.36, 1)';
              content.style.transform = 'translate3d(' + target + 'px, 0px, 0px)';
              window.setTimeout(function () {
                if (startPromptAt > 0) applyPosition(pageTop(page), true);
              }, 240);
            }
            window.setTimeout(function () {
              if (startPromptAt > 0 && Date.now() - startPromptAt >= END_PROMPT_WINDOW) {
                startPromptAt = 0;
                hideEdges();
              }
            }, END_PROMPT_WINDOW + 80);
          }

          // ------------------------------------------------------------ navigation

          function nextPage() {
            if (!ready) return;
            if (!paged) {
              view.scrollBy({ top: viewportHeight() * 0.92, behavior: 'smooth' });
              return;
            }
            if (page >= pages - 1) {
              // Turning past the last page asks first (see promptContinue).
              promptContinue();
              return;
            }
            goToPage(page + 1, true);
          }

          function prevPage() {
            if (!ready) return;
            if (!paged) {
              view.scrollBy({ top: -viewportHeight() * 0.92, behavior: 'smooth' });
              return;
            }
            if (page <= 0) {
              // Backwards past the first page: offer the previous chapter (same two step rule).
              if (hasPrev) promptPrevious();
              return;
            }
            goToPage(page - 1, true);
          }

          function goToOffset(offset) {
            if (!ready) { pendingOffset = offset; return; }
            var pixels = pixelsForOffset(offset);
            if (paged) {
              page = Math.max(0, Math.min(pages - 1, Math.floor(pixels / stride)));
              applyPosition(pageTop(page), false);
            } else {
              view.scrollTop = pixels;
            }
            lastSentOffset = -1;
            report();
          }

          function scrollToId(id) {
            var element = document.getElementById(id);
            if (!element) return false;
            var rect = element.getBoundingClientRect();
            if (paged) {
              var contentLeft = content.getBoundingClientRect().left;
              var pixels = Math.max(0, rect.left - contentLeft);
              page = Math.max(0, Math.min(pages - 1, Math.floor(pixels / stride)));
              applyPosition(pageTop(page), false);
            } else {
              var contentTop = content.getBoundingClientRect().top;
              view.scrollTop = Math.max(0, rect.top - contentTop + view.scrollTop - V_PAD);
            }
            lastSentOffset = -1;
            report();
            return true;
          }

          function highlight(query) {
            var marks = content.querySelectorAll('mark.lr-hit');
            for (var i = 0; i < marks.length; i++) {
              var mark = marks[i];
              var parent = mark.parentNode;
              parent.replaceChild(document.createTextNode(mark.textContent), mark);
              parent.normalize();
            }
            if (!query) { buildTextIndex(); return 0; }
            var needle = query.toLowerCase();
            var walker = document.createTreeWalker(content, NodeFilter.SHOW_TEXT, null);
            var nodes = [];
            var node = walker.nextNode();
            while (node) { nodes.push(node); node = walker.nextNode(); }
            var hits = 0;
            for (var index = 0; index < nodes.length; index++) {
              var textNode = nodes[index];
              var value = textNode.nodeValue || '';
              var lower = value.toLowerCase();
              var found = lower.indexOf(needle);
              if (found < 0) continue;
              var fragment = document.createDocumentFragment();
              var cursor = 0;
              while (found >= 0) {
                if (found > cursor) {
                  fragment.appendChild(document.createTextNode(value.substring(cursor, found)));
                }
                var markElement = document.createElement('mark');
                markElement.className = 'lr-hit';
                markElement.textContent = value.substr(found, needle.length);
                fragment.appendChild(markElement);
                cursor = found + needle.length;
                hits++;
                found = lower.indexOf(needle, cursor);
              }
              if (cursor < value.length) {
                fragment.appendChild(document.createTextNode(value.substring(cursor)));
              }
              textNode.parentNode.replaceChild(fragment, textNode);
            }
            buildTextIndex();
            return hits;
          }

          function refresh() {
            if (settleTimer) { window.clearTimeout(settleTimer); settleTimer = null; }
            var keep = pendingOffset > 0 ? pendingOffset : (lastSentOffset > 0 ? lastSentOffset : 0);
            var ratio = 0;
            if (!paged && view.scrollHeight > viewportHeight()) {
              ratio = view.scrollTop / Math.max(1, view.scrollHeight - viewportHeight());
            } else if (paged && pages > 1) {
              ratio = page / (pages - 1);
            }
            if (!measure()) return;
            if (!paged) {
              view.scrollTop = ratio > 0
                ? ratio * Math.max(0, view.scrollHeight - viewportHeight())
                : pixelsForOffset(keep);
            } else {
              var target = keep > 0
                ? Math.floor(pixelsForOffset(keep) / Math.max(1, stride))
                : Math.round(ratio * (pages - 1));
              page = Math.max(0, Math.min(pages - 1, target));
              applyPosition(pageTop(page), false);
            }
            lastSentOffset = -1;
            report();
          }

          function cancelGesture() {
            gesture = null;
            scrollGesture = null;
            pulling = false;
            pull = 0;
            hideEdges();
            if (settleTimer) { window.clearTimeout(settleTimer); settleTimer = null; }
            if (paged) {
              applyPosition(pageTop(page), false);
            } else {
              content.style.transition = 'none';
              content.style.transform = 'none';
            }
          }

          function setNextChapter(title) {
            hasNext = !!title && title.length > 0;
            NEXT_TITLE = title || '';
            if (edgeNext) {
              edgeNext.textContent = hasNext ? ('下一章 · ' + NEXT_TITLE) : '';
              edgeNext.style.display = hasNext ? 'flex' : 'none';
            }
          }

          /** Labels the banner for the chapter *before* this one (pulling down at the top). */
          function setPreviousChapter(title) {
            hasPrev = !!title && title.length > 0;
            PREV_TITLE = title || '';
            if (edgePrev) {
              edgePrev.textContent = hasPrev ? ('上一章 · ' + PREV_TITLE) : '';
              edgePrev.style.display = hasPrev ? 'flex' : 'none';
            }
          }

          /**
           * Restyles the page that is already on screen: replaces the stylesheet, re-measures and
           * puts the reader back where they were. Rebuilding the whole chapter for every tick of a
           * slider re-parsed the HTML and re-decoded every picture - exactly what made the settings
           * sheet stutter on phones.
           */
          function setStyle(cssText, horizontalPad, verticalPad) {
            var styleElement = document.getElementById('lr-style');
            if (styleElement && typeof cssText === 'string') styleElement.textContent = cssText;
            if (typeof horizontalPad === 'number' && horizontalPad >= 0) H_PAD = horizontalPad;
            if (typeof verticalPad === 'number' && verticalPad >= 0) V_PAD = verticalPad;
            buildTextIndex();
            var keep = lastSentOffset > 0 ? lastSentOffset : (pendingOffset > 0 ? pendingOffset : 0);
            var ratio = 0;
            if (!paged && view.scrollHeight > viewportHeight()) {
              ratio = view.scrollTop / Math.max(1, view.scrollHeight - viewportHeight());
            } else if (paged && pages > 1) {
              ratio = page / (pages - 1);
            }
            if (!measure()) return;
            if (keep > 0) {
              goToOffset(keep);
            } else if (ratio > 0) {
              if (paged) {
                page = Math.max(0, Math.min(pages - 1, Math.round(ratio * (pages - 1))));
                applyPosition(pageTop(page), false);
              } else {
                view.scrollTop = ratio * Math.max(0, view.scrollHeight - viewportHeight());
              }
            }
            lastSentOffset = -1;
            report();
            reportMetrics();
          }

          window.LrReader = {
            nextPage: nextPage,
            prevPage: prevPage,
            goToOffset: goToOffset,
            scrollToId: scrollToId,
            highlight: highlight,
            report: report,
            refresh: refresh,
            cancelGesture: cancelGesture,
            setNextChapter: setNextChapter,
            setPreviousChapter: setPreviousChapter,
            setStyle: setStyle,
            pageCount: function () { return pages; },
            metrics: function () {
              return {
                pages: pages,
                page: page,
                offset: currentOffset(),
                chars: totalChars,
                mode: !paged ? 'scroll' : (columnMode ? 'columns' : 'natural'),
                stride: stride,
                transform: content.style.transform,
                hasNext: hasNext,
                ready: ready,
                shortChapter: shortChapter,
                pullThreshold: pullThreshold,
                pages: pages
              };
            }
          };

          /** Sends the measured geometry to the host so real device problems can be diagnosed. */
          function reportMetrics() {
            if (!window.LrBridge || !LrBridge.onMetrics) return;
            try {
              LrBridge.onMetrics(JSON.stringify(window.LrReader.metrics()));
            } catch (error) {
              // Diagnostics only.
            }
          }

          // ------------------------------------------------------------ gestures

          function pointOf(event) {
            if (event.touches && event.touches.length === 1) return event.touches[0];
            if (event.changedTouches && event.changedTouches.length >= 1) return event.changedTouches[0];
            return null;
          }

          view.addEventListener('touchstart', function (event) {
            if (!ready) return;
            var point = pointOf(event);
            if (!point) return;
            if (paged) {
              gesture = {
                x: point.clientX,
                y: point.clientY,
                page: page,
                time: Date.now(),
                moved: false,
                longPress: false
              };
            } else {
              scrollGesture = {
                x: point.clientX,
                y: point.clientY,
                time: Date.now(),
                longPress: false
              };
            }
          }, { passive: true });

          view.addEventListener('touchmove', function (event) {
            var point = pointOf(event);
            if (!point) return;
            if (paged) {
              if (!gesture) return;
              var dx = point.clientX - gesture.x;
              var dy = point.clientY - gesture.y;
              if (Date.now() - gesture.time > 320) { gesture.longPress = true; return; }
              if (!gesture.moved) {
                if (Math.abs(dx) < 10 || Math.abs(dx) < Math.abs(dy)) return;
                gesture.moved = true;
              }
              event.preventDefault();
              content.classList.add('lr-turning');
              var base = gesture.page * stride;
              applyPosition(Math.max(0, Math.min((pages - 1) * stride, base - dx)), false);
              return;
            }
            if (!scrollGesture) return;
            var deltaY = point.clientY - scrollGesture.y;
            // Only a deliberate press-and-hold is a long press (text selection).
            if (Date.now() - scrollGesture.time > 700) { scrollGesture.longPress = true; return; }
            var maxScroll = Math.max(0, view.scrollHeight - view.clientHeight);
            // "At the end" is judged while the finger moves, not only at touch down: reaching the
            // bottom during the drag and then pulling on has to work, otherwise all the reader ever
            // sees is the native rubber band springing back.
            if (deltaY < 0 && hasNext && view.scrollTop >= maxScroll - BOTTOM_SLACK) {
              // Pulling past the last line: the next chapter's label slides up from the bottom
              // edge. The chapter changes only once that label is completely on screen.
              pull = Math.max(0, -deltaY);
              pulling = 'next';
              event.preventDefault();
              content.style.transition = 'none';
              content.style.transform = 'translate3d(0px, ' + (-pull * 0.45) + 'px, 0px)';
              var nextProgress = edgeProgress(pull, edgeNext);
              applyEdge(edgeNext, nextProgress);
              if (nextProgress >= EDGE_REVEAL) {
                handOver(edgeNext, reportEnd);
              }
              return;
            }
            if (deltaY > 0 && hasPrev && view.scrollTop <= BOTTOM_SLACK) {
              // Mirror image at the top of the chapter: the previous chapter's label drops in.
              pull = Math.max(0, deltaY);
              pulling = 'previous';
              event.preventDefault();
              content.style.transition = 'none';
              content.style.transform = 'translate3d(0px, ' + (pull * 0.45) + 'px, 0px)';
              var previousProgress = edgeProgress(pull, edgePrev);
              applyEdge(edgePrev, previousProgress);
              if (previousProgress >= EDGE_REVEAL) {
                handOver(edgePrev, reportStart);
              }
            }
          }, { passive: false });

          view.addEventListener('touchend', function (event) {
            if (paged) {
              var current = gesture;
              gesture = null;
              content.classList.remove('lr-turning');
              if (!current || current.longPress || !current.moved) return;
              var point = pointOf(event);
              var dx = point ? point.clientX - current.x : 0;
              var threshold = Math.min(stride * 0.2, 72);
              if (Math.abs(dx) < threshold) { goToPage(current.page, true); return; }
              var direction = dx < 0 ? 1 : -1;
              var target = current.page + direction;
              if (target < 0 || target > pages - 1) {
                goToPage(current.page, true);
                if (target > pages - 1) {
                  promptContinue();
                } else {
                  promptPrevious();
                }
                return;
              }
              goToPage(target, true);
              return;
            }
            var scrollState = scrollGesture;
            scrollGesture = null;
            if (!scrollState || !pulling) return;
            var direction = pulling;
            pulling = false;
            var amount = pull;
            pull = 0;
            content.style.transition = 'transform 240ms cubic-bezier(0.22, 0.61, 0.36, 1)';
            content.style.transform = 'translate3d(0px, 0px, 0px)';
            hideEdges();
            // Letting go before the label is fully revealed simply cancels the gesture; the
            // chapter only changes when the reader really pulled the whole label into view.
            void amount;
            void direction;
          }, { passive: true });

          // ------------------------------------------------------------ lifecycle

          var scrollTimer = null;
          view.addEventListener('scroll', function () {
            if (!ready || paged) return;
            if (scrollTimer) window.clearTimeout(scrollTimer);
            scrollTimer = window.setTimeout(function () {
              report();
            }, 160);
          }, { passive: true });

          function start() {
            // The engine can be booted from more than one place (DOM ready, window load, the late
            // fallback); starting twice would report "ready" twice and re-measure for nothing.
            if (ready) return;
            diag('start');
            buildTextIndex();
            // Labels come from the chapter titles the page was built with; the host can refine them
            // later through setNextChapter / setPreviousChapter.
            setNextChapter(NEXT_TITLE);
            setPreviousChapter(PREV_TITLE);
            var measured = measure();
            diag('measure', measured);
            if (!measured) {
              window.setTimeout(start, 60);
              return;
            }
            if (pendingOffset > 0) {
              goToOffset(pendingOffset);
              pendingOffset = 0;
            }
            lastSentOffset = -1;
            report();
            if (window.LrBridge) LrBridge.onReady(pages, page);
            reportMetrics();
          }

          var refreshTimer = null;
          function scheduleRefresh() {
            if (refreshTimer) window.clearTimeout(refreshTimer);
            refreshTimer = window.setTimeout(function () {
              refreshTimer = null;
              if (ready) refresh();
            }, 140);
          }

          /**
           * Web fonts and late images change how the text flows, which changes the page grid.
           * Measuring before they settle is what leaves a reader with pages that do not match the
           * text, so the engine waits for the fonts and re-measures when images arrive.
           */
          function startWhenFontsReady() {
            var started = false;
            function go() {
              if (started) return;
              started = true;
              diag('fonts-ready');
              start();
            }
            if (document.fonts && document.fonts.ready && document.fonts.ready.then) {
              document.fonts.ready.then(go, go);
              window.setTimeout(go, 700);
            } else {
              go();
            }
          }

          content.addEventListener('load', function (event) {
            var target = event.target;
            if (target && target.tagName === 'IMG') scheduleRefresh();
          }, true);

          var booted = false;
          function boot() {
            if (booted) return;
            booted = true;
            diag('boot', document.readyState);
            window.setTimeout(startWhenFontsReady, 0);
          }
          if (document.readyState === 'complete' || document.readyState === 'interactive') {
            window.setTimeout(boot, 0);
          } else {
            // Both events are watched: some WebViews finish parsing the document without ever
            // dispatching a window `load`, which left the reading surface measured but never
            // started - no page turns, no position reporting, nothing.
            document.addEventListener('DOMContentLoaded', boot, false);
            window.addEventListener('load', boot, false);
          }
          window.addEventListener('resize', function () {
            if (!ready) return;
            window.setTimeout(refresh, 60);
          }, false);
          window.setTimeout(function () { if (!ready) { diag('late-start'); start(); } }, 400);
        })();
    """.trimIndent()

    /** Minimal escaping for the single quoted JavaScript string that carries a chapter title. */
    private fun escapeJs(value: String): String = value
        .replace("\\", "\\\\")
        .replace("'", "\\'")
        .replace("\n", " ")
        .replace("\r", " ")
        .replace("</", "<\\/")
}
