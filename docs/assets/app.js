// 推し馬ノート Web版。Supabase の公開キーで、アプリと同じテーブルを読むだけのページ。
// 接続先は docs/config.js（リポジトリに入れて直接編集する）の window.KEIBA_CONFIG から読む。

const CONFIG = window.KEIBA_CONFIG || {};
const X_URL = 'https://x.com/tk241277687';
const PLAY_URL = 'https://play.google.com/store/apps/details?id=com.tktkcompany.kakoRaceKeiba';
const COURSE_ORDER = ['札幌', '函館', '福島', '新潟', '東京', '中山', '中京', '京都', '阪神', '小倉'];
// レース名の「(ＧⅠ)」「(GIII)」「(ＪＧⅡ)」など（アプリの GradedRaceNotices と同じ見分け方）
const GRADE = /[(（]\s*[JＪ]?[GＧ]\s*([ⅠⅡⅢ]|I{1,3}|[1-3１-３])\s*[)）]/;
const PAGE_SIZE = 1000;

const app = document.getElementById('app');

// ---------- データ取得 ----------

const MINUTE = 60e3;
// 読んだデータはこの端末に少しのあいだ取っておき、同じページを開き直したときは通信せずに出す。
// 保存できない環境（プライベートブラウズなど）では、毎回読むだけになる
function cacheGet(key) {
  try {
    const hit = JSON.parse(localStorage.getItem(key) || 'null');
    return hit && hit.until > Date.now() ? hit.rows : null;
  } catch (e) {
    return null;
  }
}
function cachePut(key, rows, ttl) {
  // 空の結果は「まだ入っていないだけ」かもしれないので、長くは取っておかない
  if (!rows.length) ttl = Math.min(ttl, 3 * MINUTE);
  try {
    localStorage.setItem(key, JSON.stringify({ until: Date.now() + ttl, rows }));
  } catch (e) {
    // 容量いっぱいなら、古い保存をまとめて消して諦める
    try { for (const k of Object.keys(localStorage)) if (k.startsWith('q:')) localStorage.removeItem(k); } catch (e2) { /* 何もしない */ }
  }
}

async function request(table, params, headers = {}) {
  if (!CONFIG.url || !CONFIG.key) throw new Error('接続先が設定されていません');
  const res = await fetch(`${CONFIG.url}/rest/v1/${table}?${params}`, { headers: { apikey: CONFIG.key, ...headers } });
  if (!res.ok) throw new Error(`${table}: ${res.status}`);
  return res;
}

// ttl: 保存しておく時間。オッズが動く出馬表は短く、終わった日の結果は長くする
async function query(table, params, ttl = 3 * MINUTE) {
  const key = `q:${table}?${params}`;
  const cached = cacheGet(key);
  if (cached) return cached;
  const rows = await (await request(table, params)).json();
  cachePut(key, rows, ttl);
  return rows;
}

// Supabase は1回に返す行数に上限がある（既定は1000行）。1回目で全部の件数を聞き、残りはまとめて同時に取る
async function queryAll(table, params, ttl = 3 * MINUTE) {
  const key = `q:${table}?${params}&all`;
  const cached = cacheGet(key);
  if (cached) return cached;
  const first = await request(table, `${params}&limit=${PAGE_SIZE}&offset=0`, { Prefer: 'count=exact' });
  const rows = await first.json();
  const total = Number((first.headers.get('content-range') || '').split('/')[1]) || rows.length;
  if (rows.length && rows.length < total) {
    const step = rows.length; // サーバーの上限が1000より小さくても、返ってきた件数ずつ進める
    const offsets = [];
    for (let o = step; o < total; o += step) offsets.push(o);
    const pages = await Promise.all(offsets.map(o => request(table, `${params}&limit=${step}&offset=${o}`).then(r => r.json())));
    for (const page of pages) rows.push(...page);
  }
  cachePut(key, rows, ttl);
  return rows;
}

// 2日以上前の開催なら、結果と払い戻しはもう出そろっていて変わらない
// （前日分は取り込みが遅れることがあるので、短い保存のままにする）
function settled(d) {
  const day = new Date(Date.UTC(+d.slice(0, 4), +d.slice(4, 6) - 1, +d.slice(6, 8)) + 2 * 864e5);
  return day.toISOString().slice(0, 10).replace(/-/g, '') <= todayJst();
}
const HOUR = 60 * MINUTE;

const enc = encodeURIComponent;
const raceFilter = (d, c, r) => `kaisaibi=eq.${d}&kaisaijo=eq.${enc(c)}&race_no=eq.${r}`;

// 1Rの1着の行を「その日その競馬場で開催があった」印として使う（アプリと同じ）
async function raceDays() {
  return query('race_results', 'select=kaisaibi,kaisaijo&race_no=eq.1&rank=eq.1&order=kaisaibi.desc,id.asc&limit=200');
}

// ---------- 表示の部品 ----------

function esc(s) {
  return String(s ?? '').replace(/[&<>"']/g, ch => ({ '&': '&amp;', '<': '&lt;', '>': '&gt;', '"': '&quot;', "'": '&#39;' }[ch]));
}

function todayJst() {
  return new Date(Date.now() + 9 * 3600e3).toISOString().slice(0, 10).replace(/-/g, '');
}

function dateLabel(d) {
  const week = '日月火水木金土'[new Date(Date.UTC(+d.slice(0, 4), +d.slice(4, 6) - 1, +d.slice(6, 8))).getUTCDay()];
  return `${Number(d.slice(4, 6))}/${Number(d.slice(6, 8))}(${week})`;
}

function courseSort(a, b) {
  const ia = COURSE_ORDER.indexOf(a), ib = COURSE_ORDER.indexOf(b);
  return (ia < 0 ? 99 : ia) - (ib < 0 ? 99 : ib) || a.localeCompare(b);
}

const isGraded = title => GRADE.test(title || '');
// 重賞の格（1〜3）。重賞でなければ 0
function gradeOf(title) {
  const m = (title || '').match(GRADE);
  if (!m) return 0;
  return { 'Ⅰ': 1, 'Ⅱ': 2, 'Ⅲ': 3, I: 1, II: 2, III: 3, '1': 1, '2': 2, '3': 3, '１': 1, '２': 2, '３': 3 }[m[1]] || 0;
}
// 「天皇賞(秋)(GⅠ)」→ 名前「天皇賞(秋)」と格の印に分ける
function titleHtml(title) {
  const t = (title || '').trim();
  const g = gradeOf(t);
  if (!g) return esc(t);
  return `${esc(t.replace(GRADE, '').trim())} <span class="grade g${g}">G${['', 'Ⅰ', 'Ⅱ', 'Ⅲ'][g]}</span>`;
}
const startTime = info => (info || '').match(/(\d{1,2}:\d{2})/)?.[1] || '';
const courseLabel = info => {
  const m = (info || '').match(/(芝|ダ|障)\D{0,2}(\d{3,4})m/);
  return m ? `${m[1]}${m[2]}m` : '';
};
const raceUrl = (d, c, r) => `race.html?d=${d}&c=${enc(c)}&r=${r}`;
const horseUrl = name => `horse.html?n=${enc(name)}`;
const horseLink = name => (name ? `<a class="horse" href="${horseUrl(name)}">${esc(name)}</a>` : '');
// 馬名として受け付ける形（問い合わせに入るので、長すぎるものや記号を含むものは外す）
const validHorseName = name => !!name && name.length <= 30 && !/[(),"\\]/.test(name);

// ---------- 推し馬（この iPhone の中だけに保存。ログインなし） ----------

const FAV_KEY = 'favHorses';
const FAV_MAX = 50;
function favs() {
  try {
    const list = JSON.parse(localStorage.getItem(FAV_KEY) || '[]');
    return Array.isArray(list) ? list.filter(validHorseName) : [];
  } catch (e) {
    return [];
  }
}
const isFav = name => favs().includes(name);
function toggleFav(name) {
  const list = favs();
  const on = !list.includes(name);
  const next = on ? [name, ...list].slice(0, FAV_MAX) : list.filter(n => n !== name);
  try { localStorage.setItem(FAV_KEY, JSON.stringify(next)); } catch (e) { return !on; }
  return on;
}
// ☆ボタン。big は馬のページの大きいボタン
function favButton(name, big = false) {
  if (!validHorseName(name)) return '';
  const on = isFav(name);
  return `<button type="button" class="fav${big ? ' big' : ''}${on ? ' on' : ''}" data-fav="${esc(name)}" aria-pressed="${on}">${favText(on, big)}</button>`;
}
const favText = (on, big) => (big ? (on ? '★ 推し馬として応援中' : '☆ 推し馬にする') : (on ? '★' : '☆'));
// ボタンが押されたら、同じ馬のボタンと行の色をまとめて切り替える
app.addEventListener('click', e => {
  const btn = e.target.closest('[data-fav]');
  if (!btn) return;
  e.preventDefault();
  const name = btn.dataset.fav;
  const on = toggleFav(name);
  for (const b of app.querySelectorAll('[data-fav]')) {
    if (b.dataset.fav !== name) continue;
    b.classList.toggle('on', on);
    b.setAttribute('aria-pressed', String(on));
    b.textContent = favText(on, b.classList.contains('big'));
    b.closest('tr')?.classList.toggle('fav-row', on);
  }
});

// ---------- 推し馬カードの飾り（アプリと同じアイコン12種・背景10種。この iPhone に保存） ----------

const CARD_ICONS = ['', '🏇', '🐴', '🦄', '🏆', '👑', '⭐', '❤️', '🍀', '🌸', '🔥', '🌈'];
const CARD_BGS = [['', '標準'], ['sakura', '桜'], ['wakakusa', '若草'], ['sora', '空'], ['yuyake', '夕焼け'], ['kin', '金'],
  ['fuji', '藤'], ['mint', 'ミント'], ['momiji', '紅葉'], ['kiri', '霧']];
const STYLE_KEY = 'cardStyles';
// 保存できなかったときは、このページを閉じるまでここに持っておく
let unsavedStyles = null;
function cardStyles() {
  if (unsavedStyles) return { ...unsavedStyles };
  try {
    const m = JSON.parse(localStorage.getItem(STYLE_KEY) || '{}');
    return m && typeof m === 'object' && !Array.isArray(m) ? m : {};
  } catch (e) {
    return {};
  }
}
function styleOf(name) {
  const st = cardStyles()[name] || {};
  return {
    icon: CARD_ICONS.includes(st.icon) ? st.icon : '',
    bg: CARD_BGS.some(([k]) => k === st.bg) ? st.bg : '',
  };
}
function saveStyle(name, st) {
  const m = cardStyles();
  if (st.icon || st.bg) m[name] = st; else delete m[name];
  try {
    localStorage.setItem(STYLE_KEY, JSON.stringify(m));
  } catch (e) {
    unsavedStyles = m; // 保存できない環境では、このページを閉じるまでだけ
  }
}
// 馬のアイコン（選んでいなければ馬の顔）。size は sm / md / lg
const avatarOf = (icon, size) => `<span class="ava ${size}" aria-hidden="true">${icon ? esc(icon) : '<img src="assets/icon-192.png?v=13" alt="">'}</span>`;
const avatar = (name, size = 'md') => avatarOf(styleOf(name).icon, size);
// 背景のクラス。選んでいなければ fallback（今週走る馬はクリーム、ほかは白。アプリと同じ）
const bgClass = (name, fallback) => `bg-${styleOf(name).bg || fallback}`;

// 表の上に出す、色や横棒の意味の説明
// withOdds が false（オッズ発売前）なら、☆の説明だけ出す
function legend(withOdds = true) {
  return `<div class="legend">${withOdds ? `
    <span><span class="pop p1">1人気</span><span class="pop p2">2人気</span><span class="pop p3">3人気</span> 単勝の人気順（金・銀・銅）</span>
    <span><span class="bar demo"><i></i></span> 横棒が長いほど単勝オッズが低い＝勝ちそうと見られている</span>` : ''}
    <span><span class="fav on">★</span> を押すと推し馬に登録（この iPhone に保存）</span>
  </div>`;
}
// 人気の印（1〜3番人気は色つきの丸）
const popBadge = pop => (pop ? `<span class="pop${Number(pop) <= 3 ? ` p${Number(pop)}` : ''}">${esc(pop)}人気</span>` : '');
// 単勝オッズの横棒。オッズが低い（人気の）馬ほど長い。幅は描いたあとに barWidths() で入れる
const oddsBar = odds => {
  const o = parseFloat(odds);
  return o > 0 ? `<span class="bar"><i data-w="${Math.min(100, Math.round(100 / Math.sqrt(o)))}"></i></span>` : '';
};
// CSP でインラインの style 属性を禁止しているので、幅はスクリプトから入れる
function barWidths() {
  for (const el of app.querySelectorAll('[data-w]')) el.style.width = `${el.dataset.w}%`;
}
// 着順の丸。取消・除外など数字でないものは小さい字にする
const rankBadge = rank => `<span class="rank r${esc(rank)}${/^\d+$/.test(rank || '') ? '' : ' rx'}">${esc(rank)}</span>`;
function fullDate(d) {
  return `${d.slice(0, 4)}/${dateLabel(d)}`;
}
const waku = w => (w ? `<span class="waku w${esc(w)}">${esc(w)}</span>` : '');

function cta(shareText) {
  const share = shareText
    ? `<a class="btn" href="https://x.com/intent/post?text=${enc(shareText)}&url=${enc(location.href)}" target="_blank" rel="noopener">このページをXでシェア</a>`
    : '';
  return `<div class="cta">
    <a class="btn x" href="${X_URL}" target="_blank" rel="noopener">Xで最新情報をフォロー</a>
    ${share}
    <a class="btn" href="${PLAY_URL}" target="_blank" rel="noopener">Android版アプリ</a>
  </div>`;
}

function showError(e) {
  console.error(e);
  app.innerHTML = `<p class="notice">データを読み込めませんでした。時間をおいて開き直してください。<br><span class="small">${esc(e.message)}</span></p>${cta()}`;
}

// レースを開催日→競馬場→レース番号でまとめる（1レース1件）
function groupRaces(rows) {
  const races = new Map();
  for (const row of rows) {
    const key = `${row.kaisaibi}|${row.kaisaijo}|${row.race_no}`;
    if (!races.has(key)) races.set(key, row);
  }
  return [...races.values()].sort((a, b) =>
    a.kaisaibi.localeCompare(b.kaisaibi) || courseSort(a.kaisaijo, b.kaisaijo) || a.race_no - b.race_no);
}

// 一覧では左に「1R」を出すので、題名の頭の「1R 」は外す
const listTitle = title => (title || '').replace(/^\s*\d{1,2}R\s*/, '') || title;

function raceList(races, extra) {
  return `<ul class="races">${races.map(r => `
    <li><a href="${raceUrl(r.kaisaibi, r.kaisaijo, r.race_no)}">
      <span class="no">${r.race_no}R</span>
      <span class="title">${titleHtml(listTitle(r.race_title))}${extra ? extra(r) : ''}</span>
      <span class="info">${esc([startTime(r.race_info), courseLabel(r.race_info)].filter(Boolean).join(' '))}</span>
    </a></li>`).join('')}</ul>`;
}

// ---------- 推し馬の次走（ホーム・推し馬ページ） ----------

// 推し馬それぞれの、いちばん近い出走予定（馬名→出馬表の1行）
async function favNextRuns(list, today) {
  if (!list.length) return new Map();
  const inList = list.map(n => `"${n}"`).join(',');
  const runs = await query('race_entries',
    `select=horse_name,kaisaibi,kaisaijo,race_no,race_title,race_info&horse_name=in.(${enc(inList)})&kaisaibi=gte.${today}&order=kaisaibi.asc,race_no.asc`)
    .catch(() => []);
  const next = new Map();
  for (const r of runs) if (!next.has(r.horse_name)) next.set(r.horse_name, r);
  return next;
}
// あと何日か（今日なら「今日」）
function daysLeft(d, today) {
  const t = v => Date.UTC(+v.slice(0, 4), +v.slice(4, 6) - 1, +v.slice(6, 8));
  const n = Math.round((t(d) - t(today)) / 864e5);
  return n <= 0 ? '今日' : n === 1 ? 'あした' : `あと${n}日`;
}

// 今週いちばん格の高いレース（同じ格なら早いほう）。重賞がなければ null
function mainRace(races) {
  return races.filter(r => isGraded(r.race_title))
    .sort((a, b) => gradeOf(a.race_title) - gradeOf(b.race_title) || a.kaisaibi.localeCompare(b.kaisaibi))[0] || null;
}

// 上の緑のバーの題名（レース・馬のページは中身に合わせて変える）
function setBarTitle(text) {
  const el = document.querySelector('.top .ttl');
  if (el) el.textContent = text;
}
function longDate(d) {
  return `${d.slice(0, 4)}年${d.slice(4, 6)}月${d.slice(6, 8)}日${dateLabel(d).replace(/^[\d/]+/, '')}`;
}

// ---------- ホーム（アプリと同じ：次に走る推し馬を主役に、ほかの推し馬、メインレース） ----------

// 推し馬それぞれの前走（一番新しい結果）。よく走る馬に件数を取られないよう、1頭ずつ1件だけ聞く
async function favLastRuns(list) {
  const rows = await Promise.all(list.map(name => query('race_results',
    `select=horse_name,kaisaibi,kaisaijo,race_no,race_title,rank,popular&horse_name=eq.${enc(name)}&order=kaisaibi.desc,race_no.desc&limit=1`, HOUR)
    .catch(() => [])));
  const last = {};
  for (const [row] of rows) if (row) last[row.horse_name] = row;
  return last;
}
const prevLine = r => (r ? `前走 ${dateLabel(r.kaisaibi)} ${esc(r.kaisaijo)}${r.race_no ? `${r.race_no}R` : ''} ${esc(r.rank)}着${r.popular ? `（${esc(r.popular)}人気）` : ''}` : '');

// 主役にする馬：今週走る馬のうち一番早く走る馬。いなければ一番最近走った馬
function pickSpotlight(list, next, last) {
  const upcoming = list.filter(n => next.get(n))
    .sort((a, b) => {
      const x = next.get(a), y = next.get(b);
      return x.kaisaibi.localeCompare(y.kaisaibi) || startTime(x.race_info).padStart(5, '0').localeCompare(startTime(y.race_info).padStart(5, '0'));
    });
  if (upcoming.length) return { name: upcoming[0], kind: 'next' };
  const ran = list.filter(n => last[n]).sort((a, b) => last[b].kaisaibi.localeCompare(last[a].kaisaibi));
  return ran.length ? { name: ran[0], kind: 'last' } : { name: list[0], kind: 'none' };
}

function spotlightCard(pick, next, last) {
  const { name, kind } = pick;
  const n = next.get(name), l = last[name];
  let body = '';
  if (kind === 'next') {
    body = `<p class="lead">▶ 次に走る推し馬</p><a class="nm" href="${horseUrl(name)}">${esc(name)} ›</a>
      <a class="nx" href="${raceUrl(n.kaisaibi, n.kaisaijo, n.race_no)}">▶ ${dateLabel(n.kaisaibi)} ${esc(n.kaisaijo)}${n.race_no}R ${esc(startTime(n.race_info))}<br>
        <span>${titleHtml(listTitle(n.race_title))}${courseLabel(n.race_info) ? `（${esc(courseLabel(n.race_info))}）` : ''}</span></a>
      ${l ? `<p class="pv">${prevLine(l)}</p>` : ''}`;
  } else if (kind === 'last') {
    body = `<p class="lead">★ 最近走った推し馬</p><a class="nm" href="${horseUrl(name)}">${esc(name)} ›</a>
      <p class="big-rank${l.rank === '1' ? ' win' : ''}">${esc(l.rank)}着${l.rank === '1' ? '！' : ''}</p>
      <p class="pv">${dateLabel(l.kaisaibi)} ${esc(l.kaisaijo)} ${titleHtml(listTitle(l.race_title))}${l.popular ? `（${esc(l.popular)}人気）` : ''}</p>
      <p class="pv">次の出走が決まったら、ここに出ます</p>`;
  } else {
    body = `<p class="lead">★ 推し馬</p><a class="nm" href="${horseUrl(name)}">${esc(name)} ›</a><p class="pv">次の出走が決まったら、ここに出ます</p>`;
  }
  // 中にレースへのリンクがあるので、カード全体はリンクにせず、アイコンと馬名から馬カルテへ
  return `<div class="spot-card ${bgClass(name, kind === 'next' ? 'cream' : 'white')}">
    <a href="${horseUrl(name)}" aria-hidden="true" tabindex="-1">${avatar(name, 'lg')}</a><div class="sc-body">${body}</div></div>`;
}

function onboardingCard() {
  return `<div class="onboard">
    <p class="nm">推し馬を見つけよう</p>
    <p>出走表で馬名の横の ☆ を押すと、推し馬になります。次に走る日や結果がホームのいちばん上に出ます。</p>
    <a class="gbtn" href="entries.html">今週の重賞に出る馬から選ぶ</a>
  </div>`;
}

function stripCard(name, n, l) {
  const line = n
    ? `<span class="nx-l">▶ 次走</span><span class="nx-d">${dateLabel(n.kaisaibi)}</span><span class="small">${esc(n.kaisaijo)}${n.race_no}R</span>`
    : l ? `<span class="small">前走 ${dateLabel(l.kaisaibi)}<br>${esc(l.rank)}着</span>` : '<span class="small">出走予定なし</span>';
  return `<a class="mini ${bgClass(name, n ? 'cream' : 'white')}" href="${horseUrl(name)}">${avatar(name, 'sm')}<b>${esc(name)}</b>${line}</a>`;
}

// 最新の開催日の 11R（その日のメインレース）を、格の高い順に
async function mainRaces(days) {
  const d = days[0]?.kaisaibi;
  if (!d) return { d: null, races: [] };
  const rows = await query('race_results',
    `select=kaisaibi,kaisaijo,race_no,race_title,hassou_time,rank,horse_name,horse_number,waku,popular,jockey,win_odds&kaisaibi=eq.${d}&race_no=eq.11&order=id.asc`,
    settled(d) ? 12 * HOUR : 3 * MINUTE).catch(() => []);
  const byCourse = new Map();
  for (const r of rows) {
    if (!byCourse.has(r.kaisaijo)) byCourse.set(r.kaisaijo, []);
    byCourse.get(r.kaisaijo).push(r);
  }
  const races = [...byCourse.values()].sort((a, b) =>
    (gradeOf(a[0].race_title) || 9) - (gradeOf(b[0].race_title) || 9) || courseSort(a[0].kaisaijo, b[0].kaisaijo));
  return { d, races };
}

function mainRaceCard(rows) {
  const first = rows[0];
  const g = gradeOf(first.race_title);
  const top = [1, 2, 3].map(k => rows.find(r => r.rank === String(k))).filter(Boolean);
  return `<a class="mrace" href="results.html?d=${first.kaisaibi}&c=${enc(first.kaisaijo)}">
    <div class="mr-head mg${g || 0}">
      <p>${g ? `<span class="chip g${g}">G${g}</span>` : ''}${esc(first.kaisaijo)}11R</p>
      <p class="mr-name">${esc(listTitle(first.race_title).replace(GRADE, '').trim())}</p>
      ${courseLabel(first.hassou_time) ? `<p class="mr-meta">${esc(courseLabel(first.hassou_time))}${startTime(first.hassou_time) ? `・${esc(startTime(first.hassou_time))}発走` : ''}</p>` : ''}
    </div>
    <div class="mr-rows">${top.map(r => `<div class="mr-row">
      <span class="medal m${r.rank}">${esc(r.rank)}</span>${waku(r.waku) ? `<span class="waku w${esc(r.waku)}">${esc(r.horse_number)}</span>` : ''}
      <span class="mr-h"><b>${esc(r.horse_name)}</b><span class="small">${[r.popular && `${esc(r.popular)}番人気`, esc(r.jockey || '')].filter(Boolean).join('・')}</span></span>
      <span class="mr-odds${r.rank === '1' ? ' gold' : ''}">${r.win_odds ? `${esc(r.win_odds)}倍` : ''}</span></div>`).join('')}</div>
    <p class="mr-more">${esc(first.kaisaijo)}のレース結果をもっとみる ›</p>
  </a>`;
}

async function renderHome() {
  const today = todayJst();
  const list = favs();
  const [next, last, days] = await Promise.all([favNextRuns(list, today), favLastRuns(list), raceDays().catch(() => [])]);
  let html = '';
  if (!list.length) {
    html += onboardingCard();
  } else {
    const pick = pickSpotlight(list, next, last);
    html += spotlightCard(pick, next, last);
    const others = list.filter(n => n !== pick.name);
    html += `<div class="sec-head"><h2>★ ほかの推し馬</h2><a href="fav.html">すべて見る ›</a></div>
      <div class="strip">${others.map(n => stripCard(n, next.get(n), last[n])).join('')}
        <a class="mini add" href="entries.html"><span class="ava sm">＋</span><b>馬を追加</b><span class="small">出走表から選ぶ</span></a></div>`;
  }
  const main = await mainRaces(days);
  if (main.races.length) {
    html += `<h2 class="green">${longDate(main.d)}のメインレース</h2>${main.races.map(mainRaceCard).join('')}`;
  }
  app.innerHTML = html + cta();
}

// ---------- 出走表：今週の全レース ----------

async function renderEntries() {
  const today = todayJst();
  const entries = await queryAll('race_entries',
    `select=kaisaibi,kaisaijo,race_no,race_title,race_info&kaisaibi=gte.${today}&order=kaisaibi.asc,kaisaijo.asc,race_no.asc,id.asc`);
  const races = groupRaces(entries);
  let html = '';
  if (!races.length) {
    html += '<p class="notice">今週の出走表はまだ出ていません（例年、木曜ごろに出ます）。</p>';
  } else {
    const main = mainRace(races);
    if (main) {
      const g = gradeOf(main.race_title);
      html += `<a class="mrace" href="${raceUrl(main.kaisaibi, main.kaisaijo, main.race_no)}">
        <div class="mr-head mg${g}">
          <p><span class="chip g${g}">G${g}</span>今週の主役　${esc(daysLeft(main.kaisaibi, today))}</p>
          <p class="mr-name">${esc(main.race_title.replace(GRADE, '').trim())}</p>
          <p class="mr-meta">${dateLabel(main.kaisaibi)} ${esc(main.kaisaijo)}${main.race_no}R ${esc(startTime(main.race_info))}発走 ${esc(courseLabel(main.race_info))}</p>
        </div>
        <p class="mr-more">出走表を見る ›</p></a>`;
    }
    const graded = races.filter(r => isGraded(r.race_title) && r !== main);
    if (graded.length) {
      html += '<h2 class="green">今週の重賞</h2>' + graded.map(r => `
        <a class="card graded g${gradeOf(r.race_title)}" href="${raceUrl(r.kaisaibi, r.kaisaijo, r.race_no)}">
          <div class="name">${titleHtml(r.race_title)}</div>
          <div class="meta">${dateLabel(r.kaisaibi)} ${esc(r.kaisaijo)}${r.race_no}R ${esc(startTime(r.race_info))}発走 ${esc(courseLabel(r.race_info))}</div>
        </a>`).join('');
    }
    const days = [...new Set(races.map(r => r.kaisaibi))];
    for (const day of days) {
      const ofDay = races.filter(r => r.kaisaibi === day);
      for (const course of [...new Set(ofDay.map(r => r.kaisaijo))]) {
        html += `<h2 class="green">${dateLabel(day)} ${esc(course)}</h2>
          <div class="card">${raceList(ofDay.filter(r => r.kaisaijo === course))}</div>`;
      }
    }
  }
  app.innerHTML = html + cta('今週の出走表');
}

// ---------- レース：出馬表・結果・払い戻し ----------

async function renderRace() {
  const p = new URLSearchParams(location.search);
  // URL の値は画面のリンクや問い合わせに入るので、形が正しいものだけ受け付ける
  const d = p.get('d'), c = (p.get('c') || '').trim(), r = Number(p.get('r'));
  if (!/^\d{8}$/.test(d || '') || !c || !Number.isInteger(r) || r < 1 || r > 12) { location.replace('./'); return; }

  const [entries, results, payouts] = await Promise.all([
    query('race_entries', `select=*&${raceFilter(d, c, r)}&order=id.asc`).catch(() => []),
    query('race_results', `select=*&${raceFilter(d, c, r)}&order=id.asc`, settled(d) ? 12 * HOUR : 3 * MINUTE),
    query('race_payouts', `select=*&${raceFilter(d, c, r)}&order=sort.asc`, settled(d) ? 12 * HOUR : 3 * MINUTE).catch(() => []),
  ]);
  const head = entries[0] || results[0];
  if (!head) {
    app.innerHTML = `<p class="notice">このレースのデータが見つかりませんでした。</p>${cta()}`;
    return;
  }
  const title = (head.race_title || '').trim() || `${c}${r}R`;
  const info = head.race_info || head.hassou_time || '';
  document.title = `${title} ${results.length ? '結果' : '出馬表'} | 推し馬ノート`;

  setBarTitle(`${longDate(d)} ${c}${r}R`);
  let html = `<div class="card race-head">
    <h1>${titleHtml(title)}</h1>
    <p class="small">${esc([startTime(info) && `${startTime(info)}発走`, courseLabel(info)].filter(Boolean).join('　'))}</p>
  </div>`;

  const tab = p.get('tab') || (results.length ? 'result' : 'card');
  if (results.length && entries.length) {
    const link = t => `${raceUrl(d, c, r)}&tab=${t}`;
    html += `<div class="tabs">
      <a href="${link('result')}" class="${tab === 'result' ? 'on' : ''}">結果</a>
      <a href="${link('card')}" class="${tab === 'card' ? 'on' : ''}">出馬表</a></div>`;
  }

  if (tab === 'result' && results.length) {
    html += resultTable(results) + payoutTable(payouts);
  } else {
    const last = await lastRuns(entries.map(e => e.horse_name), d);
    html += entryTable(entries, last);
  }

  // 前後のレースへ
  const nav = [r > 1 ? `<a href="${raceUrl(d, c, r - 1)}">← ${r - 1}R</a>` : '',
    r < 12 ? `<a href="${raceUrl(d, c, r + 1)}">${r + 1}R →</a>` : ''];
  html += `<div class="tabs spread">${nav.join('')}</div>`;

  const what = results.length ? '結果' : '出馬表';
  app.innerHTML = html + cta(`${title}の${what}（${dateLabel(d)} ${c}${r}R）`);
  barWidths();
}

// 各馬の前走（このレースより前で一番新しいもの）
async function lastRuns(names, before) {
  const list = names.filter(Boolean);
  if (!list.length) return {};
  const inList = list.map(n => `"${n.replace(/"/g, '')}"`).join(',');
  const rows = await query('race_results',
    `select=horse_name,kaisaibi,kaisaijo,race_no,race_title,rank,popular&horse_name=in.(${enc(inList)})&kaisaibi=lt.${before}&order=kaisaibi.desc&limit=${list.length * 4}`, HOUR)
    .catch(() => []);
  const last = {};
  for (const row of rows) if (!last[row.horse_name]) last[row.horse_name] = row;
  return last;
}

function entryTable(entries, last) {
  if (!entries.length) return '<p class="notice">出馬表はまだありません。</p>';
  const hasOdds = entries.some(e => e.win_odds);
  const rows = [...entries].sort((a, b) => (Number(a.horse_number) || 99) - (Number(b.horse_number) || 99)).map(e => {
    const prev = last[e.horse_name];
    const prevText = prev ? `前走 ${fullDate(prev.kaisaibi)} ${prev.kaisaijo} ${(prev.race_title || '').trim()} ${prev.rank}着` : '';
    return `<tr class="${[e.scratched ? 'scratched' : '', isFav(e.horse_name) ? 'fav-row' : ''].join(' ').trim()}">
      <td class="c">${waku(e.waku)}<br>${esc(e.horse_number)}</td>
      <td>${horseLink(e.horse_name)} ${favButton(e.horse_name)} <span class="small">${esc(e.sex_age)}</span><br>
        <span class="small">${esc(e.card_jockey || e.jockey)} ${esc(e.weight)} / ${esc(e.trainer)}</span>
        ${prevText ? `<br><span class="small">${esc(prevText)}</span>` : ''}</td>
      ${hasOdds ? `<td class="num">${popBadge(e.popular)} ${esc(e.win_odds)}${oddsBar(e.win_odds)}</td>` : ''}
    </tr>`;
  }).join('');
  const updated = entries.find(e => e.odds_updated_at)?.odds_updated_at;
  const note = hasOdds && updated
    ? `<p class="small">オッズは ${new Date(updated).toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })} 時点</p>` : '';
  return `${legend(hasOdds)}<div class="card"><table>
    <tr><th class="c">馬番</th><th>馬名・騎手</th>${hasOdds ? '<th class="num">人気・単勝</th>' : ''}</tr>${rows}</table>${note}</div>`;
}

function resultTable(results) {
  const rows = results.map(x => `<tr class="${[['1', '2', '3'].includes(x.rank) ? 'top3' : '', isFav(x.horse_name) ? 'fav-row' : ''].join(' ').trim()}">
    <td class="c">${rankBadge(x.rank)}</td>
    <td class="c">${waku(x.waku)}<br>${esc(x.horse_number)}</td>
    <td>${horseLink(x.horse_name)} ${favButton(x.horse_name)} <span class="small">${esc(x.age)}</span><br>
      <span class="small">${esc(x.jockey)} ${esc(x.weight)}　${esc(x.time)} ${esc(x.tyakusa)}　上がり${esc(x.nobori)}</span></td>
    <td class="num">${popBadge(x.popular)} ${esc(x.win_odds)}${oddsBar(x.win_odds)}</td>
  </tr>`).join('');
  return `${legend()}<div class="card"><table>
    <tr><th class="c">着</th><th class="c">馬番</th><th>馬名・騎手</th><th class="num">人気・単勝</th></tr>${rows}</table></div>`;
}

function payoutTable(payouts) {
  if (!payouts.length) return '';
  const rows = payouts.map(x => `<tr><td>${esc(x.bet_type)}</td><td>${esc(x.numbers)}</td><td class="num">${Number(x.yen).toLocaleString()}円</td></tr>`).join('');
  return `<h2>払い戻し</h2><div class="card"><table class="pay">${rows}</table></div>`;
}

// ---------- 結果：開催日・競馬場ごとの全レース ----------

async function renderResults() {
  const p = new URLSearchParams(location.search);
  const days = await raceDays();
  if (!days.length) { app.innerHTML = `<p class="notice">結果がまだありません。</p>${cta()}`; return; }

  const dates = [...new Set(days.map(x => x.kaisaibi))];
  const d = dates.includes(p.get('d')) ? p.get('d') : dates[0];
  const courses = days.filter(x => x.kaisaibi === d).map(x => x.kaisaijo).sort(courseSort);
  const c = courses.includes(p.get('c')) ? p.get('c') : courses[0];

  const rows = await queryAll('race_results',
    `select=kaisaibi,kaisaijo,race_no,race_title,hassou_time,rank,horse_name,popular&kaisaibi=eq.${d}&kaisaijo=eq.${enc(c)}&order=race_no.asc,id.asc`,
    settled(d) ? 12 * HOUR : 3 * MINUTE);
  const races = groupRaces(rows).map(r => ({ ...r, race_info: r.hassou_time }));
  const winners = {};
  for (const row of rows) if (row.rank === '1' && !winners[row.race_no]) winners[row.race_no] = row;

  const go = (dd, cc) => `results.html?d=${dd}&c=${enc(cc)}`;
  let html = `<p><select id="date" aria-label="開催日">${dates.map(x => `<option value="${x}" ${x === d ? 'selected' : ''}>${x.slice(0, 4)}/${dateLabel(x)}</option>`).join('')}</select></p>
    <div class="tabs">${courses.map(x => `<a href="${go(d, x)}" class="${x === c ? 'on' : ''}">${esc(x)}</a>`).join('')}</div>
    <div class="card">${raceList(races, r => {
      const w = winners[r.race_no];
      return w ? `<br><span class="small">1着 ${esc(w.horse_name)}${w.popular ? `（${esc(w.popular)}人気）` : ''}</span>` : '';
    })}</div>`;
  app.innerHTML = html + cta(`${dateLabel(d)} ${c}のレース結果`);
  document.getElementById('date').addEventListener('change', e => {
    const nd = e.target.value;
    const keep = days.some(x => x.kaisaibi === nd && x.kaisaijo === c);
    location.href = keep ? go(nd, c) : `results.html?d=${nd}`;
  });
}

// ---------- 馬：過去のレース結果 ----------

async function renderHorse() {
  const name = (new URLSearchParams(location.search).get('n') || '').trim();
  // 馬名は問い合わせに入るので、長すぎるものや記号だけのものは受け付けない
  if (!validHorseName(name)) { location.replace('./'); return; }
  document.title = `${name}の過去レース結果 | 推し馬ノート`;

  const [runs, next] = await Promise.all([
    query('race_results', `select=kaisaibi,kaisaijo,race_no,race_title,hassou_time,rank,popular,win_odds,jockey,time,nobori,age,weight&horse_name=eq.${enc(name)}&order=kaisaibi.desc&limit=300`, HOUR),
    query('race_entries', `select=kaisaibi,kaisaijo,race_no,race_title,race_info,horse_number,jockey&horse_name=eq.${enc(name)}&kaisaibi=gte.${todayJst()}&order=kaisaibi.asc&limit=5`).catch(() => []),
  ]);
  setBarTitle(name);
  // 着順が数字のレースだけを数える（取消・除外などは外す）
  const ranked = runs.filter(x => /^\d+$/.test(x.rank || ''));
  const n = k => ranked.filter(x => Number(x.rank) === k).length;
  const [w, s, t] = [n(1), n(2), n(3)];
  const pct = v => (ranked.length ? `${Math.round((v / ranked.length) * 100)}%` : '-');
  // 近走の着順の丸（新しい順に5走。1〜3着は金銀銅）
  const recent = runs.slice(0, 5).map(x => `<span class="medal${/^[123]$/.test(x.rank) ? ` m${x.rank}` : ''}">${esc(x.rank)}</span>`).join('');
  let html = `<div class="karte">
    <div class="k-head">${avatar(name, 'lg')}<div>
      ${isFav(name) ? '<p class="k-lead">★ 推し馬</p>' : ''}<h1>${esc(name)}</h1>
      ${runs[0]?.age ? `<p class="k-lead">${esc(runs[0].age)}</p>` : ''}</div></div>
    <div class="k-stats">
      <div><b>${ranked.length}</b><span>出走</span></div><div><b>${w}</b><span>勝利</span></div>
      <div><b>${pct(w + s)}</b><span>連対率</span></div><div><b>${pct(w + s + t)}</b><span>複勝率</span></div>
    </div>
    ${recent ? `<p class="k-recent">近走 ${recent}</p>` : ''}
    ${favButton(name, true)}
  </div>`;

  html += next.map(e => `
    <a class="card nextcard" href="${raceUrl(e.kaisaibi, e.kaisaijo, e.race_no)}">
      <p class="nx-l">📅 次走</p>
      <p class="nm">${dateLabel(e.kaisaibi)} ${esc(e.kaisaijo)}${e.race_no}R ${titleHtml(listTitle(e.race_title))}</p>
      <p class="small">${[courseLabel(e.race_info), e.jockey].filter(Boolean).map(esc).join('　')}</p>
      <p class="link">出走表を見る ›</p></a>`).join('');
  const memo = memoOf(name);
  if (isFav(name) || memo) {
    html += `<a class="card memocard" href="fav.html"><p class="nx-l blue">📝 メモ</p>
      <p class="small">${memo ? esc(memo) : '気づいたことをメモしておけます（推し馬ページで書く）'}</p></a>`;
  }

  if (!runs.length) {
    html += '<p class="notice">この馬の過去のレース結果はまだありません。</p>';
  } else {
    html += `<h2 class="green">過去のレース</h2>
    <div class="card"><ul class="runs">${runs.slice(0, 60).map(x => `
      <li><a href="${raceUrl(x.kaisaibi, x.kaisaijo, x.race_no)}">
        ${rankBadge(x.rank)}
        <span class="title">${titleHtml(x.race_title)}<br>
          <span class="small">${fullDate(x.kaisaibi)} ${esc(x.kaisaijo)} ${esc(courseLabel(x.hassou_time))}　${esc(x.jockey)}　${esc(x.time)}</span></span>
        <span class="info">${x.popular ? `${esc(x.popular)}人気` : ''}</span>
      </a></li>`).join('')}</ul></div>`;
  }
  app.innerHTML = html + cta(`${name}の過去レース結果`);
}

// ---------- 推し馬ページ：メモ・着せ替え・シェア（どれもこの iPhone の中だけに保存） ----------

// 着せ替え。CSS の [data-theme] と、シェア画像の色（bg は上→下のグラデーション）。meta は theme.js と同じ色
const THEMES = {
  // deco: シェア画像の飾り（turf=芝のしま、petal=花びら、sparkle=きらきら、frame=金の額縁、stripe=勝負服のたすき）
  standard: { meta: '#4CAF50', label: 'スタンダード', bg: ['#1b5e20', '#43a047'], ink: '#ffffff', accent: '#fbc02d', card: 'rgba(255,255,255,.14)', deco: 'turf' },
  sakura: { meta: '#e75480', label: 'さくら（かわいい）', bg: ['#ffd1e1', '#ff8fb8'], ink: '#5a1030', accent: '#ffffff', card: 'rgba(255,255,255,.55)', deco: 'petal' },
  yume: { meta: '#9b6bd6', label: 'ゆめかわ', bg: ['#c9b6ff', '#9fe3f0'], ink: '#3a2766', accent: '#ffffff', card: 'rgba(255,255,255,.5)', deco: 'sparkle' },
  noir: { meta: '#0d0d0f', label: 'ブラック×ゴールド（かっこいい）', bg: ['#000000', '#2a2a30'], ink: '#f3e2a9', accent: '#d4af37', card: 'rgba(212,175,55,.12)', deco: 'frame' },
  navy: { meta: '#1a3d7c', label: '勝負服ネイビー', bg: ['#0f2552', '#1a3d7c'], ink: '#ffffff', accent: '#e53935', card: 'rgba(255,255,255,.12)', deco: 'stripe' },
};
const MARKS = ['★', '💖', '🔥', '👑', '🐎', '🌸', '⚡'];
const MEMO_KEY = 'favMemos';
const MEMO_MAX = 100;
const TITLE_MAX = 20;

function store(key, value) {
  try { localStorage.setItem(key, value); } catch (e) { /* 保存できない環境では、このページを閉じるまでだけ */ }
}
function load(key) {
  try { return localStorage.getItem(key); } catch (e) { return null; }
}
const currentTheme = () => (THEMES[load('theme')] ? load('theme') : 'standard');
const currentMark = () => (MARKS.includes(load('favMark')) ? load('favMark') : '★');
const favTitle = () => (load('favTitle') || '').slice(0, TITLE_MAX) || 'わたしの推し馬';
function memos() {
  try {
    const m = JSON.parse(load(MEMO_KEY) || '{}');
    return m && typeof m === 'object' && !Array.isArray(m) ? m : {};
  } catch (e) {
    return {};
  }
}
const memoOf = name => (typeof memos()[name] === 'string' ? memos()[name].slice(0, MEMO_MAX) : '');
function saveMemo(name, text) {
  const m = memos();
  if (text) m[name] = text.slice(0, MEMO_MAX); else delete m[name];
  store(MEMO_KEY, JSON.stringify(m));
}
function applyTheme(key) {
  if (key === 'standard') document.documentElement.removeAttribute('data-theme');
  else document.documentElement.setAttribute('data-theme', key);
  store('theme', key);
  const meta = document.querySelector('meta[name="theme-color"]');
  if (meta) meta.setAttribute('content', THEMES[key].meta);
}

const absUrl = path => new URL(path, location.href).href;
const raceLine = r => (r ? `${dateLabel(r.kaisaibi)} ${r.kaisaijo}${r.race_no}R ${listTitle(r.race_title)}` : '');
// X の投稿文。リンクは馬のページ（見た人も同じ成績が見られる）
function shareText(name, next) {
  const memo = memoOf(name);
  return [`${currentMark()} 推し馬：${name}`, memo, next ? `次走 ${raceLine(next)}` : '', '#推し馬 #競馬'].filter(Boolean).join('\n');
}
function listShareText(list) {
  return [`${currentMark()} ${favTitle()}`, ...list.slice(0, 8).map(n => `・${n}`), '#推し馬 #競馬'].join('\n');
}
function openX(text, url) {
  window.open(`https://x.com/intent/post?text=${enc(text)}&url=${enc(url)}`, '_blank', 'noopener');
}

// ---------- シェア用の画像（1080×1080 の正方形） ----------

// 1行に入るぶんずつ文字を切って、最大 maxLines 行にする
function wrapLines(ctx, text, width, maxLines) {
  const lines = [];
  for (const para of text.split('\n')) {
    let line = '';
    for (const ch of [...para]) {
      if (ctx.measureText(line + ch).width > width) { lines.push(line); line = ch; } else line += ch;
    }
    lines.push(line);
  }
  if (lines.length > maxLines) {
    lines.length = maxLines;
    lines[maxLines - 1] = [...lines[maxLines - 1]].slice(0, -1).join('') + '…'; // 絵文字を半分で切らない
  }
  return lines;
}
function roundRect(ctx, x, y, w, h, r) {
  ctx.beginPath();
  ctx.moveTo(x + r, y);
  ctx.arcTo(x + w, y, x + w, y + h, r);
  ctx.arcTo(x + w, y + h, x, y + h, r);
  ctx.arcTo(x, y + h, x, y, r);
  ctx.arcTo(x, y, x + w, y, r);
  ctx.closePath();
}
const FONT = '"Hiragino Sans", "Noto Sans JP", sans-serif';
// 着せ替えごとの背景の飾り。W×H の横長にも使う（位置は 1080 の正方形を基準に伸ばす）
function drawDeco(ctx, t, W, H = W) {
  const sx = W / 1080, sy = H / 1080;
  ctx.save();
  if (t.deco === 'turf') {
    // 芝刈りのしま模様
    ctx.fillStyle = 'rgba(255,255,255,.06)';
    for (let x = -H; x < W; x += 160) {
      ctx.beginPath(); ctx.moveTo(x, H); ctx.lineTo(x + 80, H); ctx.lineTo(x + 80 + H, 0); ctx.lineTo(x + H, 0); ctx.fill();
    }
  } else if (t.deco === 'stripe') {
    // 勝負服のたすき（赤白）
    // 右上の角だけを斜めに横切らせる（名前やメモにかからないように）
    ctx.translate(W * 0.8, -60);
    ctx.rotate(-Math.PI / 5);
    ctx.fillStyle = t.accent; ctx.fillRect(0, 0, 70, W * 1.6);
    ctx.fillStyle = 'rgba(255,255,255,.9)'; ctx.fillRect(70, 0, 22, W * 1.6);
  } else if (t.deco === 'petal' || t.deco === 'sparkle') {
    const mark = t.deco === 'petal' ? '🌸' : '✦';
    ctx.fillStyle = t.deco === 'petal' ? '#ffffff' : t.accent;
    ctx.textBaseline = 'middle';
    const spots = [[70, 150, 70], [960, 120, 90], [1010, 520, 54], [40, 600, 46], [930, 1010, 80], [110, 1010, 60], [540, 60, 40], [680, 1050, 44]];
    for (const [x, y, size] of spots) {
      ctx.globalAlpha = t.deco === 'petal' ? 0.55 : 0.7;
      ctx.font = `${size}px ${FONT}`;
      ctx.fillText(mark, x * sx - size / 2, y * sy);
    }
  } else if (t.deco === 'frame') {
    // 金の二重の額縁
    ctx.strokeStyle = t.accent;
    ctx.lineWidth = 6; ctx.strokeRect(24, 24, W - 48, H - 48);
    ctx.lineWidth = 2; ctx.strokeRect(40, 40, W - 80, H - 80);
  }
  ctx.restore();
  // うすく推しマーク
  ctx.save();
  ctx.globalAlpha = 0.1;
  ctx.fillStyle = t.accent;
  ctx.font = `220px ${FONT}`;
  ctx.textBaseline = 'alphabetic';
  ctx.fillText(currentMark(), W - 300, H - 150);
  ctx.restore();
}
// items: [{ name, memo, next }]。1頭なら大きく、複数ならリストにする
function drawCard(items) {
  const t = THEMES[currentTheme()];
  const S = 1080;
  const cv = document.createElement('canvas');
  cv.width = cv.height = S;
  const ctx = cv.getContext('2d');
  const g = ctx.createLinearGradient(0, 0, S * 0.4, S);
  g.addColorStop(0, t.bg[0]);
  g.addColorStop(1, t.bg[1]);
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, S, S);
  drawDeco(ctx, t, S);

  ctx.fillStyle = t.ink;
  ctx.textBaseline = 'top';
  ctx.font = `bold 44px ${FONT}`;
  ctx.fillText(`${currentMark()} ${favTitle()}`, 80, 80);
  ctx.fillStyle = t.accent;
  ctx.fillRect(80, 150, 120, 8);

  ctx.fillStyle = t.card;
  roundRect(ctx, 60, 200, S - 120, 720, 40);
  ctx.fill();
  ctx.fillStyle = t.ink;
  if (items.length === 1) {
    const { name, memo, next } = items[0];
    let size = 96;
    do ctx.font = `bold ${size -= 4}px ${FONT}`; while (size > 48 && ctx.measureText(name).width > S - 220);
    ctx.fillText(name, 110, 250 + (92 - size) / 2);
    let y = 400;
    if (next) {
      ctx.font = `bold 36px ${FONT}`;
      for (const l of wrapLines(ctx, `次走 ${raceLine(next)}`, S - 220, 2)) { ctx.fillText(l, 110, y); y += 50; }
      y += 20;
    }
    ctx.font = `44px ${FONT}`;
    for (const l of wrapLines(ctx, memo || 'がんばれ！', S - 220, next ? 6 : 8)) { ctx.fillText(l, 110, y); y += 62; }
  } else {
    let y = 240;
    for (const { name, memo } of items.slice(0, 7)) {
      ctx.font = `bold 48px ${FONT}`;
      ctx.fillText(`${currentMark()} ${name}`, 110, y);
      if (memo) {
        ctx.font = `30px ${FONT}`;
        ctx.globalAlpha = 0.85;
        ctx.fillText(wrapLines(ctx, memo, S - 260, 1)[0], 150, y + 58);
        ctx.globalAlpha = 1;
      }
      y += 96;
    }
  }
  ctx.font = `28px ${FONT}`;
  ctx.fillText('推し馬ノート　tktktakatsuka.github.io/kakoRaceKekkaSp', 80, 970);
  return cv;
}
// iPhone なら共有シート（写真に保存・X・LINE など）を出す。出せなければ画像をダウンロード
async function shareImage(items, text) {
  return shareCanvas(drawCard(items), text, 'oshiuma.png');
}
async function shareCanvas(cv, text, filename) {
  const blob = await new Promise(res => cv.toBlob(res, 'image/png'));
  if (!blob) return;
  const file = new File([blob], filename, { type: 'image/png' });
  if (navigator.canShare?.({ files: [file] })) {
    try { await navigator.share({ files: [file], text }); } catch (e) { /* 閉じただけ */ }
    return;
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = filename;
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 10e3);
}

async function renderFav() {
  const list = favs();
  const next = await favNextRuns(list, todayJst());
  const theme = currentTheme();
  const last = await favLastRuns(list);
  let html = '<a class="gbtn" href="entries.html">＋ 馬を探して推し馬にする</a>'
    + '<a class="door prof-door" href="prof.html"><span class="ic">📝</span><b>推し馬プロフ帳をつくる</b><span>推し馬・推し騎手・心に残るレースを書いて、Xに貼れる画像に</span></a>';

  // アプリと同じ並び：今週走る馬が先、そのあとほかの馬
  const card = name => {
    const n = next.get(name), l = last[name];
    const st = styleOf(name);
    return `
      <div class="oshi ${bgClass(name, n ? 'cream' : 'white')}" data-card="${esc(name)}">
        <div class="o-row">
          <span data-ava="${esc(name)}">${avatar(name)}</span>
          <div class="o-body">
            <a class="o-name" href="${horseUrl(name)}">${esc(name)} ›</a>
            ${n ? `<a class="nx" href="${raceUrl(n.kaisaibi, n.kaisaijo, n.race_no)}">▶ ${dateLabel(n.kaisaibi)} ${esc(n.kaisaijo)}${n.race_no}R<br><span>${titleHtml(listTitle(n.race_title))}${courseLabel(n.race_info) ? `（${esc(courseLabel(n.race_info))}）` : ''}</span></a>` : ''}
            ${l ? `<a class="pv" href="${raceUrl(l.kaisaibi, l.kaisaijo, l.race_no || 1)}">${prevLine(l)} ›</a>` : '<p class="pv">まだ結果がありません</p>'}
          </div>
          <div class="o-side">
            <button type="button" class="ic-btn" data-deco="${esc(name)}" aria-label="カードを飾る" aria-expanded="false">🎨</button>
            ${favButton(name)}
          </div>
        </div>
        <div class="deco" data-deco-panel="${esc(name)}" hidden>
          <p class="small">アイコン</p>
          <div class="pick">${CARD_ICONS.map(ic => `<button type="button" class="pick-ic${ic === st.icon ? ' on' : ''}" data-pick-icon="${esc(ic)}" data-for="${esc(name)}">${avatarOf(ic, 'sm')}</button>`).join('')}</div>
          <p class="small">背景</p>
          <div class="pick">${CARD_BGS.map(([k, label]) => `<button type="button" class="pick-bg bg-${k || 'white'}${k === st.bg ? ' on' : ''}" data-pick-bg="${k}" data-for="${esc(name)}">${esc(label)}</button>`).join('')}</div>
        </div>
        <textarea class="memo" data-memo="${esc(name)}" maxlength="${MEMO_MAX}" rows="2" placeholder="📝 メモ（例：次は東京2400mで巻き返す！）">${esc(memoOf(name))}</textarea>
        <div class="memo-foot"><span class="small" data-count="${esc(name)}">${[...memoOf(name)].length}/${MEMO_MAX}</span>
          <button type="button" class="pill x" data-share-x="${esc(name)}">Xでシェア</button>
          <button type="button" class="pill" data-share-img="${esc(name)}">画像でシェア</button></div>
      </div>`;
  };
  if (!list.length) {
    html += '<div class="onboard"><p class="nm">まだ推し馬がいません</p><p>出走表や馬のページで馬名の横の ☆ を押すと、ここに並びます。</p></div>';
  } else {
    const running = list.filter(n => next.get(n));
    const rest = list.filter(n => !next.get(n));
    if (running.length) html += `<h2 class="green">今週出走（${running.length}頭）</h2>${running.map(card).join('')}`;
    if (rest.length) html += `<h2 class="green">${running.length ? 'ほかの推し馬' : '推し馬'}（${rest.length}頭）</h2>${rest.map(card).join('')}`;
    html += `<div class="sharebar">
      <button type="button" class="btn x" data-share-list="x">推し馬リストをXでシェア</button>
      <button type="button" class="btn" data-share-list="img">リストを画像でシェア</button></div>`;
  }

  html += `<details class="card dress"><summary>🎨 ページの着せ替え・シェア画像</summary>
    <p class="small">ページ全体の色</p>
    <div class="themes">${Object.entries(THEMES).map(([k, t]) => `
      <button type="button" class="swatch sw-${k}${k === theme ? ' on' : ''}" data-theme-pick="${k}" aria-pressed="${k === theme}">${esc(t.label)}</button>`).join('')}</div>
    <p class="small">推しマーク</p>
    <div class="marks">${MARKS.map(m => `<button type="button" class="mark${m === currentMark() ? ' on' : ''}" data-mark-pick="${esc(m)}">${esc(m)}</button>`).join('')}</div>
    <label class="small" for="favTitle">ページの名前（${TITLE_MAX}文字まで）</label>
    <input id="favTitle" class="text" maxlength="${TITLE_MAX}" value="${esc(load('favTitle') || '')}" placeholder="わたしの推し馬">
    ${list.length ? '<p class="small">シェア画像のプレビュー</p><div class="preview" id="preview"></div>' : ''}
  </details>
  <p class="small">推し馬・メモ・飾りはこの iPhone の中だけに保存されます（ほかの人には見えません。シェアしたときだけ公開されます）。</p>`;
  app.innerHTML = html + cta();

  const sel = (attr, v) => [...app.querySelectorAll(`[${attr}]`)].find(el => el.getAttribute(attr) === v);
  // 着せ替え・マーク・名前・メモを変えると、シェア画像の見本もすぐ変わる（打っている間は少し待ってから描く）
  const preview = document.getElementById('preview');
  let timer = 0;
  const redraw = () => {
    if (!preview) return;
    clearTimeout(timer);
    timer = setTimeout(() => {
      const now = favs();
      const items = now.length === 1
        ? [{ name: now[0], memo: memoOf(now[0]), next: next.get(now[0]) }]
        : now.map(name => ({ name, memo: memoOf(name) }));
      preview.replaceChildren(items.length ? drawCard(items) : '');
    }, 150);
  };
  redraw();
  app.addEventListener('input', e => {
    const name = e.target.dataset?.memo;
    if (name) {
      saveMemo(name, e.target.value);
      const c = sel('data-count', name);
      if (c) c.textContent = `${[...e.target.value].length}/${MEMO_MAX}`;
    } else if (e.target.id === 'favTitle') {
      store('favTitle', e.target.value.slice(0, TITLE_MAX));
    }
    redraw();
  });
  app.addEventListener('click', e => {
    const b = e.target.closest('button');
    if (!b) return;
    const d = b.dataset;
    if (d.themePick && THEMES[d.themePick]) {
      applyTheme(d.themePick);
      for (const s of app.querySelectorAll('[data-theme-pick]')) {
        s.classList.toggle('on', s === b);
        s.setAttribute('aria-pressed', String(s === b));
      }
    } else if (d.markPick && MARKS.includes(d.markPick)) {
      store('favMark', d.markPick);
      for (const s of app.querySelectorAll('[data-mark-pick]')) s.classList.toggle('on', s === b);
    } else if (d.deco) {
      const panel = sel('data-deco-panel', d.deco);
      panel.hidden = !panel.hidden;
      b.setAttribute('aria-expanded', String(!panel.hidden));
    } else if (d.for && (d.pickIcon !== undefined || d.pickBg !== undefined)) {
      // 飾りを選んだら、その場でカードに当てる
      const name = d.for;
      const st = styleOf(name);
      if (d.pickIcon !== undefined && CARD_ICONS.includes(d.pickIcon)) st.icon = d.pickIcon;
      if (d.pickBg !== undefined && CARD_BGS.some(([k]) => k === d.pickBg)) st.bg = d.pickBg;
      saveStyle(name, st);
      const cardEl = sel('data-card', name);
      const fallback = next.get(name) ? 'cream' : 'white';
      cardEl.className = `oshi ${bgClass(name, fallback)}`;
      sel('data-ava', name).innerHTML = avatar(name);
      for (const x of cardEl.querySelectorAll('[data-pick-icon]')) x.classList.toggle('on', x.dataset.pickIcon === st.icon);
      for (const x of cardEl.querySelectorAll('[data-pick-bg]')) x.classList.toggle('on', x.dataset.pickBg === st.bg);
    }
    if (d.themePick || d.markPick || d.fav) redraw(); else if (d.shareX) {
      openX(shareText(d.shareX, next.get(d.shareX)), absUrl(horseUrl(d.shareX)));
    } else if (d.shareImg) {
      const name = d.shareImg;
      shareImage([{ name, memo: memoOf(name), next: next.get(name) }], `${shareText(name, next.get(name))}\n${absUrl(horseUrl(name))}`);
    } else if (d.shareList) {
      const now = favs();
      if (d.shareList === 'x') openX(listShareText(now), absUrl('./'));
      else shareImage(now.map(name => ({ name, memo: memoOf(name) })), `${listShareText(now)}\n${absUrl('./')}`);
    }
  });
}

// ---------- 推し馬プロフ帳（書きこんで、Xに貼れる横長の画像にする。この iPhone に保存） ----------

const PROF_KEY = 'profile';
const PROF_PHOTO_KEY = 'profPhoto';
// [保存名, 画面の見出し, 最大文字数, 複数行か, 入力例]
const PROF_FIELDS = [
  ['name', '名前（アカウント名）', 12, false, '例：うまこ'],
  ['career', '競馬歴・きっかけ', 60, true, '例：3年。ダービーを現地で見てから沼に'],
  ['oshi1', '現役の推し 1', 12, false, ''],
  ['oshi2', '現役の推し 2', 12, false, ''],
  ['oshi3', '現役の推し 3', 12, false, ''],
  ['more', '気になる馬・書ききれなかった推し', 50, true, '例：2歳の〇〇、地方の〇〇 etc...'],
  ['jockey', '推し騎手・厩舎・産駒など', 40, false, '例：〇〇騎手、〇〇厩舎'],
  ['love', '永遠のラブ（引退馬）', 40, true, '例：〇〇、〇〇'],
  ['race', '心に残っているレース', 50, true, '例：2023年 有馬記念'],
  ['wish', 'ひとつ願いが叶うなら？', 40, false, '例：推しの口取り写真に入りたい'],
  ['comment', 'ひとこと', 50, true, '例：無言フォロー大歓迎です！'],
];
const PROF_SEX = ['牡', '牝', 'セン', 'ヒミツ'];
const PROF_TREND = 5; // ツイートの傾向：日常(0) 〜 馬(4)

function profile() {
  let p = {};
  try { p = JSON.parse(load(PROF_KEY) || 'null'); } catch (e) { p = null; }
  if (!p || typeof p !== 'object' || Array.isArray(p)) {
    // はじめて開いたときは、推し馬の上から3頭を入れておく
    const f = favs();
    p = { oshi1: f[0] || '', oshi2: f[1] || '', oshi3: f[2] || '', trend: 3 };
  }
  const out = {};
  for (const [k, , max] of PROF_FIELDS) out[k] = typeof p[k] === 'string' ? p[k].slice(0, max) : '';
  out.sex = PROF_SEX.includes(p.sex) ? p.sex : '';
  out.trend = Number.isInteger(p.trend) && p.trend >= 0 && p.trend < PROF_TREND ? p.trend : 3;
  out.icon = p.icon === 'photo' || CARD_ICONS.includes(p.icon) ? p.icon : '';
  return out;
}

// 写真は 360px の正方形に縮めて保存する（CSP で data: の画像は読めないので、Blob に戻してから描く）
async function loadPhoto() {
  const url = load(PROF_PHOTO_KEY);
  if (!url || !url.startsWith('data:image/jpeg;base64,')) return null;
  try {
    const bin = atob(url.slice(url.indexOf(',') + 1));
    const bytes = Uint8Array.from(bin, ch => ch.charCodeAt(0));
    return await createImageBitmap(new Blob([bytes], { type: 'image/jpeg' }));
  } catch (e) {
    return null;
  }
}
const PHOTO_MAX_BYTES = 30e6; // これより大きい写真は、縮める前に読みこむだけで固まることがあるので断る
async function photoFromFile(file) {
  if (file.size > PHOTO_MAX_BYTES) throw new Error('too large');
  const src = await createImageBitmap(file);
  const side = Math.min(src.width, src.height);
  const cv = document.createElement('canvas');
  cv.width = cv.height = 360;
  cv.getContext('2d').drawImage(src, (src.width - side) / 2, (src.height - side) / 2, side, side, 0, 0, 360, 360);
  const bmp = await createImageBitmap(cv);
  store(PROF_PHOTO_KEY, cv.toDataURL('image/jpeg', 0.85)); // 描けるとわかってから保存する
  return bmp;
}
let horseFace = null;
async function loadHorseFace() {
  if (horseFace) return horseFace;
  const img = new Image();
  img.src = 'assets/icon-192.png?v=13';
  try { await img.decode(); horseFace = img; } catch (e) { /* 読めなければ絵文字にする */ }
  return horseFace;
}

// 色 bg の上で読みやすいほう（明るさの差が大きいほう）の色を返す
function luminance(hex) {
  const n = parseInt(hex.slice(1), 16);
  const ch = v => { v /= 255; return v <= 0.03928 ? v / 12.92 : ((v + 0.055) / 1.055) ** 2.4; };
  return 0.2126 * ch(n >> 16) + 0.7152 * ch((n >> 8) & 255) + 0.0722 * ch(n & 255);
}
function readableOn(bg, a, b) {
  const ratio = c => { const x = luminance(bg), y = luminance(c); return (Math.max(x, y) + 0.05) / (Math.min(x, y) + 0.05); };
  return ratio(a) >= ratio(b) ? a : b;
}

// 枠の大きさに収まるまで文字を小さくする
function fitText(ctx, text, w, h, max, min, weight = '') {
  for (let size = max; size >= min; size -= 2) {
    ctx.font = `${weight}${size}px ${FONT}`;
    const lines = wrapLines(ctx, text, w, 99);
    if (lines.length * size * 1.3 <= h) return { lines, lh: size * 1.3 };
  }
  ctx.font = `${weight}${min}px ${FONT}`;
  return { lines: wrapLines(ctx, text, w, Math.max(1, Math.floor(h / (min * 1.3)))), lh: min * 1.3 };
}
function profBox(ctx, t, x, y, w, h, label, value, max = 36) {
  ctx.fillStyle = t.card;
  roundRect(ctx, x, y, w, h, 22);
  ctx.fill();
  ctx.fillStyle = t.ink;
  ctx.globalAlpha = 0.75;
  ctx.font = `bold 22px ${FONT}`;
  ctx.fillText(label, x + 20, y + 16);
  ctx.globalAlpha = 1;
  if (!value) return;
  const { lines, lh } = fitText(ctx, value, w - 40, h - 64, max, 20);
  lines.forEach((l, i) => ctx.fillText(l, x + 20, y + 52 + i * lh));
}

// 1600×900 の横長（X のタイムラインで切れない比率）
function drawProfile(p, photo, face) {
  const t = THEMES[currentTheme()];
  const W = 1600, H = 900;
  const cv = document.createElement('canvas');
  cv.width = W;
  cv.height = H;
  const ctx = cv.getContext('2d');
  const g = ctx.createLinearGradient(0, 0, W * 0.5, H);
  g.addColorStop(0, t.bg[0]);
  g.addColorStop(1, t.bg[1]);
  ctx.fillStyle = g;
  ctx.fillRect(0, 0, W, H);
  if (t.deco === 'frame') {
    // 額縁とたすきは中身にかからないよう、横長では外側の細い線・下のふちにする
    ctx.strokeStyle = t.accent;
    ctx.lineWidth = 4; ctx.strokeRect(10, 10, W - 20, H - 20);
    ctx.lineWidth = 1.5; ctx.strokeRect(20, 20, W - 40, H - 40);
    drawDeco(ctx, { ...t, deco: '' }, W, H);
  } else if (t.deco === 'stripe') {
    ctx.fillStyle = t.accent; ctx.fillRect(0, H - 16, W, 16);
    ctx.fillStyle = 'rgba(255,255,255,.9)'; ctx.fillRect(0, H - 22, W, 6);
    drawDeco(ctx, { ...t, deco: '' }, W, H);
  } else {
    drawDeco(ctx, t, W, H);
  }
  ctx.textBaseline = 'top';

  // 見出し
  ctx.fillStyle = t.ink;
  ctx.font = `bold 46px ${FONT}`;
  ctx.fillText(`${currentMark()} 推し馬プロフ帳`, 44, 38);
  ctx.fillStyle = t.accent;
  ctx.fillRect(44, 100, 120, 6);
  ctx.fillStyle = t.ink;
  ctx.font = `bold 26px ${FONT}`;
  ctx.textAlign = 'right';
  ctx.fillText('馬好きさんと繋がりたい', W - 44, 56);
  ctx.textAlign = 'left';

  // 左：アイコン・名前・性別・ツイートの傾向
  const L = 40, LW = 480;
  ctx.fillStyle = t.card;
  roundRect(ctx, L, 130, LW, 470, 22);
  ctx.fill();
  const cx = L + LW / 2, cy = 235, r = 90;
  ctx.save();
  ctx.beginPath();
  ctx.arc(cx, cy, r, 0, Math.PI * 2);
  ctx.fillStyle = '#ffffff';
  ctx.fill();
  ctx.clip();
  if (p.icon === 'photo' && photo) {
    ctx.drawImage(photo, cx - r, cy - r, r * 2, r * 2);
  } else if (p.icon && p.icon !== 'photo') {
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = `110px ${FONT}`;
    ctx.fillText(p.icon, cx, cy + 6);
  } else if (face) {
    ctx.drawImage(face, cx - r, cy - r, r * 2, r * 2);
  } else {
    // 写真や馬の顔が読めなかったときは絵文字にする
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.font = `110px ${FONT}`;
    ctx.fillText('🐴', cx, cy + 6);
  }
  ctx.restore();
  ctx.strokeStyle = t.accent;
  ctx.lineWidth = 6;
  ctx.beginPath();
  ctx.arc(cx, cy, r + 3, 0, Math.PI * 2);
  ctx.stroke();

  ctx.fillStyle = t.ink;
  ctx.textBaseline = 'top';
  ctx.globalAlpha = 0.75;
  ctx.font = `bold 22px ${FONT}`;
  ctx.fillText('名前', L + 30, 350);
  ctx.globalAlpha = 1;
  if (p.name) {
    let size = 64;
    do ctx.font = `bold ${size -= 4}px ${FONT}`; while (size > 32 && ctx.measureText(p.name).width > LW - 60);
    ctx.fillText(p.name, L + 30, 380 + (60 - size) / 2);
  }
  ctx.fillRect(L + 30, 452, LW - 60, 2);

  // 性別（選んだものに丸をつける）
  ctx.font = `bold 26px ${FONT}`;
  let x = L + 30;
  for (const s of PROF_SEX) {
    const w = ctx.measureText(s).width + 28;
    ctx.globalAlpha = p.sex === s ? 1 : 0.45;
    ctx.fillText(s, x + 14, 478);
    if (p.sex === s) {
      ctx.strokeStyle = t.ink;
      ctx.lineWidth = 3;
      roundRect(ctx, x, 470, w, 44, 22);
      ctx.stroke();
    }
    x += w + 12;
  }
  ctx.globalAlpha = 1;

  ctx.globalAlpha = 0.75;
  ctx.font = `bold 22px ${FONT}`;
  ctx.fillText('ツイートの傾向', L + 30, 528);
  ctx.globalAlpha = 1;
  ctx.font = `bold 24px ${FONT}`;
  ctx.fillText('日常', L + 30, 556);
  ctx.textAlign = 'right';
  ctx.fillText('馬', L + LW - 30, 556);
  ctx.textAlign = 'left';
  const x0 = L + 110, x1 = L + LW - 70, ly = 569;
  ctx.fillRect(x0, ly - 2, x1 - x0, 4);
  for (let i = 0; i < PROF_TREND; i++) {
    const tx = x0 + ((x1 - x0) * i) / (PROF_TREND - 1);
    ctx.fillRect(tx - 2, ly - 10, 4, 20);
  }
  ctx.fillStyle = t.accent;
  ctx.beginPath();
  ctx.arc(x0 + ((x1 - x0) * p.trend) / (PROF_TREND - 1), ly, 15, 0, Math.PI * 2);
  ctx.fill();
  ctx.strokeStyle = t.ink;
  ctx.lineWidth = 3;
  ctx.stroke();

  profBox(ctx, t, L, 615, LW, 235, '競馬歴・きっかけ', p.career, 34);

  // 右：推し3頭と、そのほかの欄
  const R = 550, RW = 1010;
  ctx.fillStyle = t.card;
  roundRect(ctx, R, 130, RW, 200, 22);
  ctx.fill();
  ctx.fillStyle = t.ink;
  ctx.globalAlpha = 0.75;
  ctx.font = `bold 22px ${FONT}`;
  ctx.fillText('現役の推し 1・2・3', R + 20, 146);
  ctx.globalAlpha = 1;
  const slotW = (RW - 40 - 40) / 3;
  [p.oshi1, p.oshi2, p.oshi3].forEach((name, i) => {
    const sx = R + 20 + i * (slotW + 20);
    ctx.fillStyle = t.card;
    roundRect(ctx, sx, 190, slotW, 120, 60);
    ctx.fill();
    ctx.fillStyle = t.accent;
    ctx.beginPath();
    ctx.arc(sx + 40, 250, 24, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = readableOn(t.accent, t.ink, t.bg[0]);
    ctx.font = `bold 28px ${FONT}`;
    ctx.textAlign = 'center';
    ctx.textBaseline = 'middle';
    ctx.fillText(String(i + 1), sx + 40, 251);
    ctx.fillStyle = t.ink;
    ctx.textAlign = 'left';
    if (name) {
      let size = 44;
      do ctx.font = `bold ${size -= 2}px ${FONT}`; while (size > 22 && ctx.measureText(name).width > slotW - 90);
      ctx.fillText(name, sx + 76, 252);
    }
    ctx.textBaseline = 'top';
  });

  const half = (RW - 20) / 2, third = (RW - 40) / 3;
  profBox(ctx, t, R, 345, half, 150, '気になる馬・書ききれなかった推し', p.more, 32);
  profBox(ctx, t, R + half + 20, 345, half, 150, '推し騎手・厩舎・産駒など', p.jockey, 34);
  profBox(ctx, t, R, 510, third, 170, '永遠のラブ（引退馬）', p.love, 32);
  profBox(ctx, t, R + third + 20, 510, third, 170, '心に残っているレース', p.race, 32);
  profBox(ctx, t, R + (third + 20) * 2, 510, third, 170, 'ひとつ願いが叶うなら？', p.wish, 32);
  profBox(ctx, t, R, 695, RW, 120, 'ひとこと', p.comment, 34);

  ctx.fillStyle = t.ink;
  ctx.font = `bold 24px ${FONT}`;
  ctx.fillText('#推し馬プロフ帳', R + 4, 838);
  ctx.globalAlpha = 0.8;
  ctx.font = `22px ${FONT}`;
  ctx.textAlign = 'right';
  ctx.fillText('推し馬ノートで作成　tktktakatsuka.github.io/kakoRaceKekkaSp', W - 40, 840);
  ctx.textAlign = 'left';
  ctx.globalAlpha = 1;
  return cv;
}

function profShareText(p) {
  const oshi = [p.oshi1, p.oshi2, p.oshi3].filter(Boolean);
  return [`${p.name ? `${p.name}の` : ''}推し馬プロフ帳を書きました🐎`, oshi.length ? `現役の推し：${oshi.join('・')}` : '',
    '馬好きさん、よろしくお願いします！', '#推し馬プロフ帳 #馬好きさんと繋がりたい #競馬好きさんと繋がりたい'].filter(Boolean).join('\n');
}

async function renderProf() {
  const p = profile();
  const theme = currentTheme();
  let [photo, face] = await Promise.all([loadPhoto(), loadHorseFace()]);
  const field = ([k, label, max, multi, ph]) => {
    const id = `pf-${k}`;
    const attrs = `id="${id}" class="${multi ? 'memo' : 'text'}" data-prof="${k}" maxlength="${max}" placeholder="${esc(ph)}"${k.startsWith('oshi') ? ' list="favList"' : ''}`;
    return `<label class="small" for="${id}">${esc(label)}</label>
      ${multi ? `<textarea ${attrs} rows="2">${esc(p[k])}</textarea>` : `<input ${attrs} value="${esc(p[k])}">`}`;
  };
  const iconPick = ['', ...CARD_ICONS.filter(Boolean)].map(ic => `<button type="button" class="pick-ic${p.icon === ic ? ' on' : ''}" data-prof-icon="${esc(ic)}">${avatarOf(ic, 'sm')}</button>`).join('')
    + `<label class="pick-ic photo${p.icon === 'photo' ? ' on' : ''}" data-prof-photo>📷<input type="file" accept="image/*" id="pf-photo" hidden></label>`;
  const html = `
    <p class="small">書いたところから、下の画像がすぐ変わります。できたら「画像を保存・シェア」から X を選ぶと、画像つきで投稿できます。</p>
    <div class="preview prof-preview" id="preview"></div>
    <div class="sharebar">
      <button type="button" class="btn" data-prof-share="img">画像を保存・シェア</button>
      <button type="button" class="btn x" data-prof-share="x">Xに投稿する文を開く</button></div>
    <div class="card prof-form">
      <p class="small">アイコン（📷 で写真も使えます）</p>
      <div class="pick">${iconPick}</div>
      ${field(PROF_FIELDS[0])}
      <p class="small">性別（ネタ枠です）</p>
      <div class="pick">${PROF_SEX.map(s => `<button type="button" class="pill${p.sex === s ? ' on' : ''}" data-prof-sex="${esc(s)}" aria-pressed="${p.sex === s}">${esc(s)}</button>`).join('')}</div>
      <label class="small" for="pf-trend">ツイートの傾向（左が日常、右が馬）</label>
      <input type="range" id="pf-trend" class="range" min="0" max="${PROF_TREND - 1}" step="1" value="${p.trend}" data-prof-trend>
      ${PROF_FIELDS.slice(1).map(field).join('')}
      <datalist id="favList">${favs().map(n => `<option value="${esc(n)}"></option>`).join('')}</datalist>
    </div>
    <details class="card dress"><summary>🎨 画像の色・推しマーク</summary>
      <div class="themes">${Object.entries(THEMES).map(([k, t]) => `
        <button type="button" class="swatch sw-${k}${k === theme ? ' on' : ''}" data-theme-pick="${k}" aria-pressed="${k === theme}">${esc(t.label)}</button>`).join('')}</div>
      <p class="small">推しマーク</p>
      <div class="marks">${MARKS.map(m => `<button type="button" class="mark${m === currentMark() ? ' on' : ''}" data-mark-pick="${esc(m)}">${esc(m)}</button>`).join('')}</div>
    </details>
    <p class="small">書いた内容と写真はこの iPhone の中だけに保存されます（シェアしたときだけ公開されます）。</p>`;
  app.innerHTML = html + cta();

  const preview = document.getElementById('preview');
  let timer = 0;
  let iconPicks = 0; // 写真を読みこんでいる間に別のアイコンが選ばれたら、読み終わった写真は使わない
  const save = () => store(PROF_KEY, JSON.stringify(p));
  const redraw = () => {
    clearTimeout(timer);
    timer = setTimeout(() => preview.replaceChildren(drawProfile(p, photo, face)), 150);
  };
  preview.replaceChildren(drawProfile(p, photo, face));

  app.addEventListener('input', e => {
    const k = e.target.dataset?.prof;
    if (k) p[k] = e.target.value.slice(0, e.target.maxLength);
    else if (e.target.id === 'pf-trend') p.trend = Number(e.target.value);
    else return;
    save();
    redraw();
  });
  document.getElementById('pf-photo').addEventListener('change', async e => {
    const file = e.target.files?.[0];
    if (!file) return;
    const pick = ++iconPicks;
    let bmp;
    try {
      bmp = await photoFromFile(file);
    } catch (err) {
      if (pick === iconPicks) alert('この写真は読みこめませんでした。別の写真を選んでください。');
      return;
    }
    if (pick !== iconPicks) return;
    photo = bmp;
    p.icon = 'photo';
    for (const x of app.querySelectorAll('.pick-ic')) x.classList.toggle('on', x.hasAttribute('data-prof-photo'));
    save();
    redraw();
  });
  app.addEventListener('click', e => {
    const b = e.target.closest('button');
    if (!b) return;
    const d = b.dataset;
    if (d.profIcon !== undefined && (d.profIcon === '' || CARD_ICONS.includes(d.profIcon))) {
      p.icon = d.profIcon;
      iconPicks++;
      for (const x of app.querySelectorAll('.pick-ic')) x.classList.toggle('on', x === b);
    } else if (d.profSex) {
      p.sex = p.sex === d.profSex ? '' : d.profSex; // もう一度押すと外せる
      for (const x of app.querySelectorAll('[data-prof-sex]')) {
        x.classList.toggle('on', x.dataset.profSex === p.sex);
        x.setAttribute('aria-pressed', String(x.dataset.profSex === p.sex));
      }
    } else if (d.themePick && THEMES[d.themePick]) {
      applyTheme(d.themePick);
      for (const s of app.querySelectorAll('[data-theme-pick]')) {
        s.classList.toggle('on', s === b);
        s.setAttribute('aria-pressed', String(s === b));
      }
    } else if (d.markPick && MARKS.includes(d.markPick)) {
      store('favMark', d.markPick);
      for (const s of app.querySelectorAll('[data-mark-pick]')) s.classList.toggle('on', s === b);
    } else if (d.profShare === 'img') {
      shareCanvas(drawProfile(p, photo, face), `${profShareText(p)}\n${absUrl('prof.html')}`, 'oshiuma-profile.png');
      return;
    } else if (d.profShare === 'x') {
      openX(profShareText(p), absUrl('prof.html'));
      return;
    } else {
      return;
    }
    save();
    redraw();
  });
}

// 上のバーの ←：同じサイトから来たときは前の画面へ、そうでなければホームへ（アプリの戻ると同じ動き）
document.querySelector('.top .back')?.addEventListener('click', e => {
  if (document.referrer.startsWith(location.origin) && history.length > 1) {
    e.preventDefault();
    history.back();
  }
});

const pages = { home: renderHome, entries: renderEntries, race: renderRace, results: renderResults, horse: renderHorse, fav: renderFav, prof: renderProf };
(pages[app.dataset.page] || renderHome)().catch(showError);
