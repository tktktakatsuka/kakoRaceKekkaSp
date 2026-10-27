// 競馬 過去レース結果 Web版。Supabase の公開キーで、アプリと同じテーブルを読むだけのページ。
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

// 指定日が今日より前なら、その日の結果はもう変わらない
const settled = d => d < todayJst();
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
// 人気の印（1〜3番人気は色つきの丸）
const popBadge = pop => (pop ? `<span class="pop${Number(pop) <= 3 ? ` p${Number(pop)}` : ''}">${esc(pop)}</span>` : '');
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

function raceList(races, extra) {
  return `<ul class="races">${races.map(r => `
    <li><a href="${raceUrl(r.kaisaibi, r.kaisaijo, r.race_no)}">
      <span class="no">${r.race_no}R</span>
      <span class="title">${titleHtml(r.race_title)}${extra ? extra(r) : ''}</span>
      <span class="info">${esc([startTime(r.race_info), courseLabel(r.race_info)].filter(Boolean).join(' '))}</span>
    </a></li>`).join('')}</ul>`;
}

// ---------- トップ：今週の出馬表 ----------

async function renderHome() {
  const today = todayJst();
  const entries = await queryAll('race_entries',
    `select=kaisaibi,kaisaijo,race_no,race_title,race_info&kaisaibi=gte.${today}&order=kaisaibi.asc,kaisaijo.asc,race_no.asc,id.asc`);
  const races = groupRaces(entries);
  let html = '<h1>今週のレース</h1>';

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
  html += '<h2>レース結果</h2><div class="card"><a href="results.html">直近の開催の結果を見る →</a></div>';
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
  document.title = `${title} ${results.length ? '結果' : '出馬表'} | 競馬 過去レース結果`;

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
    return `<tr class="${e.scratched ? 'scratched' : ''}">
      <td class="c">${waku(e.waku)}<br>${esc(e.horse_number)}</td>
      <td>${horseLink(e.horse_name)} <span class="small">${esc(e.sex_age)}</span><br>
        <span class="small">${esc(e.card_jockey || e.jockey)} ${esc(e.weight)} / ${esc(e.trainer)}</span>
        ${prevText ? `<br><span class="small">${esc(prevText)}</span>` : ''}</td>
      ${hasOdds ? `<td class="num">${popBadge(e.popular)} ${esc(e.win_odds)}${oddsBar(e.win_odds)}</td>` : ''}
    </tr>`;
  }).join('');
  const updated = entries.find(e => e.odds_updated_at)?.odds_updated_at;
  const note = hasOdds && updated
    ? `<p class="small">オッズは ${new Date(updated).toLocaleString('ja-JP', { timeZone: 'Asia/Tokyo', month: 'numeric', day: 'numeric', hour: '2-digit', minute: '2-digit' })} 時点</p>` : '';
  return `<div class="card"><table>
    <tr><th class="c">馬番</th><th>馬名・騎手</th>${hasOdds ? '<th class="num">単勝</th>' : ''}</tr>${rows}</table>${note}</div>`;
}

function resultTable(results) {
  const rows = results.map(x => `<tr class="${['1', '2', '3'].includes(x.rank) ? 'top3' : ''}">
    <td class="c">${rankBadge(x.rank)}</td>
    <td class="c">${waku(x.waku)}<br>${esc(x.horse_number)}</td>
    <td>${horseLink(x.horse_name)} <span class="small">${esc(x.age)}</span><br>
      <span class="small">${esc(x.jockey)} ${esc(x.weight)}　${esc(x.time)} ${esc(x.tyakusa)}　上がり${esc(x.nobori)}</span></td>
    <td class="num">${popBadge(x.popular)} ${esc(x.win_odds)}${oddsBar(x.win_odds)}</td>
  </tr>`).join('');
  return `<div class="card"><table>
    <tr><th class="c">着</th><th class="c">馬番</th><th>馬名・騎手</th><th class="num">単勝</th></tr>${rows}</table></div>`;
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
  let html = `<h1>レース結果</h1>
    <p><select id="date">${dates.map(x => `<option value="${x}" ${x === d ? 'selected' : ''}>${x.slice(0, 4)}/${dateLabel(x)}</option>`).join('')}</select></p>
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
  if (!name || name.length > 30 || /[(),"\\]/.test(name)) { location.replace('./'); return; }
  document.title = `${name}の過去レース結果 | 競馬 過去レース結果`;

  const [runs, next] = await Promise.all([
    query('race_results', `select=kaisaibi,kaisaijo,race_no,race_title,hassou_time,rank,popular,win_odds,jockey,time,nobori,age,weight&horse_name=eq.${enc(name)}&order=kaisaibi.desc&limit=60`),
    query('race_entries', `select=kaisaibi,kaisaijo,race_no,race_title,race_info,horse_number,jockey&horse_name=eq.${enc(name)}&kaisaibi=gte.${todayJst()}&order=kaisaibi.asc&limit=5`).catch(() => []),
  ]);
  let html = `<div class="hero"><p class="when">競走馬</p><h1>${esc(name)}</h1>
    <p class="when">${esc(runs[0]?.age || '')}</p></div>`;

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
    <div class="card"><ul class="runs">${runs.map(x => `
      <li><a href="${raceUrl(x.kaisaibi, x.kaisaijo, x.race_no)}">
        ${rankBadge(x.rank)}
        <span class="title">${titleHtml(x.race_title)}<br>
          <span class="small">${fullDate(x.kaisaibi)} ${esc(x.kaisaijo)} ${esc(courseLabel(x.hassou_time))}　${esc(x.jockey)}　${esc(x.time)}</span></span>
        <span class="info">${x.popular ? `${esc(x.popular)}人気` : ''}</span>
      </a></li>`).join('')}</ul></div>`;
  }
  app.innerHTML = html + cta(`${name}の過去レース結果`);
}

const pages = { home: renderHome, race: renderRace, results: renderResults, horse: renderHorse };
(pages[app.dataset.page] || renderHome)().catch(showError);
