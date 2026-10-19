/* Settles the engine, measures the page box, and stops - used for a screenshot of page one. */
(function () {
  window.setTimeout(function () {
    var view = document.getElementById('lr-view');
    var content = document.getElementById('lr-content');
    var first = content.children.length ? content.children[0].getBoundingClientRect() : null;
    var info = {
      metrics: window.LrReader.metrics(),
      viewport: { width: view.clientWidth, height: view.clientHeight },
      paddingTop: content.style.paddingTop,
      paddingLeft: content.style.paddingLeft,
      marginTop: content.style.marginTop,
      firstChildTop: first ? Math.round(first.top) : null,
      firstChildLeft: first ? Math.round(first.left) : null,
      errors: window.__lrErrors || []
    };
    var out = document.getElementById('lr-harness-out');
    if (out) out.textContent = JSON.stringify(info);
  }, 2200);
})();
