(function () {
  'use strict';
  var base = window.PEYGIR_BASE || '/';
  var systemSeconds = window.PEYGIR_REFRESH || 60;
  var charts = {}, firstDraw = true, timer = null, last = null;

  var PRESETS = [['today', 'امروز'], ['7', '۷ روز اخیر'], ['30', '۳۰ روز اخیر'], ['month', 'این ماه'], ['90', '۳ ماه اخیر'],
                 ['year', 'امسال'], ['3y', '۳ سال اخیر'], ['all', 'همه‌ی زمان‌ها']];
  var BUCKETS = { hour: 'ساعتی', day: 'روزانه', week: 'هفتگی', month: 'ماهانه', year: 'سالانه' };
  var PALETTE = ['#0f766e', '#2457c5', '#c2570c', '#7c3aed', '#be185d'];

  function $(id) { return document.getElementById(id); }
  function css(v) { return getComputedStyle(document.documentElement).getPropertyValue(v).trim(); }
  function esc(s) { var d = document.createElement('div'); d.textContent = s == null ? '' : s; return d.innerHTML; }

  // ---------------------------------------------------------------- بازه
  var qp = new URLSearchParams(location.search);
  var state = { preset: qp.get('preset') || (qp.get('from') ? 'custom' : 'today'), from: qp.get('from') || '', to: qp.get('to') || '' };

  function query() {
    var p = new URLSearchParams();
    p.set('preset', state.preset);
    if (state.preset === 'custom') { p.set('from', state.from); if (state.to) p.set('to', state.to); }
    return p.toString();
  }

  function renderChips() {
    $('chips').innerHTML = PRESETS.map(function (p) {
      return '<button type="button" class="chip' + (state.preset === p[0] ? ' on' : '') + '" data-p="' + p[0] + '">' + p[1] + '</button>';
    }).join('');
    $('fromIn').value = state.preset === 'custom' ? state.from : $('fromIn').value;
    $('toIn').value = state.preset === 'custom' ? state.to : $('toIn').value;
  }

  $('chips').addEventListener('click', function (e) {
    var b = e.target.closest('button[data-p]'); if (!b) return;
    state.preset = b.getAttribute('data-p'); state.from = ''; state.to = '';
    $('fromIn').value = ''; $('toIn').value = '';
    renderChips(); load();
  });
  $('applyBtn').addEventListener('click', function () {
    state.preset = 'custom'; state.from = $('fromIn').value.trim(); state.to = $('toIn').value.trim();
    renderChips(); load();
  });
  $('btnReload').addEventListener('click', load);

  // ---------------------------------------------------------------- به‌روزرسانی خودکار
  var sel = $('refreshSel');
  var OPTS = [['default', 'خودکار: پیش‌فرض سیستم (' + fmtSec(systemSeconds) + ')'], ['30', 'هر ۳۰ ثانیه'], ['60', 'هر ۱ دقیقه'], ['300', 'هر ۵ دقیقه'], ['0', 'خاموش']];
  function fmtSec(s) { return s % 60 === 0 ? (s / 60) + ' دقیقه' : s + ' ثانیه'; }
  sel.innerHTML = OPTS.map(function (o) { return '<option value="' + o[0] + '">' + o[1] + '</option>'; }).join('');
  var saved = null; try { saved = localStorage.getItem('peygir-refresh'); } catch (e) {}
  sel.value = saved && OPTS.some(function (o) { return o[0] === saved; }) ? saved : 'default';
  sel.addEventListener('change', function () {
    try { localStorage.setItem('peygir-refresh', sel.value); } catch (e) {}
    schedule();
  });
  function seconds() { return sel.value === 'default' ? systemSeconds : parseInt(sel.value, 10); }
  function schedule() {
    clearTimeout(timer);
    var s = seconds();
    if (s > 0) timer = setTimeout(function () { if (!document.hidden) load(); schedule(); }, s * 1000);
  }

  // ---------------------------------------------------------------- نمودارها
  function axes(horizontal, reverseX) {
    var tx = css('--mu'), grid = css('--bd'), f = { family: 'Vazirmatn FD' };
    return {
      x: { ticks: { color: tx, font: f, precision: 0 }, grid: { color: grid }, reverse: !!reverseX },
      y: { ticks: { color: tx, font: f, precision: 0 }, grid: { color: grid }, position: 'right', beginAtZero: true }
    };
  }
  function draw(id, type, data, opts) {
    if (charts[id]) charts[id].destroy();
    var f = { family: 'Vazirmatn FD' };
    opts.responsive = true; opts.maintainAspectRatio = false;
    opts.animation = firstDraw ? { duration: 500 } : false;
    opts.plugins = opts.plugins || {};
    opts.plugins.tooltip = { rtl: true, titleFont: f, bodyFont: f };
    charts[id] = new Chart($(id), { type: type, data: data, options: opts });
  }

  function deltaBadge(delta, pct, hasPrev, prev) {
    if (!hasPrev) return '';
    if (delta > 0) return '<span class="st st-danger">▲ ' + delta + (pct != null ? ' (' + pct + '٪)' : (prev === 0 ? ' · جدید' : '')) + '</span>';
    if (delta < 0) return '<span class="st st-success">▼ ' + Math.abs(delta) + (pct != null ? ' (' + Math.abs(pct) + '٪)' : '') + '</span>';
    return '<span class="st st-secondary">بدون تغییر</span>';
  }

  function render(d) {
    var r = d.range, k = d.kpis;
    $('updatedAt').textContent = d.updatedAt;
    $('rangeErr').hidden = true;
    $('rangeText').textContent = 'بازه‌ی نمایش: ' + r.fromJ + (r.fromJ === r.toJ ? '' : ' تا ' + r.toJ) + ' (' + r.days + ' روز)' +
      (r.hasPrev ? ' · مقایسه با بازه‌ی قبل: ' + r.prevFromJ + (r.prevFromJ === r.prevToJ ? '' : ' تا ' + r.prevToJ) : '');
    $('bucketName').textContent = '(' + (BUCKETS[d.trend.bucket] || '') + ')';

    $('kTotal').textContent = k.total;
    $('kDelta').innerHTML = deltaBadge(k.total - k.prevTotal, k.deltaPercent, r.hasPrev, k.prevTotal);
    $('kPerDay').textContent = k.perDay;
    $('kAwaiting').textContent = k.awaiting;
    $('kClosed').textContent = k.closed; $('kClosedP').textContent = k.total ? '(' + k.closedPercent + '٪)' : '';
    $('kTop').textContent = k.topTeam; $('kTopCount').textContent = k.topCount ? '(' + k.topCount + ' مورد)' : '';

    // برداشت‌های سریع برای تصمیم‌گیری
    var ins = [];
    if (d.byTeam.length && d.byTeam[0].count > 0) {
      var w = d.byTeam[0];
      ins.push('<div class="insight bad"><i class="bi bi-exclamation-triangle"></i> بیشترین عدم پاسخ در این بازه: <strong>' + esc(w.name) + '</strong> با ' + w.count + ' مورد (' + w.percent + '٪ از کل)' +
        (r.hasPrev && w.deltaPercent != null ? ' — ' + (w.delta > 0 ? 'بدتر' : w.delta < 0 ? 'بهتر' : 'بدون تغییر') + ' نسبت به بازه‌ی قبل' : '') + '</div>');
    }
    if (r.hasPrev) {
      var worse = d.byTeam.filter(function (t) { return t.delta > 0; }).sort(function (a, b) { return b.delta - a.delta; })[0];
      var better = d.byTeam.filter(function (t) { return t.delta < 0; }).sort(function (a, b) { return a.delta - b.delta; })[0];
      if (worse) ins.push('<div class="insight bad"><i class="bi bi-graph-up-arrow"></i> بیشترین بدترشدن: <strong>' + esc(worse.name) + '</strong> (' + worse.prev + ' ← ' + worse.count + ')</div>');
      if (better) ins.push('<div class="insight good"><i class="bi bi-graph-down-arrow"></i> بیشترین بهبود: <strong>' + esc(better.name) + '</strong> (' + better.prev + ' ← ' + better.count + ')</div>');
    }
    if (!k.total) ins.push('<div class="insight"><i class="bi bi-info-circle"></i> در این بازه گزارش تأییدشده‌ای نیست. بازه را بزرگ‌تر کنید (مثلاً «۳۰ روز اخیر» یا «امسال»).</div>');
    $('insights').innerHTML = ins.join('');

    // مقایسه‌ی تیم‌ها
    var tl = d.byTeam.slice(0, 12);
    var ds = [{ label: 'این بازه', data: tl.map(function (t) { return t.count; }), backgroundColor: css('--ac'), borderRadius: 6 }];
    if (r.hasPrev) ds.push({ label: 'بازه‌ی قبل', data: tl.map(function (t) { return t.prev; }), backgroundColor: css('--bd'), borderRadius: 6 });
    var ax = axes(true, true);
    draw('chCompare', 'bar', { labels: tl.map(function (t) { return t.name; }), datasets: ds },
      { indexAxis: 'y', scales: ax, plugins: { legend: { display: r.hasPrev, rtl: true, labels: { color: css('--mu'), font: { family: 'Vazirmatn FD' } } } } });

    // روند
    var tr = d.trend, lines = tr.series.map(function (s, i) {
      return { label: s.name, data: s.data, borderColor: PALETTE[i % PALETTE.length], backgroundColor: PALETTE[i % PALETTE.length], tension: .3, pointRadius: 3, borderWidth: 2 };
    });
    lines.push({ label: 'جمع', data: tr.total, borderColor: css('--mu'), backgroundColor: css('--mu'), borderDash: [6, 4], tension: .3, pointRadius: 2, borderWidth: 2 });
    draw('chTrend', 'line', { labels: tr.labels, datasets: lines },
      { scales: axes(false, true), plugins: { legend: { display: true, rtl: true, labels: { color: css('--mu'), font: { family: 'Vazirmatn FD' } } } } });

    // علت‌ها
    var rs = d.byReason.slice(0, 10);
    draw('chReasons', 'bar', { labels: rs.map(function (x) { return x.name; }), datasets: [{ data: rs.map(function (x) { return x.count; }), backgroundColor: css('--warning'), borderRadius: 6 }] },
      { indexAxis: 'y', scales: axes(true, true), plugins: { legend: { display: false } } });

    // جدول‌ها
    $('tPersons').innerHTML = d.byPerson.length ? d.byPerson.map(function (p, i) {
      return '<tr><td>' + (i + 1) + '</td><td><strong>' + esc(p.name) + '</strong></td><td>' + esc(p.team) + '</td><td>' + p.count + '</td></tr>';
    }).join('') : '<tr><td colspan="4" class="empty">داده‌ای نیست</td></tr>';

    $('tTeams').innerHTML = d.byTeam.length ? d.byTeam.map(function (t, i) {
      return '<tr><td>' + (i + 1) + '</td><td><strong>' + esc(t.name) + '</strong></td><td>' + t.count + '</td><td>' + t.percent + '٪</td><td>' +
        (r.hasPrev ? t.prev : '—') + '</td><td>' + deltaBadge(t.delta, t.deltaPercent, r.hasPrev, t.prev) + '</td><td>' + (t.count ? t.avgAttempts : '—') + '</td><td>' + t.open + '</td></tr>';
    }).join('') : '<tr><td colspan="8" class="empty">در این بازه داده‌ای نیست</td></tr>';

    var m = d.matrix, html;
    if (m.teams.length) {
      var max = 1; m.rows.forEach(function (row) { row.cells.forEach(function (c) { if (c > max) max = c; }); });
      html = '<thead><tr><th>تماس‌گیرنده ↓ / هدف ←</th>' + m.teams.map(function (t) { return '<th>' + esc(t) + '</th>'; }).join('') + '<th>جمع</th></tr></thead><tbody>' +
        m.rows.map(function (row) {
          return '<tr><td>' + esc(row.team) + '</td>' + row.cells.map(function (c) {
            var a = c ? Math.round((0.12 + 0.55 * c / max) * 100) : 0;
            return '<td style="' + (c ? 'background:color-mix(in srgb,' + css('--ac') + ' ' + a + '%,transparent);font-weight:600' : 'color:' + css('--mu')) + '">' + (c || '·') + '</td>';
          }).join('') + '<td><strong>' + row.total + '</strong></td></tr>';
        }).join('') + '</tbody>';
    } else html = '<tbody><tr><td class="empty">در این بازه داده‌ای نیست</td></tr></tbody>';
    $('tMatrix').innerHTML = html;

    $('tLatest').innerHTML = d.latest.length ? d.latest.map(function (x) {
      return '<tr><td><a href="' + base + 'reports/' + x.id + '">' + esc(x.trackingNo) + '</a></td><td>' + esc(x.callerTeam) + '</td><td><strong>' + esc(x.targetTeam) + '</strong></td><td>' +
        esc(x.targetPerson || '—') + '</td><td>' + esc(x.reason) + '</td><td class="text-nowrap">' + esc(x.date) + '</td><td><span class="st st-' + esc(x.statusClass) + '">' + esc(x.status) + '</span></td></tr>';
    }).join('') : '<tr><td colspan="7" class="empty">گزارشی در این بازه نیست</td></tr>';

    firstDraw = false;
  }

  // ---------------------------------------------------------------- بارگذاری
  function syncLinks(q) {
    var ex = $('btnExport'); if (ex) ex.href = base + 'dashboard/export?' + q;
    var all = $('btnAll');
    if (all) {
      var p = new URLSearchParams();
      if (last) { p.set('from', last.range.fromJ); p.set('to', last.range.toJ); }
      all.href = base + 'reports/all?' + p.toString();
    }
    var url = location.pathname + (state.preset === 'today' ? '' : '?' + q);
    history.replaceState(null, '', url);
  }

  function load() {
    var q = query();
    fetch(base + 'dashboard/data?' + q, { headers: { 'Accept': 'application/json' }, credentials: 'same-origin' })
      .then(function (r) { return r.json().then(function (j) { return { ok: r.ok, body: j }; }); })
      .then(function (res) {
        if (!res.ok) { var e = $('rangeErr'); e.textContent = res.body.error || 'خطا در دریافت اطلاعات'; e.hidden = false; return; }
        last = res.body; render(last); syncLinks(q);
      })
      .catch(function () { /* در دور بعدی دوباره تلاش می‌شود */ });
  }

  renderChips();
  load();
  schedule();
  document.addEventListener('peygir-theme', function () { if (last) { firstDraw = false; render(last); } });
})();
