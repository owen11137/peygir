# ساخت راهنمای کاربری

خروجی‌های قابل دریافت در `deliverables` هستند. این پوشه متن، سبک و تصاویر منبع راهنما را نگه می‌دارد.

- `build_manual.py`: متن فارسی و ساخت HTML مستقل و PDF.
- `manual.css`: صفحه‌آرایی A4، نسخه مرورگر و نمایش موبایل.
- `manual.js`: فهرست، جستجو، چاپ و بزرگ‌نمایی تصویر در نسخه HTML.
- `assets/screens`: تصاویر واقعی برنامه با داده‌های نمونه؛ بدون استفاده از دیتابیس عملیاتی یا فایل کاربران واقعی.

فونت راهنما از Vazirmatn موجود در منابع برنامه استفاده می‌کند؛ مجوز OFL آن در `src/main/resources/static/fonts/OFL-Vazirmatn.txt` و در پوشه assets این راهنما قرار دارد.

از ریشه پروژه:

```bash
python docs/user-manual/build_manual.py
```

برای ساخت PDF نیز، Python Playwright و مرورگر Chromium لازم است:

```bash
python docs/user-manual/build_manual.py --pdf --chromium /usr/bin/chromium
```

در محیط دیگر، مسیر اجرایی Chromium را با `--chromium` مشخص کنید. ابزار ساخت، برنامه را اجرا نمی‌کند و دیتابیس یا فایل کاربران را نمی‌خواند.

پس از تغییر محتوا، PDF را دوباره بسازید؛ تعداد صفحات، عدم برخورد متن با پاورقی، خوانایی فارسی، تصاویر و عملکرد نسخه HTML را بررسی کنید.
