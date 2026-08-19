# 🎬 ClipMint — AI YouTube Shorts Maker

> Turn long-form YouTube videos into ready-to-share vertical Shorts from your Android phone.

ClipMint is a mobile-first YouTube Shorts creation project built around an Android app and a lightweight server-side video-processing backend. Paste a YouTube URL, choose how many Shorts you want, select a duration, analyze the video, and render individual 9:16 MP4 clips.

## ✨ Current features

- Android app with a Shorts-focused Jetpack Compose interface
- YouTube URL analysis
- 3 or 4 distinct clip ranges
- 15-second or 30-second clips
- Server-side extraction with yt-dlp
- FFmpeg rendering to vertical 9:16 MP4
- Android Downloads/ClipMint output
- Docker-ready backend
- GitHub Actions debug APK build
- Backend request validation and bounded rendering concurrency
- Streaming MP4 responses instead of loading the complete rendered video into Node memory

## 🏗️ Architecture

```text
Android APK
   │ HTTPS
   ▼
ClipMint Backend
   ├── /health
   ├── /api/analyze
   └── /api/render
        │
        ├── yt-dlp
        └── FFmpeg
```

The Android app handles the UI and downloads. The backend performs the CPU-heavy extraction and rendering.

## 🚀 Run locally

### Backend

Requirements: Node.js 22, Docker or local FFmpeg + yt-dlp.

```bash
docker build -t clipmint-backend ./backend
docker run --rm -p 10000:10000 clipmint-backend
```

Health check:

```text
http://localhost:10000/health
```

### Android

The app reads `BACKEND_BASE_URL` from `app/build.gradle.kts`. Set it to your HTTPS backend URL before building a release APK.

## 📱 Build the APK

The `clipmint-free-apk` branch contains a GitHub Actions workflow. It now performs a backend JavaScript syntax check before the Android build.

Open GitHub → Actions → Build APK → run the workflow. The debug APK is uploaded as the `shorts-maker-debug-apk` artifact.

## 🔧 Backend improvements

The renderer has been hardened for free/small hosting environments:

- Rejects malformed JSON and oversized analyze requests.
- Accepts only supported YouTube URL forms.
- Rejects invalid/NaN/negative render parameters.
- Enforces a maximum 30-second render.
- Limits concurrent renders to avoid exhausting a small server.
- Uses a lower download resolution before producing the 1080×1920 output, reducing bandwidth and temporary disk pressure.
- Streams the finished MP4 to the client instead of reading the whole file into RAM.
- Cleans temporary render directories after successful or failed requests.
- Returns useful HTTP error codes for busy/invalid requests.

Set `MAX_RENDER_CONCURRENCY` if your server has more CPU/RAM. For a free instance, the default of 1 is intentional.

## ⚠️ Important limitations

Clip selection is currently **range-based, not true AI semantic selection**. The backend does not yet analyze transcripts to identify the strongest hooks. The current scores and hook labels are metadata generated from the selected ranges, not measured viral performance.

The next meaningful upgrades are:

1. Transcript extraction and speech-to-text.
2. Semantic hook detection and moment ranking.
3. Automatic captions with word-level timing.
4. Face/speaker-aware 9:16 framing.
5. Background job queue and real progress reporting.
6. Render history and retry support.
7. Production authentication/rate limiting if the backend becomes public.

Free hosting can sleep and has limited CPU, RAM, bandwidth, and temporary disk. Rendering video is resource-intensive.

## 🔐 Responsible use

Only download, transform, and redistribute videos when you have the necessary rights or permission to do so. Respect copyright, platform terms, and creator rights. ClipMint does not grant rights to third-party content.

## 🗺️ Roadmap

- [x] Android application
- [x] YouTube URL input
- [x] 3/4 clip selection
- [x] 15/30-second selection
- [x] Different clip timestamps
- [x] yt-dlp backend
- [x] FFmpeg rendering
- [x] 9:16 vertical output
- [x] Android MP4 download
- [x] Docker backend
- [x] GitHub Actions APK build
- [x] Backend validation and memory improvements
- [ ] Transcript-based clip selection
- [ ] True AI viral-moment ranking
- [ ] Automatic captions/subtitles
- [ ] Caption styling
- [ ] Multiple aspect ratios
- [ ] Batch rendering
- [ ] Render progress reporting
- [ ] User accounts/history
- [ ] Production-grade job queue

## 📄 License

Add your preferred open-source or proprietary license before distributing the project publicly.
