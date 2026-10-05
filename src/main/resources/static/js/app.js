(function () {
  var root = document.documentElement;
  var saved = null;
  try { saved = localStorage.getItem('peygir-theme'); } catch (e) {}
  if (saved) root.setAttribute('data-theme', saved);

  var tb = document.getElementById('themeBtn');
  if (tb) tb.addEventListener('click', function () {
    var next = root.getAttribute('data-theme') === 'dark' ? 'light' : 'dark';
    root.setAttribute('data-theme', next);
    try { localStorage.setItem('peygir-theme', next); } catch (e) {}
    document.dispatchEvent(new Event('peygir-theme'));
  });
  var mb = document.getElementById('menuBtn'), sb = document.getElementById('sidebar');
  if (mb && sb) mb.addEventListener('click', function () { sb.classList.toggle('open'); });

  // علامت اجباری‌شدن توضیحات برای علت‌هایی مثل «سایر»
  var reason = document.getElementById('reasonId'), label = document.getElementById('detailLabel');
  if (reason && label) {
    var sync = function () {
      var o = reason.options[reason.selectedIndex];
      label.classList.toggle('req', !!(o && o.dataset.requires === 'true'));
    };
    reason.addEventListener('change', sync); sync();
  }

  // همگام‌سازی «تیم هدف» و «شخص»: انتخاب شخص، تیمش را تنظیم می‌کند؛ تغییر تیم، شخصِ تیم دیگر را پاک می‌کند
  var tteam = document.getElementById('targetTeamId'), tuser = document.getElementById('targetUserId');
  if (tteam && tuser) {
    tuser.addEventListener('change', function () {
      var o = tuser.options[tuser.selectedIndex];
      if (o && o.dataset.team) tteam.value = o.dataset.team;
    });
    tteam.addEventListener('change', function () {
      var o = tuser.options[tuser.selectedIndex];
      if (o && o.dataset.team && o.dataset.team !== tteam.value) tuser.value = '';
    });
  }

  // صفحه‌ی ادمین: با تغییر تیک نقش‌ها، تیک دسترسی‌ها از روی اجتماع دسترسی نقش‌های انتخاب‌شده تنظیم می‌شود
  document.querySelectorAll('form.user-form').forEach(function (form) {
    form.querySelectorAll('input.role-check').forEach(function (cb) {
      cb.addEventListener('change', function () {
        var want = {};
        form.querySelectorAll('input.role-check:checked').forEach(function (c) {
          (c.dataset.perms || '').split(',').forEach(function (p) { if (p) want[p] = true; });
        });
        form.querySelectorAll('input[name=perm]').forEach(function (p) { p.checked = !!want[p.value]; });
        var d = form.querySelector('details'); if (d) d.open = true;
      });
    });
  });
})();
