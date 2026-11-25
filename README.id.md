<div align="center">

  <img src="app/src/main/res/drawable-nodpi/ic_launcher_logo.png" alt="Logo Antigravity" width="88" height="88" style="border-radius: 18px;" />

  # Antigravity for Android

  <p align="center">
    <a href="README.md"><img src="https://img.shields.io/badge/English-007ACC?style=for-the-badge" alt="English" /></a>
    &nbsp;
    <a href="README.id.md"><img src="https://img.shields.io/badge/Bahasa%20Indonesia-DC3545?style=for-the-badge" alt="Bahasa Indonesia" /></a>
  </p>

  [![Android](https://img.shields.io/badge/Android-11.0%2B-3DDC84?style=flat-square&logo=android&logoColor=white)](https://android.com)
  [![Arsitektur](https://img.shields.io/badge/Arsitektur-ARM64-blue?style=flat-square)](https://arm.com)
  [![UI](https://img.shields.io/badge/UI-Jetpack%20Compose-4285F4?style=flat-square&logo=jetpackcompose&logoColor=white)](https://developer.android.com/jetpack/compose)
  [![Bahasa](https://img.shields.io/badge/Kotlin-2.0.21-7F52FF?style=flat-square&logo=kotlin&logoColor=white)](https://kotlinlang.org)
  [![Lisensi](https://img.shields.io/badge/Lisensi-AGPL_3.0-blue?style=flat-square)](LICENSE)

  <br /><br />

  <img src="assets/banner.png" alt="Antigravity for Android Showcase" width="100%" />

</div>

Antigravity for Android adalah aplikasi untuk menjalankan Google Antigravity AI coding agent langsung di HP Android. Aplikasi ini menyediakan userspace Linux ARM64 lokal dengan UI Jetpack Compose, sehingga agent bisa langsung akses workspace project, edit code, dan eksekusi command terminal (`git`, `bash`, `python`, `pkg`) langsung dari HP tanpa butuh PC.

## Fitur

- **UI Jetpack Compose**: Tampilan chat dan code editor ringan untuk layar HP.
- **Userspace Linux Lokal**: Environment ARM64 dengan `bash`, `python3`, dan CLI tools.
- **Engine Resmi Antigravity**: Menjalankan binary `language_server` resmi via login Google OAuth 2.0 PKCE.
- **Package Manager `pkg`**: Package manager bawaan (Bash & AWK) untuk install tools langsung di terminal.
- **Background Daemon (`agy-daemon`)**: Service background untuk cron task, file watcher, dan notifikasi status bar.
- **Skills & Plugins**: Mengambil skills dan plugins komunitas langsung dari katalog.

## Repo Terkait

- [KuatoDev/antigravity-bootstrap](https://github.com/KuatoDev/antigravity-bootstrap) — Rootfs Linux ARM64 yang diekstrak saat pertama kali aplikasi dibuka.
- [KuatoDev/antigravity-catalogue](https://github.com/KuatoDev/antigravity-catalogue) — Katalog skills dan plugins. Submit pull request ke sini jika ingin menambahkan skill buatan sendiri :)

## Persyaratan

- Android 11 ke atas (`arm64-v8a`)
- Izin akses storage & notifikasi

## Komunitas

- **Channel Telegram**: [t.me/vernkuato](https://t.me/vernkuato)
- **Grup Telegram**: [t.me/kuatodevprojects](https://t.me/kuatodevprojects)

## Donasi

Jika Antigravity for Android bermanfaat untuk Anda dan ingin mendukung pengembangannya, Anda dapat berdonasi melalui:

<div align="center">

  <a href="https://saweria.co/kuatodev">
    <img src="assets/saweria-qr.png" alt="QR Code Saweria" width="180" height="180" style="border-radius: 12px; background: white; padding: 8px;" />
  </a>

  <br />

  [![Saweria](https://img.shields.io/badge/Saweria-kuatodev-FAAE2B?style=for-the-badge&logo=coffee&logoColor=black)](https://saweria.co/kuatodev)
  &nbsp;
  [![PayPal](https://img.shields.io/badge/PayPal-vernkuato-00457C?style=for-the-badge&logo=paypal&logoColor=white)](http://paypal.me/vernkuato)

  <p><sub>Scan kode QR di atas atau kunjungi <strong><a href="https://saweria.co/kuatodev">Saweria</a></strong> (QRIS, GoPay, OVO, Dana, LinkAja) atau <strong><a href="http://paypal.me/vernkuato">PayPal</a></strong>.</sub></p>

</div>

## Credits

- **[Werdik (werdio325-png)](https://github.com/werdio325-png)** — `pkg`, `agy-daemon`, dan script userspace.
- **[Termux](https://github.com/termux)** — Base rootfs ARM64 dan `libtermux-exec`.
- **[Google DeepMind](https://deepmind.google)** — Binary inti `language_server` Antigravity.

---

<div align="center">
  <sub>Dirilis di bawah lisensi GNU Affero General Public License v3.0 (AGPL-3.0).</sub>
</div>
