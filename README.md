# 🎬 ClipMint — AI YouTube Shorts Maker

ClipMint turns a YouTube video into ready-to-share vertical Shorts from Android. The app uses a lightweight backend for the expensive video work so the phone does not have to download and process the full source video itself.

## What ClipMint does

1. Paste a supported YouTube URL.
2. Fetch real video metadata and duration.
3. Try to obtain English subtitles/auto-captions with yt-dlp.
4. Rank transcript windows using hook language, questions, speech density, sentence completeness, and overlap avoidance.
5. Return 3 or 4 distinct 15/30-second Short candidates.
6. Render the selected segment as 1080×1920 MP4.
7. Burn transcript captions into the exported Short when captions are available.
8. Stream the MP4 to Android and save it under `Downloads/ClipMint`.

The semantic ranking is deliberately free and self-hostable: it does not require a paid AI API. A future optional LLM provider can be added for deeper semantic ranking without changing the rendering pipeline.

## Current feature set

- Jetpack Compose + Material 3 UI
- System/dynamic Material 3 colors on supported Android versions
- Keyboard-safe creator workflow with IME/navigation insets
- YouTube URL validation
- Real backend video metadata for live URLs
- Transcript/auto-caption extraction when YouTube provides it
- Transcript-based candidate ranking
- Hook/question/density/completeness scoring
- 3 or 4 candidate Shorts
- 15 or 30-second selection
- Automatic subtitle timing in candidate metadata
- 9:16 vertical rendering
- Burned-in captions during server rendering
- 720p-or-lower source selection to reduce free-host resource use
- FFmpeg H.264/AAC output with fast-start MP4
- Streaming MP4 responses instead of loading rendered files into Node memory
- Render concurrency limits and HTTP 429 handling
- Request validation and bounded request size
- Temporary-file cleanup
- Android Downloads/ClipMint output
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
   └── /api/render
          ├── yt-dlp section download
          ├── FFmpeg 9:16 crop
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

Live YouTube URLs require a reachable backend. When the backend is unavailable, ClipMint now stops cleanly and shows an actionable error instead of displaying fabricated video metadata or fake clip timestamps. Built-in sample presets can still be used for UI/demo testing.

The Studio UI uses Material 3 components, system/dynamic colors, proper keyboard insets, consistent cards/buttons/chips, and a clearer separation between source metadata, suggested clips, preview, and export actions.

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
- The current in-app preview uses the source thumbnail; final export renders the actual video on the backend.
- Face/speaker tracking is not yet implemented; the current 9:16 crop is center-based.
- Rendering remains CPU-intensive and free hosting can sleep or throttle.
- There is no production authentication/rate limiting yet. Do not expose a public backend without adding those controls.
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
- [ ] Face/speaker-aware framing
- [ ] Background job queue with progress events
- [ ] Batch rendering
- [ ] Optional LLM semantic ranking
- [ ] Production authentication/rate limiting

## Responsible use

ClipMint is a transformation tool. It does not grant rights to third-party videos. Respect copyright, platform terms, and creator permissions.
