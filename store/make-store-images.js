// ストア用のスクリーンショット（1080x1920）とフィーチャー グラフィック（1024x500）を作る。
// 画面の素材（エミュレーターで撮った 1080x2400 の PNG）を store/work/shots/ に置いてから実行する。
//   node store/make-store-images.js
// HTML を store/work/ に書き出し、Chrome（画面なし）で撮って store/screenshots-v393/ に PNG を作る。
const fs = require("fs");
const path = require("path");
const { execFileSync } = require("child_process");

const store = __dirname;
const work = path.join(store, "work");
const out = path.join(store, "screenshots-v393");
fs.mkdirSync(out, { recursive: true });
const CHROME = "C:/Program Files (x86)/Google/Chrome/Application/chrome.exe";

const FONT = "'Yu Gothic UI','BIZ UDPGothic','Meiryo',sans-serif";
// 新しいアイコン（金色＋きらきら）に合わせた、やわらかいクリーム色から金色への背景
const BG = "linear-gradient(165deg,#FFFBEA 0%,#FFE9A8 55%,#F7C255 100%)";
const INK = "#5A3A1A";
const ACCENT = "#C46A00";

const screens = [
  { file: "01_home", shot: "s_home.png", title: "推しの馬、<br>次はいつ走る？", sub: "次に走る推し馬が、ホームのいちばん上に。<br>出走決定・発走30分前・結果をお知らせ" },
  { file: "02_favorites", shot: "s_fav.png", title: "推し馬カードを、<br>自分好みに", sub: "アイコンと背景を選んで、推し馬ごとに飾れる" },
  { file: "03_decorate", shot: "s_deco.png", title: "選ぶだけで、<br>かんたんに飾れる", sub: "アイコン12種・背景10色。勝ったら勝利をシェア" },
  { file: "04_racecard", shot: "s_race.png", title: "出走表で、<br>近走をまとめてチェック", sub: "休み明け・距離延長・乗り替わりも自動で表示" },
  { file: "05_karte", shot: "s_karte.png", title: "得意な条件が、<br>ひと目で分かる", sub: "馬カルテで、芝・ダート、距離、競馬場ごとの成績" },
];

function shotHtml(s) {
  return `<!doctype html><html><head><meta charset="utf-8"></head>
<body style="margin:0;width:1080px;height:1920px;overflow:hidden;position:relative;font-family:${FONT};background:${BG}">
  <svg style="position:absolute;inset:0" width="1080" height="1920">
    <g fill="#FFFFFF" opacity=".75">
      <path d="M96,120 l10,22 22,10 -22,10 -10,22 -10,-22 -22,-10 22,-10 Z"/>
      <path d="M990,250 l7,16 16,7 -16,7 -7,16 -7,-16 -16,-7 16,-7 Z"/>
      <path d="M940,90 l5,11 11,5 -11,5 -5,11 -5,-11 -11,-5 11,-5 Z"/>
    </g>
  </svg>
  <div style="position:absolute;top:96px;left:0;right:0;text-align:center;color:${INK}">
    <div style="font-size:76px;font-weight:800;line-height:1.25;letter-spacing:.01em">${s.title}</div>
    <div style="margin-top:28px;font-size:34px;font-weight:600;line-height:1.5;color:${ACCENT}">${s.sub}</div>
  </div>
  <div style="position:absolute;top:500px;left:100px;width:880px;height:1956px;border:14px solid #3B2A1A;border-radius:68px;overflow:hidden;box-shadow:0 30px 70px rgba(120,70,0,.35);background:#fff">
    <img src="shots/${s.shot}" style="width:880px;display:block">
  </div>
</body></html>`;
}

const featureHtml = `<!doctype html><html><head><meta charset="utf-8"></head>
<body style="margin:0;width:1024px;height:500px;overflow:hidden;position:relative;font-family:${FONT};background:${BG}">
  <img src="../icon/icon.svg" style="position:absolute;left:64px;top:110px;width:200px;height:200px;border-radius:44px;box-shadow:0 12px 30px rgba(120,70,0,.3)">
  <div style="position:absolute;left:300px;top:118px;color:${INK}">
    <div style="font-size:66px;font-weight:800;letter-spacing:.02em">推し馬ノート</div>
    <div style="margin-top:14px;font-size:30px;font-weight:700;color:${ACCENT};line-height:1.45">推しの馬、次はいつ走る？<br>出走・発走前・結果をスマホにお知らせ</div>
  </div>
  <div style="position:absolute;left:785px;top:44px;width:210px;height:467px;border:8px solid #3B2A1A;border-radius:34px;overflow:hidden;box-shadow:0 14px 30px rgba(120,70,0,.35);transform:rotate(6deg);background:#fff">
    <img src="shots/s_home.png" style="width:210px;display:block">
  </div>
</body></html>`;

function render(html, name, w, h) {
  const htmlPath = path.join(work, name + ".html");
  fs.writeFileSync(htmlPath, html);
  const png = path.join(out, name + ".png");
  execFileSync(CHROME, ["--headless=new", "--disable-gpu", "--hide-scrollbars", "--allow-file-access-from-files",
    `--window-size=${w},${h}`, `--screenshot=${png}`, "file:///" + htmlPath.replace(/\\/g, "/")], { stdio: "ignore" });
  console.log("作りました:", path.relative(store, png));
}

for (const s of screens) render(shotHtml(s), s.file, 1080, 1920);
render(featureHtml, "feature_graphic", 1024, 500);
