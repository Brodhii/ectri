# ECCChat - Secure Messaging App

ECCChat adalah aplikasi pesan instan yang mengutamakan keamanan dan privasi menggunakan algoritma **Elliptic Curve Cryptography (ECC)** untuk enkripsi **End-to-End (E2EE)**.

## 🚀 Fitur Utama
- **True End-to-End Encryption**: Kunci privat disimpan secara lokal di perangkat pengguna. Pesan hanya bisa dibaca oleh pengirim dan penerima.
- **Independent Message Stores**: Setiap pengguna memiliki salinan basis data pesan sendiri. Menghapus riwayat di satu sisi tidak akan menghapus data di pihak lawan.
- **Real-time Communication**: Didukung oleh Firebase Realtime Database untuk pengiriman pesan instan.
- **Background Notifications**: Notifikasi tetap masuk meskipun aplikasi ditutup atau dalam keadaan mengambang (Floating).
- **User Presence**: Status Online/Offline dan "Terakhir Dilihat" (Last Seen).
- **Auto-Sync Security Key**: Pembaruan kunci keamanan otomatis saat pengguna melakukan instal ulang.

## 📥 Download APK
Anda dapat menemukan file APK hasil build di folder berikut:
`app/build/outputs/apk/debug/app-debug.apk`

> **Note**: Jika Anda menggunakan rilis resmi, silakan cek bagian [Releases](../../releases).

## 📱 Persyaratan Sistem
Aplikasi ini dapat diinstal pada perangkat dengan spesifikasi berikut:

| Komponen | Persyaratan Minimum |
|---|---|
| **Versi Android** | Android 8.0 (Oreo) atau lebih tinggi |
| **API Level** | API 26 ke atas |
| **Koneksi** | Internet diperlukan untuk Firebase & Notifikasi |

## 🛠️ Teknologi yang Digunakan
- **Bahasa**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Backend**: Firebase (Auth, Realtime Database, Cloud Messaging)
- **Kriptografi**: Elliptic Curve Cryptography (ECC)
- **Architecture**: Repository Pattern

## 🔧 Cara Install
1. Download file `app-debug.apk`.
2. Izinkan instalasi dari "Sumber Tidak Dikenal" di pengaturan HP Anda.
3. Buka file APK dan pilih **Install**.
4. Selesai! Anda bisa langsung mendaftar dan mulai berkirim pesan secara aman.

---
*Dikembangkan untuk tugas sistem keamanan (SISKEM).*
