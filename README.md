# 🎬 ClipMint — AI YouTube Shorts Maker

ClipMint turns a supported YouTube video into ready-to-share vertical Shorts from Android. The app uses a backend for video extraction, transcript analysis, and FFmpeg rendering so the phone does not have to download and process the full source video itself.

## What ClipMint does

1. Paste a supported YouTube URL.
2. Fetch real video metadata with yt-dlp.
3. Obtain English subtitles/auto-captions when YouTube provides them.
4. Rank transcript windows using hook language, questions, speech density, sentence completeness, and overlap avoidance.
5. Return 3 or 4 distinct 15/30-second Short candidates.
6. Render the selected segment as a 1080×1920 MP4.
7. Burn transcript captions into the exported Short when captions are available.
8. Stream the MP4 to Android and save it under `Downloads/ClipMint`.

## Important product rule

ClipMint does **not** claim to know YouTube's real audience retention unless actual analytics data is supplied. Candidate cards use an **AI Selection Score** based on transcript heuristics. They do not represent measured retention, views, replay rate, or guaranteed virality.

If the backend cannot process a real YouTube URL, the app reports the real error instead of silently replacing the video with a demo/offline result.

## Current feature set

- Jetpack Compose Android UI
- Material 3 components
- YouTube URL validation
- Real backend video metadata
- Transcript/auto-caption extraction when available
- Transcript-based candidate ranking
- AI selection scoring from explainable transcript signals
- 3 or 4 candidate Shorts
- 15 or 30-second selection
- Automatic subtitle timing in candidate metadata
- 9:16 vertical rendering
- Burned-in captions during server rendering
- 720p-or-lower source selection to reduce free-host resource use
- FFmpeg H.264/AAC output with fast-start MP4
- Streaming MP4 responses
- Render concurrency limits and HTTP 429 handling
- Request validation and bounded request size
- Temporary-file cleanup
- Android Downloads/ClipMint output
- GitHub Actions backend container validation, Android unit tests, and debug APK build
- Docker-ready backend

## YouTube extraction reliability

Modern yt-dlp YouTube extraction requires an external JavaScript runtime for full support. The backend image therefore installs **Deno** and the yt-dlp EJS components. yt-dlp retries extraction, fragments, and extractor requests and uses a conservative single-fragment concurrency setting for small/free hosting.

## Architecture

```text
Android APK
   │ HTTPS
   ▼
ClipMint Backend
   ├── /health
   ├── /api/analyze
   │      ├── yt-dlp metadata + YouTube JS challenge solving
   │      ├── yt-dlp subtitles
   │      └── transcript ranking
   └── /api/render
          ├── yt-dlp section download
          ├── FFmpeg 9:16 crop
          ├── optional SRT captions
          └── streamed MP4
```

## Backend

Requirements: Node.js 22, Docker, FFmpeg, yt-dlp, and a supported JavaScript runtime. The provided Dockerfile installs these dependencies for deployment.

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

The app requires the real backend for YouTube processing. Backend failures are surfaced to the user with the server's error message; there is no fake success state for unavailable backend processing.

## Build the APK

The `clipmint-free-apk` branch contains `.github/workflows/build-apk.yml`.

The workflow:

1. Checks `backend/server.js` with Node syntax validation.
2. Builds the backend Docker image so deployment dependencies are validated in CI.
3. Sets up Java 17 and Gradle.
4. Runs Android unit tests.
5. Generates a debug keystore.
6. Builds `assembleDebug`.
7. Uploads the APK as the `shorts-maker-debug-apk` artifact.

## Known limitations

- Transcript ranking is a local heuristic selection engine, not measured YouTube retention or guaranteed virality.
- English subtitles are preferred. Videos without usable subtitles may receive deterministic timestamp selection, but the result is clearly treated as a selection signal rather than retention analytics.
- Face/speaker-aware framing is not yet implemented; current 9:16 crop is center-based.
- Rendering remains CPU-intensive and free hosting can sleep or throttle.
- Production authentication/rate limiting and a durable render queue still need to be added before exposing the backend as a large public service.
- YouTube, copyright, and creator-rights rules still apply. Only process content you are allowed to download and transform.

## Roadmap

- [x] Android application
- [x] YouTube URL input
- [x] 3/4 clip selection
- [x] 15/30-second selection
- [x] yt-dlp backend
- [x] YouTube JS runtime support
- [x] FFmpeg rendering
- [x] 9:16 output
- [x] Android MP4 download
- [x] Backend validation and streaming
- [x] Transcript extraction
- [x] Transcript-based clip ranking
- [x] Explainable AI selection score
- [x] Automatic caption rendering
- [x] Truthful backend failure states
- [ ] Face/speaker-aware framing
- [ ] Background job queue with progress events
- [ ] Batch rendering
- [ ] Optional LLM semantic ranking
- [ ] Production authentication/rate limiting

## Responsible use

ClipMint is a transformation tool. It does not grant rights to third-party videos. Respect copyright, platform terms, and creator permissions.
