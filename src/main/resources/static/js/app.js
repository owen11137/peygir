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

  // فهرست اشخاص فقط اعضای تیم انتخاب‌شده را نشان می‌دهد.
  var tteam = document.getElementById('targetTeamId'), tuser = document.getElementById('targetUserId');
  if (tteam && tuser) {
    var userOptions = Array.from(tuser.querySelectorAll('option[data-team]'));
    var placeholder = tuser.querySelector('option[value=""]').cloneNode(true);
    var syncUsers = function () {
      var selectedUser = tuser.value;
      var teamUsers = userOptions.filter(function (o) { return o.dataset.team === tteam.value; });
      var prompt = placeholder.cloneNode(true);
      prompt.textContent = !tteam.value ? 'ابتدا تیم را انتخاب کنید…'
        : teamUsers.length ? 'انتخاب کنید…' : 'عضوی برای این تیم وجود ندارد';
      tuser.replaceChildren(prompt);
      teamUsers.forEach(function (o) { tuser.appendChild(o.cloneNode(true)); });
      tuser.value = teamUsers.some(function (o) { return o.value === selectedUser; }) ? selectedUser : '';
      tuser.disabled = !tteam.value || teamUsers.length === 0;
    };
    tteam.addEventListener('change', syncUsers);
    syncUsers();
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
