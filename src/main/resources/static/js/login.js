(function () {
  var password = document.getElementById('password');
  var toggle = document.getElementById('passwordToggle');
  if (!password || !toggle) return;
  toggle.addEventListener('click', function () {
    var show = password.type === 'password';
    password.type = show ? 'text' : 'password';
    toggle.setAttribute('aria-pressed', String(show));
    toggle.setAttribute('aria-label', show ? 'پنهان کردن رمز عبور' : 'نمایش رمز عبور');
    toggle.querySelector('i').className = show ? 'bi bi-eye-slash' : 'bi bi-eye';
  });
})();
