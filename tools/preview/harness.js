/*
 * Measurement harness injected into the reader page.
 *
 * It stands in for the Android side (fake LrBridge), lets the engine settle, then walks a few
 * pages and records what really happened: page numbers, the applied transform, the caret offset
 * before/after each turn, the side margins and whether the next-chapter hint is offered.
 * The result is written into #lr-harness-out so `chrome --dump-dom` can be parsed.
 */
(function () {
  var result = {
    ready: 0,
    locations: 0,
    end: 0,
    metrics: [],
    steps: [],
    margins: null,
    hint: null,
    error: null
  };

  function write() {
    var out = document.getElementById('lr-harness-out');
    if (out) out.textContent = JSON.stringify(result);
  }

  window.setTimeout(function () {
    try {
      result.errors = window.__lrErrors || [];
      result.bridge = window.__lr || null;
      result.fonts = document.fonts ? document.fonts.status : 'n/a';
      var view = document.getElementById('lr-view');
      var content = document.getElementById('lr-content');
      var hint = document.getElementById('lr-hint');

      result.start = window.LrReader.metrics();
      result.probe = {
        readyState: document.readyState,
        hintText: hint ? hint.textContent : null,
        scrollTop: view.scrollTop,
        scrollHeight: view.scrollHeight,
        clientHeight: view.clientHeight
      };
      result.hint = hint ? hint.style.opacity : null;
      result.viewport = { width: view.clientWidth, height: view.clientHeight };
      result.scroll = {
        scrollWidth: content.scrollWidth,
        scrollHeight: content.scrollHeight,
        columnWidth: content.style.columnWidth,
        columnGap: content.style.columnGap
      };

      var first = content.children.length ? content.children[0].getBoundingClientRect() : null;
      if (first) {
        result.margins = {
          left: Math.round(first.left),
          right: Math.round(view.clientWidth - first.right)
        };
      }

      // Scroll mode first: the reader's own gesture - pull up at the very end. The position is set
      // without firing a scroll event on purpose, so the engine has to judge "at the end" from the
      // live scroll position, exactly like it does under a real finger.
      if (result.start.mode === 'scroll') {
        function touch(y) {
          return new Touch({ identifier: 1, target: view, clientX: 200, clientY: y });
        }
        function fire(type, y) {
          var t = touch(y);
          view.dispatchEvent(new TouchEvent(type, {
            touches: type === 'touchend' ? [] : [t],
            changedTouches: [t],
            bubbles: true,
            cancelable: true
          }));
        }
        function drag(from, to, steps) {
          fire('touchstart', from);
          for (var s = 1; s <= steps; s++) {
            fire('touchmove', from + (to - from) * s / steps);
          }
          return function () { fire('touchend', to); };
        }

        view.scrollTop = view.scrollHeight;
        var beforePull = window.__lr.end;
        var release = drag(700, 610, 6);
        result.pullHintOpacity = hint ? hint.style.opacity : null;
        result.pullTransform = content.style.transform;
        result.pullDuringDrag = window.__lr.end - beforePull;
        release();
        result.pullAfterRelease = window.__lr.end - beforePull;
      }

      for (var i = 0; i < 6; i++) {
        var before = window.LrReader.metrics();
        window.LrReader.nextPage();
        var after = window.LrReader.metrics();
        result.steps.push({
          page: after.page,
          stride: after.stride,
          transform: content.style.transform,
          offsetBefore: before.offset,
          offsetAfter: after.offset
        });
      }
      result.endAfterWalk = result.end;
      result.final = window.LrReader.metrics();

      if (result.start.mode === 'scroll') {
        result.bottomBefore = window.__lr.end;
        view.dispatchEvent(new Event('scroll'));
      }
    } catch (error) {
      result.error = String(error);
    }
    write();
  }, 2200);

  window.setTimeout(function () {
    try {
      var out = document.getElementById('lr-harness-out');
      var parsed = JSON.parse(out.textContent);
      parsed.bottomAfter = (window.__lr && window.__lr.end) || 0;
      parsed.bottomReached = window.__lr ? window.__lr.end - (parsed.bottomBefore || 0) : 0;
      out.textContent = JSON.stringify(parsed);
    } catch (error) {
      // ignore
    }
  }, 3400);
})();
