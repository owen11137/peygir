(function () {
  'use strict';
  var search = document.getElementById('guideSearch');
  var pages = Array.from(document.querySelectorAll('.sheet'));
  var links = Array.from(document.querySelectorAll('.guide-nav a'));
  var count = document.getElementById('searchCount');
  var empty = document.getElementById('noResults');
  var menu = document.querySelector('.guide-sidebar');
  var toggle = document.getElementById('menuToggle');
  var dialog = document.getElementById('imageZoom');
  function normalize(text) { return text.toLowerCase().replace(/ي/g, 'ی').replace(/ك/g, 'ک').replace(/[\u200c\u200f\u200e]/g, '').replace(/\s+/g, ' ').trim(); }
  var pageText = pages.map(function (p) { return normalize(p.textContent); });
  function filter() {
    var query = normalize(search.value), matches = 0;
    pages.forEach(function (p, i) {
      var show = !query || pageText[i].includes(query);
      p.hidden = !show;
      if (show) matches++;
      if (links[i]) links[i].hidden = !show;
    });
    count.textContent = query ? matches + ' صفحهٔ مرتبط' : pages.length + ' صفحهٔ راهنما';
    empty.hidden = matches !== 0;
  }
  search.addEventListener('input', filter);
  toggle.addEventListener('click', function () {
    var open = menu.classList.toggle('is-open');
    toggle.setAttribute('aria-expanded', String(open));
  });
  links.forEach(function (link) {
    link.addEventListener('click', function () {
      menu.classList.remove('is-open');
      toggle.setAttribute('aria-expanded', 'false');
    });
  });
  function progress() {
    var max = document.documentElement.scrollHeight - window.innerHeight;
    document.getElementById('readingProgress').style.width = (max > 0 ? Math.min(100, window.scrollY / max * 100) : 100) + '%';
  }
  window.addEventListener('scroll', progress, {passive:true});
  window.addEventListener('resize', progress);
  if ('IntersectionObserver' in window) {
    var observer = new IntersectionObserver(function (entries) {
      entries.forEach(function (entry) {
        if (entry.isIntersecting) links.forEach(function (link) {
          var active = link.getAttribute('href') === '#' + entry.target.id;
          link.classList.toggle('active', active);
          if (active) link.setAttribute('aria-current', 'location'); else link.removeAttribute('aria-current');
        });
      });
    }, {rootMargin:'-15% 0px -60% 0px'});
    pages.forEach(function (p) { observer.observe(p); });
  }
  document.getElementById('printGuide').addEventListener('click', function () { window.print(); });
  window.addEventListener('beforeprint', function () {
    pages.forEach(function (p) { p.hidden = false; });
    if (dialog.open) dialog.close();
  });
  window.addEventListener('afterprint', filter);
  document.querySelectorAll('.screen img').forEach(function (image) {
    image.tabIndex = 0;
    image.setAttribute('role', 'button');
    image.setAttribute('aria-label', image.alt + '؛ نمایش بزرگ‌تر');
    function open() {
      var target = dialog.querySelector('img');
      target.src = image.src;
      target.alt = image.alt;
      dialog.showModal();
    }
    image.addEventListener('click', open);
    image.addEventListener('keydown', function (event) { if (event.key === 'Enter' || event.key === ' ') { event.preventDefault(); open(); } });
  });
  document.getElementById('closeZoom').addEventListener('click', function () { dialog.close(); });
  dialog.addEventListener('click', function (event) { if (event.target === dialog) dialog.close(); });
  progress();
})();
