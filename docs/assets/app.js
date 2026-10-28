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
const favText = (on, big) => (big ? (on ? '★ 推し馬に登録中' : '☆ 推し馬に登録') : (on ? '★' : '☆'));
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

// ---------- トップ：今週の出馬表 ----------

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
const nextRunHtml = r => (r
  ? `<a href="${raceUrl(r.kaisaibi, r.kaisaijo, r.race_no)}">${dateLabel(r.kaisaibi)} ${esc(r.kaisaijo)}${r.race_no}R ${titleHtml(listTitle(r.race_title))}</a>`
  : '<span class="small">今週の出走予定なし</span>');

// 推し馬の今週の出走予定。推し馬がいなければ登録のしかたを案内する
async function favSection(today) {
  const list = favs();
  if (!list.length) {
    return '<div class="card hint">☆ 推し馬を登録すると、ここに今週の出走予定が出ます。出馬表や馬のページで馬名の横の ☆ を押してください。</div>';
  }
  const next = await favNextRuns(list, today);
  const rows = list.map(name => `<li><span class="fav on">★</span> ${horseLink(name)}<br>${nextRunHtml(next.get(name))}</li>`).join('');
  return `<h2>${esc(currentMark())} 推し馬の出走予定</h2><div class="card"><ul class="favs">${rows}</ul>
    <a class="more" href="fav.html">メモ・着せ替え・シェアは推し馬ページへ ›</a></div>`;
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

// トップのいちばん上：今週の主役のレースと、「今週のレース」「レース結果」への大きな入り口
function homeTop(races, today, latest) {
  const main = mainRace(races);
  const days = [...new Set(races.map(r => r.kaisaibi))];
  let html = '';
  if (main) {
    const g = gradeOf(main.race_title);
    html += `<a class="hero spot g${g}" href="${raceUrl(main.kaisaibi, main.kaisaijo, main.race_no)}">
      <p class="when"><span class="tag">今週の主役</span> ${esc(daysLeft(main.kaisaibi, today))}</p>
      <h1>${titleHtml(main.race_title)}</h1>
      <p class="when">${dateLabel(main.kaisaibi)} ${esc(main.kaisaijo)}${main.race_no}R ${esc(startTime(main.race_info))}発走 ${esc(courseLabel(main.race_info))}</p>
      <span class="go">出馬表を見る ›</span>
    </a>`;
  }
  html += `<div class="doors">
    <a class="door week" href="#week"><span class="ic">🏇</span><b>今週のレース</b>
      <span>${races.length ? `${days.map(dateLabel).join('・')}<br>全${races.length}レース` : '木曜ごろに出ます'}</span></a>
    <a class="door result" href="${latest ? `results.html?d=${latest}` : 'results.html'}"><span class="ic">🏁</span><b>レース結果</b>
      <span>${latest ? `${dateLabel(latest)}の結果<br>払い戻しも` : '直近の結果'}</span></a>
  </div>`;
  return html;
}

async function renderHome() {
  const today = todayJst();
  const [entries, days] = await Promise.all([
    queryAll('race_entries',
      `select=kaisaibi,kaisaijo,race_no,race_title,race_info&kaisaibi=gte.${today}&order=kaisaibi.asc,kaisaijo.asc,race_no.asc,id.asc`),
    raceDays().catch(() => []),
  ]);
  const races = groupRaces(entries);
  let html = homeTop(races, today, days[0]?.kaisaibi);
  html += await favSection(today);
  html += '<h1 id="week" class="sec">🏇 今週のレース</h1>';

  if (!races.length) {
    html += '<p class="notice">今週の出馬表はまだ出ていません（例年、木曜ごろに出ます）。</p>';
  } else {
    const graded = races.filter(r => isGraded(r.race_title));
    if (graded.length) {
      html += '<h2>重賞</h2>' + graded.map(r => `
        <a class="card graded g${gradeOf(r.race_title)}" href="${raceUrl(r.kaisaibi, r.kaisaijo, r.race_no)}">
          <div class="name">${titleHtml(r.race_title)}</div>
          <div class="meta">${dateLabel(r.kaisaibi)} ${esc(r.kaisaijo)}${r.race_no}R ${esc(startTime(r.race_info))}発走 ${esc(courseLabel(r.race_info))}</div>
        </a>`).join('');
    }
    const days = [...new Set(races.map(r => r.kaisaibi))];
    for (const day of days) {
      const ofDay = races.filter(r => r.kaisaibi === day);
      for (const course of [...new Set(ofDay.map(r => r.kaisaijo))]) {
        html += `<h2>${dateLabel(day)} ${esc(course)}</h2>
          <div class="card">${raceList(ofDay.filter(r => r.kaisaijo === course))}</div>`;
      }
    }
  }
  html += '<a class="bigbtn" href="results.html">🏁 直近のレース結果を見る</a>';
  app.innerHTML = html + cta();
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

  const g = gradeOf(title);
  let html = `<div class="hero${g ? ` g${g}` : ''}">
    <p class="when">${fullDate(d)} ${esc(c)}${r}R</p>
    <h1>${titleHtml(title)}</h1>
    <p class="when">${esc([startTime(info) && `${startTime(info)}発走`, courseLabel(info)].filter(Boolean).join('　'))}</p>
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
    `select=horse_name,kaisaibi,kaisaijo,race_title,rank&horse_name=in.(${enc(inList)})&kaisaibi=lt.${before}&order=kaisaibi.desc&limit=${list.length * 4}`, HOUR)
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
  let html = `<div class="hero"><p class="when">🏁 レース結果</p><h1>${fullDate(d)} ${esc(c)}</h1>
    <p class="when">レースを押すと、着順と払い戻しが見られます</p></div>
    <p><select id="date" aria-label="開催日">${dates.map(x => `<option value="${x}" ${x === d ? 'selected' : ''}>${x.slice(0, 4)}/${dateLabel(x)}</option>`).join('')}</select></p>
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
  let html = `<div class="hero"><p class="when">競走馬</p><h1>${esc(name)}</h1>
    <p class="when">${esc(runs[0]?.age || '')}</p>${favButton(name, true)}</div>`;

  if (next.length) {
    html += '<h2>出走予定</h2>' + next.map(e => `
      <a class="card graded g${gradeOf(e.race_title)}" href="${raceUrl(e.kaisaibi, e.kaisaijo, e.race_no)}">
        <div class="name">${titleHtml(e.race_title)}</div>
        <div class="meta">${dateLabel(e.kaisaibi)} ${esc(e.kaisaijo)}${e.race_no}R ${esc(courseLabel(e.race_info))}${e.jockey ? ` / ${esc(e.jockey)}` : ''}</div>
      </a>`).join('');
  }

  if (!runs.length) {
    html += '<p class="notice">この馬の過去のレース結果はまだありません。</p>';
  } else {
    // 着順が数字のレースだけを数える（取消・除外などは外す）
    const ranked = runs.filter(x => /^\d+$/.test(x.rank || ''));
    const n = k => ranked.filter(x => Number(x.rank) === k).length;
    const [w, s, t] = [n(1), n(2), n(3)];
    const pct = v => (ranked.length ? `${Math.round((v / ranked.length) * 100)}%` : '-');
    html += `<h2>成績</h2><div class="stats">
      <div><b>${w}-${s}-${t}-${ranked.length - w - s - t}</b><span>成績（${ranked.length}戦）</span></div>
      <div><b>${pct(w)}</b><span>勝率</span></div>
      <div><b>${pct(w + s + t)}</b><span>複勝率</span></div>
    </div>
    <h2>過去のレース</h2>
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
  standard: { meta: '#2e7d32', label: 'スタンダード', bg: ['#1b5e20', '#43a047'], ink: '#ffffff', accent: '#fbc02d', card: 'rgba(255,255,255,.14)', deco: 'turf' },
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
// 着せ替えごとの背景の飾り
function drawDeco(ctx, t, S) {
  ctx.save();
  if (t.deco === 'turf') {
    // 芝刈りのしま模様
    ctx.fillStyle = 'rgba(255,255,255,.06)';
    for (let x = -S; x < S; x += 160) {
      ctx.beginPath(); ctx.moveTo(x, S); ctx.lineTo(x + 80, S); ctx.lineTo(x + 80 + S, 0); ctx.lineTo(x + S, 0); ctx.fill();
    }
  } else if (t.deco === 'stripe') {
    // 勝負服のたすき（赤白）
    // 右上の角だけを斜めに横切らせる（名前やメモにかからないように）
    ctx.translate(S * 0.8, -60);
    ctx.rotate(-Math.PI / 5);
    ctx.fillStyle = t.accent; ctx.fillRect(0, 0, 70, S * 1.6);
    ctx.fillStyle = 'rgba(255,255,255,.9)'; ctx.fillRect(70, 0, 22, S * 1.6);
  } else if (t.deco === 'petal' || t.deco === 'sparkle') {
    const mark = t.deco === 'petal' ? '🌸' : '✦';
    ctx.fillStyle = t.deco === 'petal' ? '#ffffff' : t.accent;
    ctx.textBaseline = 'middle';
    const spots = [[70, 150, 70], [960, 120, 90], [1010, 520, 54], [40, 600, 46], [930, 1010, 80], [110, 1010, 60], [540, 60, 40], [680, 1050, 44]];
    for (const [x, y, size] of spots) {
      ctx.globalAlpha = t.deco === 'petal' ? 0.55 : 0.7;
      ctx.font = `${size}px ${FONT}`;
      ctx.fillText(mark, x - size / 2, y);
    }
  } else if (t.deco === 'frame') {
    // 金の二重の額縁
    ctx.strokeStyle = t.accent;
    ctx.lineWidth = 6; ctx.strokeRect(24, 24, S - 48, S - 48);
    ctx.lineWidth = 2; ctx.strokeRect(40, 40, S - 80, S - 80);
  }
  ctx.restore();
  // うすく推しマーク
  ctx.save();
  ctx.globalAlpha = 0.1;
  ctx.fillStyle = t.accent;
  ctx.font = `220px ${FONT}`;
  ctx.textBaseline = 'alphabetic';
  ctx.fillText(currentMark(), S - 300, S - 150);
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
  const blob = await new Promise(res => drawCard(items).toBlob(res, 'image/png'));
  if (!blob) return;
  const file = new File([blob], 'oshiuma.png', { type: 'image/png' });
  if (navigator.canShare?.({ files: [file] })) {
    try { await navigator.share({ files: [file], text }); } catch (e) { /* 閉じただけ */ }
    return;
  }
  const a = document.createElement('a');
  a.href = URL.createObjectURL(blob);
  a.download = 'oshiuma.png';
  a.click();
  setTimeout(() => URL.revokeObjectURL(a.href), 10e3);
}

async function renderFav() {
  const list = favs();
  const next = await favNextRuns(list, todayJst());
  const theme = currentTheme();
  let html = `<div class="hero fav-hero"><p class="when">推し馬ページ</p>
    <h1><span id="mark">${esc(currentMark())}</span> <span id="title">${esc(favTitle())}</span></h1>
    <p class="when">${list.length}頭を応援中</p></div>`;

  html += `<h2>着せ替え</h2><div class="card dress">
    <div class="themes">${Object.entries(THEMES).map(([k, t]) => `
      <button type="button" class="swatch sw-${k}${k === theme ? ' on' : ''}" data-theme-pick="${k}" aria-pressed="${k === theme}">${esc(t.label)}</button>`).join('')}</div>
    <p class="small">推しマーク</p>
    <div class="marks">${MARKS.map(m => `<button type="button" class="mark${m === currentMark() ? ' on' : ''}" data-mark-pick="${esc(m)}">${esc(m)}</button>`).join('')}</div>
    <label class="small" for="favTitle">ページの名前（${TITLE_MAX}文字まで）</label>
    <input id="favTitle" class="text" maxlength="${TITLE_MAX}" value="${esc(load('favTitle') || '')}" placeholder="わたしの推し馬">
  </div>`;
  if (list.length) html += '<h2>シェア画像のプレビュー</h2><div class="preview" id="preview"></div>';

  if (!list.length) {
    html += '<div class="card hint">☆ まだ推し馬がいません。出馬表や馬のページで馬名の横の ☆ を押すと、ここにメモ欄とシェアボタンが出ます。</div>';
  } else {
    html += `<div class="sharebar">
      <button type="button" class="btn x" data-share-list="x">推し馬リストをXでシェア</button>
      <button type="button" class="btn" data-share-list="img">リストを画像でシェア</button></div>`;
    html += list.map(name => `
      <div class="card oshi">
        <div class="oshi-head">${horseLink(name)} ${favButton(name)}</div>
        <div class="small">${nextRunHtml(next.get(name))}</div>
        <textarea class="memo" data-memo="${esc(name)}" maxlength="${MEMO_MAX}" rows="3" placeholder="メモ（例：次は東京2400mで巻き返す！）">${esc(memoOf(name))}</textarea>
        <div class="memo-foot"><span class="small" data-count="${esc(name)}">${[...memoOf(name)].length}/${MEMO_MAX}</span>
          <button type="button" class="pill x" data-share-x="${esc(name)}">Xでシェア</button>
          <button type="button" class="pill" data-share-img="${esc(name)}">画像でシェア</button></div>
      </div>`).join('');
    html += '<p class="small">メモ・着せ替えはこの iPhone の中だけに保存されます（ほかの人には見えません。シェアしたときだけ公開されます）。</p>';
  }
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
      document.getElementById('title').textContent = favTitle();
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
      document.getElementById('mark').textContent = d.markPick;
      for (const s of app.querySelectorAll('[data-mark-pick]')) s.classList.toggle('on', s === b);
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

const pages = { home: renderHome, race: renderRace, results: renderResults, horse: renderHorse, fav: renderFav };
(pages[app.dataset.page] || renderHome)().catch(showError);
