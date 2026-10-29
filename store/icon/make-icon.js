// アプリのアイコン（案B：金色＋きらきら＋オリジナルの馬の顔）と、ウェルカム画面の羽のある子を、1か所の部品から書き出す。
//   store/icon/icon.svg                         … ストア用（512x512 の PNG はこれから作る）
//   app/src/main/res/drawable/ic_launcher_*.xml … アプリ用のアダプティブアイコン（背景・前景・単色）
//   app/src/main/res/drawable/welcome_pegasus.xml … ウェルカム画面の羽のある子（顔は同じ部品）
// 使い方: node store/icon/make-icon.js
const fs = require("fs");
const path = require("path");
const root = path.join(__dirname, "..", "..");

const INK = "#6B4423";
const ellipse = (cx, cy, rx, ry) =>
  `M${cx - rx},${cy}a${rx},${ry} 0,1 0,${2 * rx},0a${rx},${ry} 0,1 0,${-2 * rx},0Z`;
const sparkle = (x, y, r) =>
  `M${x},${y - r}l${r * 0.4},${r * 0.6} ${r * 0.6},${r * 0.4} ${-r * 0.6},${r * 0.4} ${-r * 0.4},${r * 0.6} ${-r * 0.4},${-r * 0.6} ${-r * 0.6},${-r * 0.4} ${r * 0.6},${-r * 0.4}Z`;

// 馬の顔（108x108 の座標）。fill・stroke・alpha は SVG と Android の両方で使う
const face = [
  { d: "M36.5,44C31,28 35,19 45.5,31Z", fill: "#D9944A", stroke: INK, sw: 2.4 },
  { d: "M71.5,44C77,28 73,19 62.5,31Z", fill: "#D9944A", stroke: INK, sw: 2.4 },
  { d: "M38.5,40C36,31 37.5,27 42.5,32.5Z", fill: "#F2B8A0" },
  { d: "M69.5,40C72,31 70.5,27 65.5,32.5Z", fill: "#F2B8A0" },
  { d: ellipse(54, 57, 21.5, 24), fill: "#D9944A", stroke: INK, sw: 2.4 },
  { d: "M42,38Q45,27 52,33Q55,25 61,32Q67,29 66,38Q60,43 54,40Q47,44 42,38Z", fill: "#8B5A2B", stroke: INK, sw: 2 },
  { d: ellipse(45.5, 54, 3.4, 3.9), fill: "#4A2E19", eye: true },
  { d: ellipse(62.5, 54, 3.4, 3.9), fill: "#4A2E19", eye: true },
  { d: ellipse(46.7, 52.6, 1.25, 1.25), fill: "#FFFFFF", shine: true },
  { d: ellipse(63.7, 52.6, 1.25, 1.25), fill: "#FFFFFF", shine: true },
  { d: ellipse(39, 63, 3.6, 2.2), fill: "#F08A8A", alpha: 0.75, cheek: true },
  { d: ellipse(69, 63, 3.6, 2.2), fill: "#F08A8A", alpha: 0.75, cheek: true },
  { d: ellipse(54, 71, 14.5, 9.8), fill: "#FBE3C4", stroke: INK, sw: 2.2 },
  { d: ellipse(49, 70.5, 1.7, 2.3), fill: INK, eye: true },
  { d: ellipse(59, 70.5, 1.7, 2.3), fill: INK, eye: true },
  { d: "M50.5,76Q54,78.8 57.5,76", stroke: INK, sw: 1.8, cap: "round" },
];
// 顔のうしろの白い光と、まわりのきらきら
const extras = [
  { d: ellipse(54, 56, 33, 33), fill: "#FFFFFF", alpha: 0.35, halo: true },
  { d: sparkle(26, 34, 5.6), fill: "#FFFFFF", spark: true },
  { d: sparkle(82, 78, 4.5), fill: "#FFFFFF", spark: true },
  { d: sparkle(80, 32.5, 3.5), fill: "#FFFFFF", spark: true },
];
const GRADIENT = { from: "#FFF1A8", to: "#F5A623" };
// アプリのアイコンは端末が真ん中の 72dp を切り抜いて見せるので、絵を縮めて安全な範囲（直径66dp）に収める
const LAUNCHER_SCALE = 0.72;

// ---- ストア用の SVG（108 全体がそのまま見える）
function svgShape(s) {
  const a = [`d="${s.d}"`];
  a.push(`fill="${s.fill || "none"}"`);
  if (s.alpha != null) a.push(`fill-opacity="${s.alpha}"`);
  if (s.stroke) a.push(`stroke="${s.stroke}" stroke-width="${s.sw}" stroke-linejoin="round"`);
  if (s.cap) a.push(`stroke-linecap="${s.cap}"`);
  return `  <path ${a.join(" ")}/>`;
}
const svg = `<svg xmlns="http://www.w3.org/2000/svg" viewBox="0 0 108 108">
  <!-- make-icon.js が書き出したもの。直すときは make-icon.js を直して書き出し直す -->
  <defs>
    <linearGradient id="bg" x1="0" y1="0" x2="108" y2="108" gradientUnits="userSpaceOnUse">
      <stop offset="0" stop-color="${GRADIENT.from}"/>
      <stop offset="1" stop-color="${GRADIENT.to}"/>
    </linearGradient>
  </defs>
  <rect width="108" height="108" fill="url(#bg)"/>
${[...extras.filter(s => s.halo), ...face, ...extras.filter(s => s.spark)].map(svgShape).join("\n")}
</svg>
`;
fs.writeFileSync(path.join(__dirname, "icon.svg"), svg);

// ---- アプリ用のベクター画像
const header = `<?xml version="1.0" encoding="utf-8"?>
<!-- store/icon/make-icon.js が書き出したもの。直すときは make-icon.js を直して書き出し直す -->
<vector xmlns:android="http://schemas.android.com/apk/res/android"
    xmlns:aapt="http://schemas.android.com/aapt"
    android:width="108dp"
    android:height="108dp"
    android:viewportWidth="108"
    android:viewportHeight="108">
`;
function vecShape(s, o = {}) {
  const fill = o.fill !== undefined ? o.fill : s.fill;
  const lines = [`        <path`, `            android:pathData="${s.d}"`];
  if (fill) lines.push(`            android:fillColor="${fill}"`);
  const alpha = o.alpha !== undefined ? o.alpha : s.alpha;
  if (fill && alpha != null) lines.push(`            android:fillAlpha="${alpha}"`);
  const stroke = o.stroke !== undefined ? o.stroke : s.stroke;
  if (stroke) {
    lines.push(`            android:strokeColor="${stroke}"`);
    lines.push(`            android:strokeWidth="${o.sw || s.sw}"`);
    lines.push(`            android:strokeLineJoin="round"`);
    if (s.cap || o.cap) lines.push(`            android:strokeLineCap="${s.cap || o.cap}"`);
  }
  return lines.join("\n") + " />";
}
const group = body => `    <group
        android:pivotX="54"
        android:pivotY="54"
        android:scaleX="${LAUNCHER_SCALE}"
        android:scaleY="${LAUNCHER_SCALE}">
${body}
    </group>
</vector>
`;
const res = path.join(root, "app", "src", "main", "res", "drawable");

fs.writeFileSync(path.join(res, "ic_launcher_background.xml"), header + `    <path android:pathData="M0,0h108v108h-108z">
        <aapt:attr name="android:fillColor">
            <gradient
                android:type="linear"
                android:startX="0"
                android:startY="0"
                android:endX="108"
                android:endY="108">
                <item android:offset="0" android:color="${GRADIENT.from}" />
                <item android:offset="1" android:color="${GRADIENT.to}" />
            </gradient>
        </aapt:attr>
    </path>
</vector>
`);

fs.writeFileSync(path.join(res, "ic_launcher_foreground.xml"), header +
  group([...extras.filter(s => s.halo), ...face, ...extras.filter(s => s.spark)].map(s => vecShape(s)).join("\n")));

// 単色（テーマアイコン）は色の濃さだけが使われるので、線画にする。目と鼻の穴だけ塗る
const mono = face
  .filter(s => !s.shine && !s.cheek && s.fill !== "#F2B8A0")
  .map(s => s.eye ? vecShape(s, { fill: "#FF000000" }) : vecShape(s, { fill: null, stroke: "#FF000000", sw: s.sw || 2.2 }));
fs.writeFileSync(path.join(res, "ic_launcher_monochrome.xml"), header + group(mono.join("\n")));

// ---- ウェルカム画面に出す、羽のある子（ペガサス）。背景なし・縮めない。アプリのアイコンには使わない
const WING = "M35,52C26,38 13,37 7,44C13,46 16,48 17,51C10,51 6,55 5,59C12,59 16,60 18,62C14,64 12,68 12,71C22,70 30,64 35,60Z";
const WING_LINE = "M17,51C23,52 29,54 34,55M18,62C24,61 29,59 34,58";
const wing = [
  vecShape({ d: WING, fill: "#FFFFFF", stroke: INK, sw: 2 }),
  vecShape({ d: WING_LINE, fill: null, stroke: "#E0B060", sw: 1.2 }, { fill: null }),
].join("\n");
const mirrored = `    <group
        android:pivotX="54"
        android:scaleX="-1">
${wing}
    </group>`;
fs.writeFileSync(path.join(res, "welcome_pegasus.xml"), header + wing + "\n" + mirrored + "\n" +
  face.map(s => vecShape(s)).join("\n") + "\n</vector>\n");

console.log("書き出しました: store/icon/icon.svg と ic_launcher_background / foreground / monochrome と welcome_pegasus");
