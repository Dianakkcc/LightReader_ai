/*
 * Runs before the reader engine (injected into <head>) so that errors thrown while the engine
 * starts are captured instead of disappearing into the console.
 */
window.__lrErrors = [];
window.addEventListener('error', function (event) {
  window.__lrErrors.push(String(event.message) + ' @' + event.lineno + ':' + event.colno);
});
window.addEventListener('unhandledrejection', function (event) {
  window.__lrErrors.push('rejection: ' + String(event.reason));
});

window.__lr = { ready: 0, locations: 0, end: 0, metrics: [], lastLocation: null };
window.LrBridge = {
  onLocation: function (chapter, offset, fraction, pages, page) {
    window.__lr.locations++;
    window.__lr.lastLocation = [chapter, offset, Math.round(fraction * 1000) / 1000, pages, page];
  },
  onReady: function () { window.__lr.ready++; },
  onReachEnd: function () { window.__lr.end++; },
  onMetrics: function (json) { window.__lr.metrics.push(json); }
};
