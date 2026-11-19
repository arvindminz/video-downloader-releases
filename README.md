# 📱 Video Downloader — Native Android Media Downloader

![Android](https://img.shields.io/badge/Platform-Android-green.svg)
![Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)
![Compose](https://img.shields.io/badge/UI-Jetpack%20Compose-blue.svg)
![yt-dlp](https://img.shields.io/badge/Engine-yt--dlp-red.svg)
![FFmpeg](https://img.shields.io/badge/Audio-FFmpeg-black.svg)
![Cloudflare](https://img.shields.io/badge/Telemetry-Cloudflare%20Workers-orange.svg)

**Video Downloader** is a high-performance native Android application engineered for high-speed multi-quality video and audio downloading, real-time playlist extraction, embedded ExoPlayer video playback, and dynamic cloud telemetry.

---

## 📥 Downloads & APK Releases

Download the latest compiled APK directly to your Android device. **The latest version is always listed at the top.**

| Version | Release Date | Highlights & Notes | Download Link |
| :--- | :--- | :--- | :--- |
| 🚀 **v1.1.0** *(Latest)* | **2026-11-19** | Added 4K playlist support, realtime streaming extraction, fast-draggable scrollbars, and in-app updates. | [📥 Download APK v1.1.0](https://github.com/arvindminz/video-downloader-releases/releases/download/v1.1.0/VideoDownloader_v1.1.0.apk) \| [GitHub Release](https://github.com/arvindminz/video-downloader-releases/releases/tag/v1.1.0) |
| 📦 **v1.0.0** | **2026-11-18** | Initial Stable Release with yt-dlp & FFmpeg stream merging. | [📥 Download APK v1.0.0](https://github.com/arvindminz/video-downloader-releases/releases/download/v1.1.0/VideoDownloader_v1.1.0.apk) |

---

## 🌟 Key Features

### 1. 🎥 Multi-Quality Video & Audio Extraction
- **Ultra-HD Video Options**: Extract DASH/HLS streams from **2160p (4K)**, **1440p (2K)**, **1080p Full HD**, **720p**, **480p**, **360p**, **240p**, down to **144p**.
- **Lossless Audio Extraction**: Download standalone **MP3** or **M4A** audio tracks.
- **FFmpeg Stream Merging**: Merges VP9/AV1 video streams with AAC (`bestaudio[ext=m4a]`) audio into playback-compatible `.mp4` containers.

### 2. ⚡ Realtime Streaming Playlist Downloader
- **Line-by-Line Item Streaming**: As YouTube playlist entries are extracted, items appear on screen live in real time without waiting for the full playlist to finish parsing.
- **Batch Downloading**: Select individual videos or tap *Select All* to enqueue full playlists into multi-threaded parallel downloads.

### 3. 🎬 Built-In ExoPlayer Video Player
- Watch videos directly inside the app with player controls:
  - Auto-play toggle & CC Subtitles badge.
  - Resolution selector (`360p` / `1080p`) & Speed multiplier (`1x`).
  - Headphones / Background audio mode (`🎧`).
  - Action buttons: **Save**, **Popup (PiP)**, **Play Audio**, **Share**, and **Download** (`📥`).
  - Suggested Related Videos feed list.

### 4. 📂 Dual Queue & Downloads Manager
- **Queue Tab**:
  - Multi-threaded **Chunked Progress Bar** rendering individual segment progress (4, 8, or 16 parallel stream chunks).
  - Empty queue state with a **"Go to downloads"** button.
- **Downloads Tab**:
  - Active music conversions (`Convert to Music`) and APK updates listed **at the top** with green Android robot logos (`🤖`).
  - Fast **Right-Edge Draggable Scrollbar** for instant scrolling through long file lists.
  - 3-Dot Action Menu: **Share**, **Hide**, **Delete**, **Convert to Music**, **Rename**, and **Other Resolutions**.
- **Horizontal Swipe Gestures**: Slide left or right to switch smoothly between Queue and Downloads.

### 5. ☁️ Web-Based In-App Update System & Telemetry
- **Cloudflare Worker Backend**: Deployed at `https://video-downloader-telemetry.arvindminz158.workers.dev`.
- **Web Admin Portal (`/admin`)**: Publish new app updates directly from any phone or PC browser.
- **Live Stats Dashboard (`/stats`)**: Tracks total unique installs, top device models, country/city geolocation, and IST timestamps.

---

## 🛠️ Tech Stack & Architecture

- **UI Framework**: 100% Jetpack Compose with Material 3 Design
- **Media Engine**: `youtubedl-android` (v0.19.0) + bundled native `FFmpeg` binaries
- **Video Playback**: AndroidX Media3 ExoPlayer (`1.3.1`)
- **Background Tasks**: WorkManager + Android Foreground Services
- **Image Loading**: Coil (`2.7.0`)
- **Backend API**: Cloudflare Workers + Cloudflare D1 Database (`telemetry_db`)

---

## 🏗️ How to Build from Source

### Prerequisites
- Android Studio 2026.1.1 or newer
- JDK 17 or higher
- Android SDK 35 (Target SDK)

### Building Steps
1. **Clone the Repository**:
   ```bash
   git clone https://github.com/arvindminz/video-downloader-releases.git
   cd video-downloader-releases
   ```
2. **Build Debug APK**:
   ```bash
   ./gradlew assembleDebug
   ```
3. **Run Unit Tests**:
   ```bash
   ./gradlew testDebugUnitTest
   ```

---

## 📜 License & Acknowledgments

- **License**: Distributed under the MIT License.
- **Engine**: Powered by [yt-dlp](https://github.com/yt-dlp/yt-dlp) and [youtubedl-android](https://github.com/deniscerri/youtubedl-android).
- **Icons**: Mary Akveo Flat Line SVG & Material Symbols.
