// 推し馬ページで選んだ着せ替えを、画面を描く前に当てる（色がチラつかないよう head で読む）
(function () {
  var colors = { sakura: '#e75480', yume: '#9b6bd6', noir: '#0d0d0f', navy: '#1a3d7c' };
  try {
    var t = localStorage.getItem('theme');
    if (colors[t]) {
      document.documentElement.setAttribute('data-theme', t);
      var meta = document.querySelector('meta[name="theme-color"]');
      if (meta) meta.setAttribute('content', colors[t]);
    }
  } catch (e) { /* 保存できない環境では標準の色のまま */ }
})();
