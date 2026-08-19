# 🎬 ClipMint — AI YouTube Shorts Maker

ClipMint turns a YouTube video into ready-to-share vertical Shorts from Android. The app uses a lightweight backend for the expensive video work so the phone does not have to download and process the full source video itself.

## What ClipMint does

1. Paste a supported YouTube URL.
2. Fetch real video metadata and duration.
3. Try to obtain English subtitles/auto-captions with yt-dlp.
4. Rank transcript windows using hook language, questions, speech density, sentence completeness, and overlap avoidance.
5. Return 3 or 4 distinct 15/30-second Short candidates.
6. Open a real low-resolution preview stream for the selected segment when the backend is reachable.
7. Trim, seek, play/pause, change caption style, framing mode, and hook text in the Android editor.
8. Render the selected segment as 1080×1920 MP4.
9. Burn transcript captions into the exported Short when captions are available.
10. Stream the MP4 to Android with visible download progress and save it under `Downloads/ClipMint`.

The semantic ranking is deliberately free and self-hostable: it does not require a paid AI API. A future optional LLM provider can be added for deeper semantic ranking without changing the rendering pipeline.

## Current feature set

- Jetpack Compose + Material 3 UI
- System/dynamic Material 3 colors on supported Android versions
- Keyboard-safe creator workflow with IME/navigation insets
- Real backend video metadata for live URLs
- Transcript/auto-caption extraction when YouTube provides it
- Transcript-based candidate ranking
- Hook/question/density/completeness scoring
- 3 or 4 candidate Shorts
- 15 or 30-second selection
- Automatic subtitle timing in candidate metadata
- Real low-resolution Media3 preview stream
- True playback seek/play/pause in the editor
- 9:16 vertical rendering
- Burned-in captions during server rendering
- 720p-or-lower source selection for final rendering and 360p preview streaming
- FFmpeg H.264/AAC output with fast-start MP4
- HTTP Range support for preview playback
- Streaming MP4 responses instead of loading rendered files into Node memory
- Render concurrency limits and HTTP 429 handling
- Request validation and bounded request size
- Temporary-file cleanup
- Android Downloads/ClipMint output
- Visible client-side download progress
- Built-in sample presets for offline UI/demo testing
- Honest backend errors for real URLs instead of fabricated metadata/timestamps
- GitHub Actions backend syntax check and debug APK build
- Docker-ready backend

## Architecture

```text
Android APK
   │ HTTPS
   ▼
ClipMint Backend
   ├── /health
   ├── /api/analyze
   │      ├── yt-dlp metadata
   │      ├── yt-dlp subtitles
   │      └── transcript ranking
   ├── /api/preview
   │      ├── yt-dlp short section
   │      ├── FFmpeg 360×640 encode
   │      └── HTTP Range streaming
   └── /api/render
          ├── yt-dlp short section
          ├── FFmpeg 1080×1920 crop
          ├── optional SRT captions
          └── streamed MP4
```

## Backend

Requirements: Node.js 22, Docker or local FFmpeg + yt-dlp.

```bash
docker build -t clipmint-backend ./backend
docker run --rm -p 10000:10000 clipmint-backend
```

Health check:

```text
http://localhost:10000/health
```

### Environment variables

- `PORT` — HTTP port, default `10000`
- `FFMPEG_PATH` — FFmpeg executable path
- `YTDLP_PATH` — yt-dlp executable path
- `MAX_RENDER_CONCURRENCY` — simultaneous renders, default `1`
- `CORS_ORIGIN` — allowed origin, default `*`

The default concurrency of one render is intentional for free/small hosting.

## Android

`app/build.gradle.kts` contains the backend URL in `BuildConfig.BACKEND_BASE_URL`. Use an HTTPS deployment URL for a release APK.

Live YouTube URLs require a reachable backend. When the backend is unavailable, ClipMint stops cleanly and shows an actionable error instead of displaying fabricated video metadata or fake clip timestamps. Built-in sample presets can still be used for UI/demo testing.

The Studio is Material 3 based and uses the device's dynamic color scheme on supported Android versions. Media3 provides the actual clip playback preview; the final server render remains the source of truth for exported quality.

## Build the APK

The `clipmint-free-apk` branch contains `.github/workflows/build-apk.yml`.

The workflow:

1. Checks `backend/server.js` with Node syntax validation.
2. Sets up Java 17 and Gradle.
3. Generates a debug keystore.
4. Builds `assembleDebug`.
5. Uploads the APK as the `shorts-maker-debug-apk` artifact.

## Important limitations

- The transcript scorer is a local heuristic engine, not a claim of measured YouTube retention or guaranteed virality.
- English subtitles are preferred. Videos without usable subtitles may have fewer or no semantic candidates.
- Face/speaker tracking is not yet implemented; the current 9:16 framing is crop-based.
- The current render endpoint is synchronous. A persistent background job queue with stage-by-stage server progress is still a future scale improvement.
- There is no production authentication/rate limiting yet. Do not expose a public backend without adding those controls.
- Free hosting remains CPU/RAM/bandwidth constrained and may sleep or throttle.
- YouTube, copyright, and creator-rights rules still apply. Only process content you are allowed to download and transform.

## Roadmap

- [x] Android application
- [x] YouTube URL input
- [x] 3/4 clip selection
- [x] 15/30-second selection
- [x] yt-dlp backend
- [x] FFmpeg rendering
- [x] 9:16 output
- [x] Android MP4 download
- [x] Backend validation and streaming
- [x] Transcript extraction
- [x] Transcript-based clip ranking
- [x] Automatic caption rendering
- [x] Material 3 Studio UI
- [x] Real Media3 video preview
- [x] Render/download progress
- [ ] Face/speaker-aware framing
- [ ] Persistent background job queue with stage progress
- [ ] Batch rendering
- [ ] Optional LLM semantic ranking
- [ ] Production authentication/rate limiting

## Responsible use

ClipMint is a transformation tool. It does not grant rights to third-party videos. Respect copyright, platform terms, and creator permissions.
