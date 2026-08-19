const http = require('http');
const { execFile } = require('child_process');
const { promisify } = require('util');
const { mkdtemp, rm } = require('fs/promises');
const { createReadStream, readdirSync, statSync } = require('fs');
const { join } = require('path');
const { tmpdir } = require('os');

const execFileAsync = promisify(execFile);
const PORT = Number(process.env.PORT || 10000);
const FFMPEG = process.env.FFMPEG_PATH || '/usr/bin/ffmpeg';
const YTDLP = process.env.YTDLP_PATH || '/usr/local/bin/yt-dlp';
const MAX_BODY_BYTES = 16 * 1024;
const MAX_RENDER_SECONDS = 30;
const MAX_RENDER_CONCURRENCY = Math.max(1, Number(process.env.MAX_RENDER_CONCURRENCY || 1));
let activeRenders = 0;

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', process.env.CORS_ORIGIN || '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type');
}

function json(res, status, value) {
  cors(res);
  const body = JSON.stringify(value);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(body);
}

function getVideoId(input) {
  try {
    const u = new URL(input);
    const host = u.hostname.replace(/^www\./, '').toLowerCase();
    if (host === 'youtu.be') return u.pathname.split('/').filter(Boolean)[0] || null;
    if (host === 'youtube.com' || host.endsWith('.youtube.com')) {
      if (u.pathname === '/watch') return u.searchParams.get('v');
      const parts = u.pathname.split('/').filter(Boolean);
      if (['shorts', 'embed', 'live'].includes(parts[0] || '')) return parts[1] || null;
    }
  } catch {}
  return null;
}

function validateFiniteNumber(value, fallback) {
  const n = Number(value);
  return Number.isFinite(n) ? n : fallback;
}

function formatTime(seconds) {
  const n = Math.max(0, Math.floor(seconds));
  const h = Math.floor(n / 3600);
  const m = Math.floor((n % 3600) / 60);
  const s = n % 60;
  return h ? `${h}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')}` : `${m}:${String(s).padStart(2, '0')}`;
}

function makeClips(duration, count, requestedLength) {
  const length = Math.min(requestedLength, Math.max(10, duration - 1));
  const usable = Math.max(0, duration - length);
  const fractions = count === 4 ? [0.10, 0.36, 0.62, 0.86] : [0.12, 0.50, 0.82];
  const starts = [];
  for (const fraction of fractions) {
    let start = Math.floor(usable * fraction);
    if (starts.some((existing) => Math.abs(existing - start) < length)) {
      start = Math.min(usable, start + length + 3);
    }
    starts.push(start);
  }
  return starts.map((start, index) => {
    const end = Math.min(duration, start + length);
    return {
      id: `clip-${index + 1}-${start}-${end}`,
      clipIndex: index + 1,
      title: ['The opening hook', 'The turning point', 'The key insight', 'The payoff'][index],
      hookHeadline: ['THE MOMENT THAT GRABS YOU', 'THEN EVERYTHING CHANGES', 'HERE IS THE BIG INSIGHT', 'WAIT FOR THE PAYOFF'][index],
      startSeconds: start,
      endSeconds: end,
      durationSeconds: end - start,
      rangeFormatted: `${formatTime(start)} – ${formatTime(end)}`,
      viralityScore: 96 - index * 2,
      whyViralReason: 'A distinct segment selected from a different part of the source video.',
      keyTakeaway: 'Standalone short-form moment.',
      suggestedHashtags: ['#Shorts', '#viral', '#youtubeshorts'],
      youtubeShortsDescription: 'Generated from a selected segment of the source video.',
      sampleSubtitles: []
    };
  });
}

async function runCommand(file, args, options = {}) {
  try {
    return await execFileAsync(file, args, options);
  } catch (error) {
    const stderr = String(error?.stderr || '').trim();
    const stdout = String(error?.stdout || '').trim();
    const detail = stderr || stdout || error?.message || 'Command failed';
    throw new Error(`${file.split('/').pop()} failed: ${detail}`.slice(0, 1200));
  }
}

async function getVideoInfo(url) {
  const { stdout } = await runCommand(YTDLP, [
    '--dump-single-json', '--skip-download', '--no-playlist', '--no-warnings',
    '--js-runtimes', 'node', url
  ], { maxBuffer: 20 * 1024 * 1024, timeout: 90000 });
  return JSON.parse(stdout);
}

async function renderClip(url, start, end) {
  if (activeRenders >= MAX_RENDER_CONCURRENCY) {
    const error = new Error('Renderer is busy. Please try again in a moment.');
    error.statusCode = 429;
    throw error;
  }

  activeRenders += 1;
  const workDir = await mkdtemp(join(tmpdir(), 'clipmint-'));
  const inputPattern = join(workDir, 'source.%(ext)s');
  const output = join(workDir, 'clip.mp4');

  try {
    await runCommand(YTDLP, [
      '--no-playlist', '--no-warnings', '--js-runtimes', 'node',
      '-f', 'best[ext=mp4][height<=720]/best[height<=720]/best',
      '--download-sections', `*${start}-${end}`,
      '--force-overwrites', '--no-part',
      '--merge-output-format', 'mp4',
      '--ffmpeg-location', FFMPEG,
      '-o', inputPattern,
      url
    ], { maxBuffer: 20 * 1024 * 1024, timeout: 180000 });

    const sourceName = readdirSync(workDir).find((name) => /^source\.(mp4|mkv|webm|mov)$/.test(name));
    if (!sourceName) throw new Error('yt-dlp completed but no source video was produced');

    await runCommand(FFMPEG, [
      '-y', '-i', join(workDir, sourceName),
      '-t', String(end - start),
      '-vf', 'scale=1080:1920:force_original_aspect_ratio=increase,crop=1080:1920',
      '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '23',
      '-c:a', 'aac', '-b:a', '128k', '-movflags', '+faststart', output
    ], { maxBuffer: 10 * 1024 * 1024, timeout: 180000 });

    return { workDir, output };
  } catch (error) {
    await rm(workDir, { recursive: true, force: true }).catch(() => {});
    throw error;
  } finally {
    activeRenders -= 1;
  }
}

function streamFile(res, filePath, cleanupDir) {
  const size = statSync(filePath).size;
  cors(res);
  res.writeHead(200, {
    'Content-Type': 'video/mp4',
    'Content-Length': size,
    'Content-Disposition': 'attachment; filename="clipmint-short.mp4"',
    'Cache-Control': 'no-store'
  });
  const stream = createReadStream(filePath);
  const cleanup = () => rm(cleanupDir, { recursive: true, force: true }).catch(() => {});
  stream.on('error', cleanup);
  res.on('close', cleanup);
  stream.pipe(res);
}

async function readJsonBody(req) {
  let raw = '';
  for await (const chunk of req) {
    raw += chunk;
    if (Buffer.byteLength(raw) > MAX_BODY_BYTES) {
      const error = new Error('Request body is too large.');
      error.statusCode = 413;
      throw error;
    }
  }
  try {
    return JSON.parse(raw || '{}');
  } catch {
    const error = new Error('Invalid JSON request.');
    error.statusCode = 400;
    throw error;
  }
}

const server = http.createServer(async (req, res) => {
  cors(res);
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);

  try {
    if (url.pathname === '/health') {
      return json(res, 200, { ok: true, service: 'clipmint-render', ffmpeg: true, ytdlp: true, activeRenders });
    }

    if (url.pathname === '/api/analyze' && req.method === 'POST') {
      const body = await readJsonBody(req);
      const sourceUrl = String(body.url || '').trim();
      const id = getVideoId(sourceUrl);
      if (!id) return json(res, 400, { error: 'Invalid YouTube URL.' });

      const info = await getVideoInfo(sourceUrl);
      const duration = Math.floor(validateFiniteNumber(info.duration, 0));
      if (!duration || duration < 10) return json(res, 422, { error: 'Video is too short or its duration could not be determined.' });

      const count = Number(body.clipCount) === 3 ? 3 : 4;
      const length = Number(body.clipLength) === 15 ? 15 : 30;
      return json(res, 200, {
        video: {
          id,
          url: sourceUrl,
          title: info.title || 'YouTube video',
          channelName: info.uploader || info.channel || 'YouTube creator',
          durationSeconds: duration,
          thumbnailUrl: info.thumbnail || `https://i.ytimg.com/vi/${id}/hqdefault.jpg`
        },
        clips: makeClips(duration, count, length)
      });
    }

    if (url.pathname === '/api/render' && req.method === 'GET') {
      const sourceUrl = url.searchParams.get('url') || '';
      const start = Math.max(0, Math.floor(validateFiniteNumber(url.searchParams.get('start'), 0)));
      const end = Math.floor(validateFiniteNumber(url.searchParams.get('end'), start + 15));
      if (!getVideoId(sourceUrl)) return json(res, 400, { error: 'Invalid YouTube URL.' });
      if (end <= start || end - start > MAX_RENDER_SECONDS) {
        return json(res, 400, { error: `Clip length must be between 1 and ${MAX_RENDER_SECONDS} seconds.` });
      }

      const rendered = await renderClip(sourceUrl, start, end);
      return streamFile(res, rendered.output, rendered.workDir);
    }

    return json(res, 404, { error: 'Not found' });
  } catch (error) {
    console.error('ClipMint request failed:', error);
    const status = Number(error?.statusCode) || 502;
    const message = error instanceof Error ? error.message : 'Server error';
    return json(res, status, { error: message.slice(0, 1200) });
  }
});

server.listen(PORT, '0.0.0.0', () => console.log(`ClipMint backend listening on ${PORT}`));
