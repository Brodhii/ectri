# ECCChat - Secure Messaging App

ECCChat adalah aplikasi pesan instan yang mengutamakan keamanan dan privasi menggunakan algoritma **Elliptic Curve Cryptography (ECC)** untuk enkripsi **End-to-End (E2EE)**.

## 🚀 Fitur Utama
- **True End-to-End Encryption**: Kunci privat disimpan secara lokal di perangkat pengguna.
- **Independent Message Stores**: Setiap pengguna memiliki salinan basis data pesan sendiri.
- **Real-time Communication**: Pengiriman pesan instan didukung oleh Firebase.
- **Background Notifications**: Notifikasi tetap masuk meskipun aplikasi ditutup.
- **Auto-Sync Security Key**: Pembaruan kunci otomatis saat instal ulang.

## 📦 Latest Release

### [v1.0.0 - Initial Secure Release](https://github.com/YOUR_USERNAME/YOUR_REPO/releases/latest) `Latest`

*   **ECC Encryption**: Implementasi penuh Elliptic Curve Cryptography.
*   **Performance Fix**: Optimasi sinkronisasi pesan agar aplikasi lebih ringan.
*   **Background Resilience**: Layanan latar belakang yang lebih stabil untuk notifikasi.
*   **Independent Deletion**: Menghapus chat di satu sisi tidak menghapus di sisi lawan.

#### 📂 Assets
| Name | Size | Download |
| :--- | :--- | :--- |
| 📱 **ECCChat_v1.0.0_debug.apk** | 5.2 MB | [**Download APK**](app/build/outputs/apk/debug/app-debug.apk) |
| 📄 Source code (zip) | - | [Download ZIP](../../archive/main.zip) |
| 📄 Source code (tar.gz) | - | [Download TAR](../../archive/main.tar.gz) |

> **Note**: Untuk mendownload langsung dari folder lokal proyek ini, klik link [**Download APK**](app/build/outputs/apk/debug/app-debug.apk) di atas.

## 📱 Persyaratan Sistem
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

## 🔧 Cara Install
1. Download file `app-debug.apk` melalui link di atas.
2. Izinkan instalasi dari "Sumber Tidak Dikenal" di pengaturan HP Anda.
3. Buka file APK dan pilih **Install**.
4. Selesai! Daftar akun baru dan mulai berkirim pesan.

---
*Dikembangkan untuk tugas sistem keamanan (SISKEM).*
