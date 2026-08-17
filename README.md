# 🎬 ClipMint — AI YouTube Shorts Maker

> Turn long-form YouTube videos into ready-to-share vertical Shorts from your Android phone.

ClipMint is a mobile-first YouTube Shorts creation project built around an Android app and a lightweight server-side video-processing backend. Paste a YouTube URL, choose how many Shorts you want, select a duration, analyze the video, and render individual 9:16 MP4 clips.

---

## ✨ Features

- 📱 Android app with a clean Shorts-focused interface
- 🔗 YouTube URL analysis
- ✂️ Generate **3 or 4 separate Shorts** from different sections of a source video
- ⏱️ Supports **15-second and 30-second** clip lengths
- 🎯 Non-overlapping clip timestamps to avoid returning the same segment repeatedly
- 🎥 Server-side video extraction with **yt-dlp**
- 🛠️ Video rendering with **FFmpeg**
- 📐 Automatic **9:16 / 1080×1920** output for Shorts/Reels/TikTok-style video
- 📥 Download rendered MP4 files directly to Android
- 📂 Saves downloads under `Downloads/ClipMint`
- ☁️ Docker-ready backend for services such as Render
- 🤖 GitHub Actions workflow for free APK builds
- 💰 Designed so the project can be developed and tested with a **₹0 software budget**

---

## 🏗️ Architecture

```text
┌──────────────────────────┐
│       Android APK        │
│                          │
│  • Paste YouTube URL     │
│  • Select clip count     │
│  • Select clip duration  │
│  • Analyze               │
│  • Download MP4          │
└────────────┬─────────────┘
             │ HTTPS
             ▼
┌──────────────────────────┐
│      ClipMint Backend    │
│                          │
│  /api/analyze            │
│  /api/render             │
│                          │
│  yt-dlp + FFmpeg         │
└──────────────────────────┘
```

The Android application handles the user experience. The backend performs the CPU-heavy YouTube extraction and FFmpeg rendering.

---

## 📁 Project Structure

```text
shorts-maker-/
├── app/                         # Android application
│   └── src/main/...
├── backend/                    # Dockerized video-processing backend
│   ├── Dockerfile
│   ├── package.json
│   └── server.js
├── .github/
│   └── workflows/
│       └── build-apk.yml       # Free GitHub Actions APK build
├── gradle/                     # Gradle configuration
├── build.gradle.kts
├── settings.gradle.kts
└── README.md
```

---

## 🚀 How It Works

### 1. Analyze a YouTube video

The Android app sends the URL and requested clip settings to:

```text
POST /api/analyze
```

The backend uses yt-dlp to retrieve video metadata and duration, then creates separate clip ranges.

### 2. Select different sections

For four clips, the backend distributes the selected ranges across the source video instead of repeatedly selecting the same starting point.

Example:

```text
Source video
│
├── Short 1 ──────► early section
│
├── Short 2 ──────► around 1/3 point
│
├── Short 3 ──────► around 2/3 point
│
└── Short 4 ──────► later section
```

### 3. Render a Short

When a user downloads a clip, the Android app requests:

```text
GET /api/render?url=...&start=...&end=...
```

The backend:

1. Downloads the requested section with yt-dlp.
2. Processes it with FFmpeg.
3. Converts it to 1080×1920.
4. Crops it to a 9:16 frame.
5. Encodes H.264 video + AAC audio.
6. Returns the MP4 to the Android app.

### 4. Save to Android

The Android app stores the completed file in:

```text
Downloads/ClipMint/
```

---

## 🧰 Tech Stack

### Android

- Kotlin
- Jetpack Compose
- Material 3
- OkHttp
- Coroutines
- Android Download/MediaStore APIs

### Backend

- Node.js
- HTTP API
- yt-dlp
- FFmpeg
- Docker

### Build / Deployment

- GitHub Actions
- Gradle
- Render-compatible Docker deployment

---

## ⚙️ Backend API

### Health check

```http
GET /health
```

Example response:

```json
{
  "ok": true,
  "service": "clipmint-render",
  "ffmpeg": true,
  "ytdlp": true
}
```

### Analyze

```http
POST /api/analyze
Content-Type: application/json
```

Request:

```json
{
  "url": "https://www.youtube.com/watch?v=VIDEO_ID",
  "clipCount": 4,
  "clipLength": 30
}
```

The response contains video metadata and individual clip timestamps.

### Render

```http
GET /api/render?url=YOUTUBE_URL&start=START&end=END
```

Returns:

```text
video/mp4
```

---

## 🐳 Run the Backend Locally

### Requirements

- Docker
- Internet connection

Build:

```bash
docker build -t clipmint-backend ./backend
```

Run:

```bash
docker run --rm -p 10000:10000 clipmint-backend
```

Check:

```text
http://localhost:10000/health
```

---

## ☁️ Deploy the Backend

The backend is Docker-based and can be deployed to a Docker-capable hosting service.

For a Render deployment:

```text
Repository: aj976142/shorts-maker-
Branch: clipmint-free-apk
Root Directory: backend
Runtime: Docker
```

After deployment, copy the HTTPS service URL.

Example:

```text
https://your-service.onrender.com
```

Then configure the Android app's `BACKEND_BASE_URL` to point to that service.

> Free hosting can sleep when idle and has CPU/runtime limitations. Video rendering is resource-intensive, so free infrastructure may be slow or unsuitable for large-scale production traffic.

---

## 📱 Build the Android APK

The repository includes a GitHub Actions workflow that builds the debug APK.

Open:

```text
GitHub → Actions → Build APK
```

Select the `clipmint-free-apk` branch and run the workflow if it is not triggered automatically.

After a successful build:

```text
Actions run → Artifacts → shorts-maker-debug-apk
```

Download the artifact ZIP, extract it, and install the APK on Android.

---

## 🔧 Configure the Backend URL

The Android app reads the backend URL from:

```text
app/build.gradle.kts
```

Look for:

```kotlin
buildConfigField("String", "BACKEND_BASE_URL", "\"https://your-backend-url\"")
```

Replace the URL with your deployed backend.

Do not commit private API keys, passwords, signing credentials, or other secrets to the repository.

---

## 🧪 Testing Checklist

Before considering a build ready, test:

- [ ] App opens successfully
- [ ] YouTube URL is accepted
- [ ] Video metadata loads
- [ ] 3-clip mode returns three different ranges
- [ ] 4-clip mode returns four different ranges
- [ ] 15-second mode works
- [ ] 30-second mode works
- [ ] Render request reaches the backend
- [ ] FFmpeg produces a valid MP4
- [ ] Output is 1080×1920 / 9:16
- [ ] Download completes on Android
- [ ] File appears in `Downloads/ClipMint`
- [ ] Render errors are shown to the user

---

## ⚠️ Current Limitations

### Clip selection is not fully semantic AI yet

The current backend selects distinct time ranges using the video's duration. It does **not** yet understand the transcript, identify the strongest hooks, or rank moments using a true AI/LLM pipeline.

A future version can add:

- transcript extraction
- speech-to-text
- semantic scene analysis
- hook detection
- engagement scoring
- automatic subtitle generation
- face/speaker detection
- silence removal
- AI-generated titles and captions

### Free hosting limitations

Video downloading and FFmpeg encoding consume significant CPU, memory, bandwidth, and temporary disk space. Free hosting is suitable for experimentation but is not a guarantee of production-scale capacity.

### YouTube availability

YouTube may change its extraction requirements or restrict automated access. yt-dlp must therefore be kept updated.

---

## 🔐 Responsible Use

Only download, transform, and redistribute videos when you have the necessary rights or permission to do so. Respect copyright, platform terms, and the rights of video creators.

ClipMint is a technical project for creating short-form video edits; it does not grant rights to third-party content.

---

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
- [ ] Transcript-based clip selection
- [ ] True AI viral-moment ranking
- [ ] Automatic captions/subtitles
- [ ] Caption styling
- [ ] Multiple aspect ratios
- [ ] Batch rendering
- [ ] Render progress reporting
- [ ] User accounts/history
- [ ] Production-grade job queue

---

## 🤝 Contributing

Contributions, bug reports, and ideas are welcome.

A good contribution should:

1. Explain the problem being solved.
2. Keep changes focused.
3. Avoid committing secrets.
4. Include testing steps where possible.

---

## 📄 License

Add your preferred open-source or proprietary license before distributing this project publicly.

---

## ⭐ Project Goal

ClipMint is being built around one simple idea:

> **Paste a long video. Find useful moments. Turn them into Shorts. Download them to your phone.**

The project is intentionally designed to start with a low-cost/free development setup and evolve toward a more capable AI-powered Shorts generation platform.
