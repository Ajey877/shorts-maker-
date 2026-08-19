# ClipMint 2.3 Release Candidate

## Highlights

- Material 3 creator-first Android UI.
- Real Media3 video preview instead of a thumbnail-only editor.
- Honest live-URL failure handling.
- Transcript-based clip ranking with explicit selection signals.
- Trim, seek, playback, caption style, hook editing, and 9:16 crop controls.
- Server-side caption rendering with selectable caption styles.
- Preview caching and HTTP Range playback support.
- Batch export for all suggested Shorts.
- Android WorkManager background export with network constraints, retry/backoff, and progress.
- Backend request validation, cleanup, render concurrency controls, and lightweight rate limiting.
- Android unit-test step in GitHub Actions.

## Release gate

A release build should only be considered ready after the latest GitHub Actions run is green and the APK artifact has been installed on a real Android phone and tested end-to-end with a permitted YouTube video.

## Known next-stage work

- Face/speaker-aware dynamic framing.
- Persistent/distributed server-side job queue.
- Optional LLM semantic ranking.
- Production authentication and distributed rate limiting.
- Expanded UI/integration tests and device performance profiling.
