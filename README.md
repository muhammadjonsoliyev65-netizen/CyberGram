# CyberGram

**CyberGram** — Telegram API asosida ishlaydigan **norasmiy** Android ilova. U O'zbekistonda keng tarqalgan firibgarliklardan (soxta havolalar, SMS o'g'irlovchi APK fayllar, "sovrin yutdingiz" xabarlari) himoya qilishga qaratilgan.

> ⚠️ CyberGram Telegram kompaniyasining rasmiy ilovasi emas va unga aloqador emas. Ilova [Telegram API](https://core.telegram.org/api) dan foydalanadi va [DrKLO/Telegram](https://github.com/DrKLO/Telegram) (13.2.2) ochiq kodi asosida qurilgan.

*Unofficial Telegram client based on the Telegram API and the open-source code of DrKLO/Telegram (13.2.2). Not affiliated with Telegram.*

---

## Imkoniyatlar

- 🛡 **Xabar tekshiruvi** — har bir xabarda oflayn tekshiruv belgisi (🟢 / 🟡 / 🔴).
- 🔗 **Havola qo'riqchisi** — xavfli yoki notanish saytlar avtomatik ochilmaydi, avval ogohlantiradi.
- 📦 **APK / Fayl qo'riqchisi** — APK fayllardagi xavfli ruxsatlarni (SMS o'qish, Accessibility va boshqalar), niqoblangan fayllarni (`hujjat.pdf.exe`) va soxta bank/to'lov ilovalarini aniqlaydi.
- 🤖 **AI tekshiruv** (ixtiyoriy, rozilik bilan) — faqat havolalar va fayl nomlari tahlil qilinadi.
- 🌐 O'zbek va rus tillari.

## Maxfiylik

- **Xabarlaringiz matni hech qayerga yuborilmaydi.**
- AI tekshiruvga faqat havola (so'rov parametrlarisiz) va fayl nomi yuboriladi.
- Karta raqami, telefon, email kabi ma'lumotlar telefonning o'zida yashiriladi.
- Ba'zi ogohlantirishlar Google Safe Browsing orqali beriladi: [Advisory provided by Google](https://developers.google.com/safe-browsing/v4/advisory).
- 100% himoya kafolati yo'q — shubhali havola va fayllarga doim ehtiyot bo'ling.

## O'zingiz yig'ish (build)

1. [my.telegram.org](https://my.telegram.org) dan o'z `api_id` va `api_hash` ingizni oling.
2. Loyiha ildizida `local.properties` faylini yarating va o'z kalitlaringizni yozing (qaysi nomlar o'qilishini `TMessagesProj/build.gradle` dan ko'ring).
3. [Firebase](https://console.firebase.google.com) da o'z loyihangizni yarating va `google-services.json` ni `TMessagesProj_App/` ga qo'ying.
4. Android Studio'da oching va `TMessagesProj_App` modulini yig'ing.

Kalitlar, `local.properties`, `google-services.json` va imzo kaliti (`.jks`) bu repoga kiritilmagan.

## Litsenziya

GNU GPL v2 — [LICENSE](LICENSE) ga qarang. Asl kod: © Telegram FZ-LLC va DrKLO/Telegram mualliflari.

## Aloqa

- 📢 Yangiliklar: [@CyberGramNews](https://t.me/CyberGramNews)
- 💬 Yordam: [@CYBERGRAMUZ](https://t.me/CYBERGRAMUZ)
