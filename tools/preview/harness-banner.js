/*
 * Holds the chapter boundary banner part way through its pull, so a screenshot shows what the
 * reader really sees while dragging: plain text over a gradient that fades into the page, with no
 * coloured plate. Set window.__lrBannerEdge = 'prev' before this script to check the top banner.
 *
 * Nothing is released, so the state stays put for `chrome --screenshot`.
 */
(function () {
  var edge = (window.__lrBannerEdge === 'prev') ? 'prev' : 'next';
  var result = { edge: edge, error: null };

  function write() {
    var out = document.getElementById('lr-harness-out');
    if (out) out.textContent = JSON.stringify(result);
  }

  window.setTimeout(function () {
    try {
      var view = document.getElementById('lr-view');
      var content = document.getElementById('lr-content');
      var banner = document.getElementById(edge === 'next' ? 'lr-edge-next' : 'lr-edge-prev');

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
        for (var s = 1; s <= steps; s++) fire('touchmove', from + (to - from) * s / steps);
      }

      // Half of the 84px banner: enough to read the label, not enough to switch chapters.
      if (edge === 'next') {
        view.scrollTop = view.scrollHeight;
        drag(700, 650, 6);
      } else {
        view.scrollTop = 0;
        drag(200, 250, 6);
      }

      var box = banner.getBoundingClientRect();
      var style = getComputedStyle(banner);
      result.metrics = window.LrReader.metrics();
      result.text = banner.textContent;
      result.opacity = banner.style.opacity;
      result.backgroundImage = style.backgroundImage;
      result.backgroundColor = style.backgroundColor;
      result.borderRadius = style.borderRadius;
      result.boxShadow = style.boxShadow;
      result.transform = style.transform;
      result.rect = { top: Math.round(box.top), height: Math.round(box.height) };
      result.contentTransform = content.style.transform;
      result.scrollTop = view.scrollTop;
      result.scrollHeight = view.scrollHeight;
      result.clientHeight = view.clientHeight;
      result.errors = window.__lrErrors || [];
    } catch (error) {
      result.error = String(error);
    }
    write();
  }, 2200);
})();
