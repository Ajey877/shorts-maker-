const http = require('http');
const { execFile } = require('child_process');
const { promisify } = require('util');
const { mkdtemp, rm, writeFile } = require('fs/promises');
const { createReadStream, readdirSync, statSync } = require('fs');
const { join } = require('path');
const { tmpdir } = require('os');

const execFileAsync = promisify(execFile);
const PORT = Number(process.env.PORT || 10000);
const FFMPEG = process.env.FFMPEG_PATH || '/usr/bin/ffmpeg';
const YTDLP = process.env.YTDLP_PATH || '/usr/local/bin/yt-dlp';
const MAX_BODY_BYTES = 16 * 1024;
const MAX_RENDER_SECONDS = 30;
const MAX_PREVIEW_SECONDS = 30;
const MAX_RENDER_CONCURRENCY = Math.max(1, Number(process.env.MAX_RENDER_CONCURRENCY || 1));
const RATE_LIMIT_PER_MINUTE = Math.max(1, Number(process.env.RATE_LIMIT_PER_MINUTE || 12));
let activeRenders = 0;
const rateBuckets = new Map();

const HOOK_TERMS = /\b(how|why|secret|mistake|never|always|truth|problem|lesson|learned|changed|cost|saved|wrong|best|worst|important|actually|imagine|surprising|nobody|everyone|first|finally|before|after|because)\b/i;
const QUESTION_TERMS = /\?|\b(why|how|what|when|where|who)\b/i;

function cors(res) {
  res.setHeader('Access-Control-Allow-Origin', process.env.CORS_ORIGIN || '*');
  res.setHeader('Access-Control-Allow-Methods', 'GET,POST,OPTIONS,HEAD');
  res.setHeader('Access-Control-Allow-Headers', 'Content-Type, Range, Accept');
  res.setHeader('Access-Control-Expose-Headers', 'Content-Length, Content-Range, Accept-Ranges, Content-Type, Retry-After, X-RateLimit-Limit, X-RateLimit-Remaining');
  res.setHeader('X-Content-Type-Options', 'nosniff');
}

function json(res, status, value) {
  cors(res);
  const body = JSON.stringify(value);
  res.writeHead(status, { 'Content-Type': 'application/json; charset=utf-8', 'Cache-Control': 'no-store' });
  res.end(body);
}

function clientKey(req) {
  const forwarded = String(req.headers['x-forwarded-for'] || '').split(',')[0].trim();
  return forwarded || req.socket.remoteAddress || 'unknown';
}

function rateLimit(req) {
  const now = Date.now();
  const key = clientKey(req);
  const bucket = rateBuckets.get(key) || { startedAt: now, count: 0 };
  if (now - bucket.startedAt >= 60_000) {
    bucket.startedAt = now;
    bucket.count = 0;
  }
  bucket.count += 1;
  rateBuckets.set(key, bucket);
  if (bucket.count > RATE_LIMIT_PER_MINUTE) {
    return { allowed: false, retryAfter: Math.max(1, Math.ceil((60_000 - (now - bucket.startedAt)) / 1000)) };
  }
  return { allowed: true, remaining: Math.max(0, RATE_LIMIT_PER_MINUTE - bucket.count) };
}

setInterval(() => {
  const cutoff = Date.now() - 120_000;
  for (const [key, bucket] of rateBuckets.entries()) {
    if (bucket.startedAt < cutoff) rateBuckets.delete(key);
  }
}, 60_000).unref();

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

function parseTimestamp(value) {
  const cleaned = value.trim().replace(',', '.');
  const parts = cleaned.split(':').map(Number);
  if (parts.some((n) => !Number.isFinite(n))) return null;
  if (parts.length === 3) return parts[0] * 3600 + parts[1] * 60 + parts[2];
  if (parts.length === 2) return parts[0] * 60 + parts[1];
  return null;
}

function stripVtt(text) {
  return text
    .replace(/<\d{2}:\d{2}:\d{2}\.\d{3}>/g, '')
    .replace(/<[^>]+>/g, '')
    .replace(/&amp;/g, '&').replace(/&lt;/g, '<').replace(/&gt;/g, '>')
    .replace(/\s+/g, ' ')
    .trim();
}

function parseVtt(text) {
  const blocks = text.replace(/\r/g, '').split(/\n\s*\n/);
  const segments = [];
  for (const block of blocks) {
    const lines = block.split('\n').map((line) => line.trim()).filter(Boolean);
    const timingIndex = lines.findIndex((line) => line.includes('-->'));
    if (timingIndex < 0) continue;
    const [left, right] = lines[timingIndex].split('-->').map((x) => x.trim().split(/\s+/)[0]);
    const start = parseTimestamp(left);
    const end = parseTimestamp(right);
    const textValue = stripVtt(lines.slice(timingIndex + 1).join(' '));
    if (start != null && end != null && end > start && textValue) segments.push({ start, end, text: textValue });
  }
  const merged = [];
  for (const segment of segments) {
    const previous = merged[merged.length - 1];
    if (previous && segment.start <= previous.end + 0.15 && segment.text === previous.text) {
      previous.end = Math.max(previous.end, segment.end);
    } else {
      merged.push({ ...segment });
    }
  }
  return merged;
}

function cleanTranscript(segments) {
  const seen = new Set();
  return segments.filter((s) => {
    const normalized = s.text.toLowerCase().replace(/[^a-z0-9]+/g, ' ').trim();
    const key = `${Math.round(s.start * 2)}:${normalized}`;
    if (!normalized || seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function scoreCandidate(text, duration, start, end) {
  const words = text.trim().split(/\s+/).filter(Boolean);
  const density = Math.min(1, words.length / Math.max(1, duration * 2.2));
  const hook = HOOK_TERMS.test(text) ? 1 : 0;
  const question = QUESTION_TERMS.test(text) ? 1 : 0;
  const sentenceEnd = /[.!?]$/.test(text.trim()) ? 1 : 0;
  const fillerPenalty = Math.min(0.3, (text.match(/\b(um|uh|you know|like)\b/gi) || []).length * 0.04);
  const score = Math.round(Math.max(0, Math.min(100, 45 + density * 22 + hook * 15 + question * 5 + sentenceEnd * 10 - fillerPenalty * 20)));
  return { score, start, end };
}

function buildCandidates(segments, duration, requestedLength, count) {
  if (!segments.length) return [];
  const length = Math.min(requestedLength, Math.max(10, duration - 1));
  const candidates = [];
  for (const segment of segments) {
    const center = segment.start + Math.min(3, (segment.end - segment.start) / 2);
    let start = Math.max(0, center - length * 0.22);
    let end = start + length;
    const windowSegments = segments.filter((s) => s.end >= start && s.start <= end);
    if (!windowSegments.length) continue;
    start = Math.max(0, windowSegments[0].start - 0.8);
    end = Math.min(duration, start + length);
    const text = windowSegments.map((s) => s.text).join(' ');
    candidates.push({ ...scoreCandidate(text, end - start, start, end), text, start, end });
  }
  candidates.sort((a, b) => b.score - a.score);
  const selected = [];
  for (const candidate of candidates) {
    if (selected.every((other) => Math.abs(other.start - candidate.start) >= length * 0.55)) {
      selected.push(candidate);
      if (selected.length >= count) break;
    }
  }
  return selected;
}

function fallbackClips(duration, count, requestedLength) {
  const length = Math.min(requestedLength, Math.max(10, duration - 1));
  const usable = Math.max(0, duration - length);
  const fractions = count === 4 ? [0.10, 0.36, 0.62, 0.86] : [0.12, 0.50, 0.82];
  return fractions.map((fraction, index) => {
    const start = Math.floor(usable * fraction);
    return { start, end: Math.min(duration, start + length), score: Math.max(60, 82 - index * 3), text: '' };
  });
}

function headlineFromText(text, index) {
  const sentences = text.split(/(?<=[.!?])\s+/).map((s) => s.trim()).filter(Boolean);
  const best = sentences.find((s) => s.length >= 18 && (HOOK_TERMS.test(s) || QUESTION_TERMS.test(s))) || sentences[0];
  if (!best) return ['A strong opening moment', 'The key turning point', 'The insight to remember', 'The payoff'][index] || 'Watch this moment';
  const compact = best.replace(/\s+/g, ' ').replace(/[.?!]+$/, '');
  return compact.length > 58 ? `${compact.slice(0, 55).trim()}…` : compact;
}

function transcriptToSubtitles(segments, start, end) {
  return segments.filter((s) => s.end > start && s.start < end).slice(0, 80).map((s) => ({
    relativeSec: Math.max(0, s.start - start),
    text: s.text,
    highlightWord: s.text.split(/\s+/)[0] || ''
  }));
}

function makeClips(duration, count, requestedLength, transcript) {
  const candidates = buildCandidates(transcript, duration, requestedLength, count);
  const ranges = candidates.length >= Math.min(count, 2) ? candidates : fallbackClips(duration, count, requestedLength);
  return ranges.map((range, index) => {
    const words = range.text.trim().split(/\s+/).filter(Boolean);
    const firstWords = words.slice(0, 7).join(' ');
    const title = firstWords ? (firstWords.length > 55 ? `${firstWords.slice(0, 52)}…` : firstWords) : ['The opening hook', 'The turning point', 'The key insight', 'The payoff'][index];
    const end = Math.min(duration, range.end);
    return {
      id: `clip-${index + 1}-${Math.floor(range.start)}-${Math.floor(end)}`,
      clipIndex: index + 1,
      title,
      hookHeadline: headlineFromText(range.text, index),
      startSeconds: Math.max(0, Math.floor(range.start)),
      endSeconds: Math.max(Math.floor(range.start) + 1, Math.floor(end)),
      durationSeconds: end - range.start,
      rangeFormatted: `${formatTime(range.start)} – ${formatTime(end)}`,
      viralityScore: range.score,
      whyViralReason: range.text ? 'Selection score based on transcript hook language, questions, speech density, sentence completeness, and low filler.' : 'Fallback segment selected because a usable transcript was unavailable.',
      keyTakeaway: firstWords || 'Standalone short-form moment.',
      suggestedHashtags: ['#Shorts', '#viral', '#youtubeshorts'],
      youtubeShortsDescription: range.text ? range.text.slice(0, 220) : 'Generated from a selected segment of the source video.',
      sampleSubtitles: transcriptToSubtitles(transcript, range.start, end)
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
  const { stdout } = await runCommand(YTDLP, ['--dump-single-json', '--skip-download', '--no-playlist', '--no-warnings', '--js-runtimes', 'node', url], { maxBuffer: 20 * 1024 * 1024, timeout: 90000 });
  return JSON.parse(stdout);
}

async function getTranscript(url, workDir) {
  const pattern = join(workDir, 'subs.%(ext)s');
  try {
    await runCommand(YTDLP, ['--skip-download', '--no-playlist', '--no-warnings', '--js-runtimes', 'node', '--write-auto-subs', '--write-subs', '--sub-langs', 'en.*,en', '--sub-format', 'vtt', '--force-overwrites', '-o', pattern, url], { maxBuffer: 12 * 1024 * 1024, timeout: 120000 });
    const subtitleFile = readdirSync(workDir).find((name) => name.endsWith('.vtt'));
    if (!subtitleFile) return [];
    const fs = require('fs');
    return cleanTranscript(parseVtt(fs.readFileSync(join(workDir, subtitleFile), 'utf8')));
  } catch (error) {
    console.warn('Transcript unavailable:', error.message);
    return [];
  }
}

function captionStyleFor(style) {
  switch (String(style || '').toUpperCase()) {
    case 'NEON_GLOW':
      return 'FontName=Arial,FontSize=18,Bold=1,Outline=2,OutlineColour=&H00111111&,PrimaryColour=&H00FFFF00&,Alignment=2,MarginV=120';
    case 'PUNCH_RED':
      return 'FontName=Arial,FontSize=18,Bold=1,Outline=2,OutlineColour=&H00000000&,BackColour=&H000000FF&,BorderStyle=3,Alignment=2,MarginV=120';
    case 'CLEAN_MINIMAL':
      return 'FontName=Arial,FontSize=17,Bold=1,Outline=1,OutlineColour=&H00000000&,PrimaryColour=&H00FFFFFF&,Alignment=2,MarginV=120';
    case 'HORMOZI_BOLD':
    default:
      return 'FontName=Arial,FontSize=19,Bold=1,Outline=3,OutlineColour=&H00000000&,PrimaryColour=&H00FFFFFF&,Alignment=2,MarginV=120';
  }
}

async function createRenderedFile(url, start, end, options = {}) {
  const {
    preview = false,
    captions = true,
    width = 1080,
    height = 1920,
    sourceHeight = 720,
    preset = 'veryfast',
    crf = 23,
    audioBitrate = '128k',
    hookText = '',
    captionStyle = 'HORMOZI_BOLD'
  } = options;
  if (activeRenders >= MAX_RENDER_CONCURRENCY) {
    const error = new Error('Renderer is busy. Please try again in a moment.');
    error.statusCode = 429;
    throw error;
  }
  activeRenders += 1;
  const workDir = await mkdtemp(join(tmpdir(), preview ? 'clipmint-preview-' : 'clipmint-render-'));
  const inputPattern = join(workDir, 'source.%(ext)s');
  const output = join(workDir, preview ? 'preview.mp4' : 'clip.mp4');
  try {
    let transcript = [];
    if (captions && !preview) transcript = await getTranscript(url, workDir);
    await runCommand(YTDLP, [
      '--no-playlist', '--no-warnings', '--js-runtimes', 'node',
      '-f', `best[ext=mp4][height<=${sourceHeight}]/best[height<=${sourceHeight}]/best`,
      '--download-sections', `*${start}-${end}`,
      '--force-overwrites', '--no-part', '--merge-output-format', 'mp4',
      '--ffmpeg-location', FFMPEG, '-o', inputPattern, url
    ], { maxBuffer: 20 * 1024 * 1024, timeout: 180000 });
    const sourceName = readdirSync(workDir).find((name) => /^source\.(mp4|mkv|webm|mov)$/.test(name));
    if (!sourceName) throw new Error('yt-dlp completed but no source video was produced');

    const filters = [`scale=${width}:${height}:force_original_aspect_ratio=increase`, `crop=${width}:${height}`];
    if (hookText && !preview) {
      const hookPath = join(workDir, 'hook.txt');
      await writeFile(hookPath, String(hookText).replace(/[\r\n]+/g, ' ').slice(0, 180), 'utf8');
      filters.push(`drawtext=textfile=${hookPath}:fontcolor=white:fontsize=48:fontweight=bold:box=1:boxcolor=black@0.72:boxborderw=16:x=(w-text_w)/2:y=90`);
    }
    if (captions && !preview) {
      const clipSubs = transcriptToSrt(transcript, start, end);
      if (clipSubs.trim()) {
        const subtitlePath = join(workDir, 'captions.srt');
        await writeFile(subtitlePath, clipSubs, 'utf8');
        filters.push(`subtitles=${subtitlePath}:force_style='${captionStyleFor(captionStyle)}'`);
      }
    }
    await runCommand(FFMPEG, ['-y', '-i', join(workDir, sourceName), '-t', String(end - start), '-vf', filters.join(','), '-c:v', 'libx264', '-preset', preset, '-crf', String(crf), '-c:a', 'aac', '-b:a', audioBitrate, '-movflags', '+faststart', output], { maxBuffer: 10 * 1024 * 1024, timeout: preview ? 180000 : 240000 });
    return { workDir, output };
  } catch (error) {
    await rm(workDir, { recursive: true, force: true }).catch(() => {});
    throw error;
  } finally {
    activeRenders -= 1;
  }
}

function srtTime(seconds) {
  const totalMs = Math.max(0, Math.round(seconds * 1000));
  const h = Math.floor(totalMs / 3600000);
  const m = Math.floor((totalMs % 3600000) / 60000);
  const s = Math.floor((totalMs % 60000) / 1000);
  const ms = totalMs % 1000;
  return `${String(h).padStart(2, '0')}:${String(m).padStart(2, '0')}:${String(s).padStart(2, '0')},${String(ms).padStart(3, '0')}`;
}

function transcriptToSrt(segments, start, end) {
  let index = 1;
  return segments.filter((s) => s.end > start && s.start < end).map((s) => {
    const a = Math.max(0, s.start - start);
    const b = Math.min(end - start, s.end - start);
    if (b <= a) return '';
    return `${index++}\n${srtTime(a)} --> ${srtTime(b)}\n${s.text}\n`;
  }).filter(Boolean).join('\n');
}

function streamVideoFile(req, res, filePath, cleanupDir, inline = false) {
  const size = statSync(filePath).size;
  const rangeHeader = req.headers.range;
  const cleanup = () => rm(cleanupDir, { recursive: true, force: true }).catch(() => {});
  cors(res);
  res.setHeader('Accept-Ranges', 'bytes');
  res.setHeader('Cache-Control', 'no-store');
  res.setHeader('Content-Type', 'video/mp4');
  res.setHeader('Content-Disposition', `${inline ? 'inline' : 'attachment'}; filename="clipmint-short.mp4"`);
  if (!rangeHeader) {
    res.setHeader('Content-Length', size);
    if (req.method === 'HEAD') { res.writeHead(200); return res.end(); }
    const stream = createReadStream(filePath);
    stream.on('error', cleanup);
    res.on('close', cleanup);
    return stream.pipe(res);
  }
  const match = /^bytes=(\d*)-(\d*)$/.exec(rangeHeader);
  if (!match) { res.writeHead(416, { 'Content-Range': `bytes */${size}` }); return res.end(); }
  const start = match[1] ? Number(match[1]) : Math.max(0, size - Number(match[2]));
  const end = match[2] ? Number(match[2]) : size - 1;
  if (start > end || start >= size || !Number.isFinite(start) || !Number.isFinite(end)) { res.writeHead(416, { 'Content-Range': `bytes */${size}` }); return res.end(); }
  const safeEnd = Math.min(end, size - 1);
  const chunkSize = safeEnd - start + 1;
  res.writeHead(206, { 'Content-Length': chunkSize, 'Content-Range': `bytes ${start}-${safeEnd}/${size}` });
  if (req.method === 'HEAD') return res.end();
  const stream = createReadStream(filePath, { start, end: safeEnd });
  stream.on('error', cleanup);
  res.on('close', cleanup);
  stream.pipe(res);
}

async function readJsonBody(req) {
  let raw = '';
  for await (const chunk of req) {
    raw += chunk;
    if (Buffer.byteLength(raw) > MAX_BODY_BYTES) {
      const error = new Error('Request body is too large.'); error.statusCode = 413; throw error;
    }
  }
  try { return JSON.parse(raw || '{}'); }
  catch { const error = new Error('Invalid JSON request.'); error.statusCode = 400; throw error; }
}

const server = http.createServer(async (req, res) => {
  cors(res);
  if (req.method === 'OPTIONS') { res.writeHead(204); return res.end(); }
  const url = new URL(req.url, `http://${req.headers.host || 'localhost'}`);
  try {
    const protectedPaths = new Set(['/api/analyze', '/api/render', '/api/preview']);
    if (protectedPaths.has(url.pathname) && ['GET', 'POST', 'HEAD'].includes(req.method)) {
      const limited = rateLimit(req);
      res.setHeader('X-RateLimit-Limit', RATE_LIMIT_PER_MINUTE);
      res.setHeader('X-RateLimit-Remaining', limited.remaining ?? 0);
      if (!limited.allowed) {
        res.setHeader('Retry-After', limited.retryAfter);
        return json(res, 429, { error: 'Too many requests. Please try again shortly.', retryAfterSeconds: limited.retryAfter });
      }
    }
    if (url.pathname === '/health') {
      return json(res, 200, { ok: true, service: 'clipmint-render', ffmpeg: true, ytdlp: true, activeRenders, maxRenderConcurrency: MAX_RENDER_CONCURRENCY, rateLimitPerMinute: RATE_LIMIT_PER_MINUTE });
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
      const workDir = await mkdtemp(join(tmpdir(), 'clipmint-analysis-'));
      try {
        const transcript = await getTranscript(sourceUrl, workDir);
        return json(res, 200, { video: { id, url: sourceUrl, title: info.title || 'YouTube video', channelName: info.uploader || info.channel || 'YouTube creator', durationSeconds: duration, thumbnailUrl: info.thumbnail || `https://i.ytimg.com/vi/${id}/hqdefault.jpg` }, transcriptAvailable: transcript.length > 0, clips: makeClips(duration, count, length, transcript) });
      } finally { await rm(workDir, { recursive: true, force: true }).catch(() => {}); }
    }
    if (url.pathname === '/api/preview' && (req.method === 'GET' || req.method === 'HEAD')) {
      const sourceUrl = url.searchParams.get('url') || '';
      const start = Math.max(0, Math.floor(validateFiniteNumber(url.searchParams.get('start'), 0)));
      const end = Math.floor(validateFiniteNumber(url.searchParams.get('end'), start + 15));
      if (!getVideoId(sourceUrl)) return json(res, 400, { error: 'Invalid YouTube URL.' });
      if (end <= start || end - start > MAX_PREVIEW_SECONDS) return json(res, 400, { error: `Preview length must be between 1 and ${MAX_PREVIEW_SECONDS} seconds.` });
      const rendered = await createRenderedFile(sourceUrl, start, end, { preview: true, captions: false, width: 360, height: 640, sourceHeight: 360, preset: 'ultrafast', crf: 30, audioBitrate: '96k' });
      return streamVideoFile(req, res, rendered.output, rendered.workDir, true);
    }
    if (url.pathname === '/api/render' && req.method === 'GET') {
      const sourceUrl = url.searchParams.get('url') || '';
      const start = Math.max(0, Math.floor(validateFiniteNumber(url.searchParams.get('start'), 0)));
      const end = Math.floor(validateFiniteNumber(url.searchParams.get('end'), start + 15));
      const captions = url.searchParams.get('captions') !== '0';
      const hookText = String(url.searchParams.get('hook') || '').trim().slice(0, 180);
      const captionStyle = String(url.searchParams.get('captionStyle') || 'HORMOZI_BOLD').trim();
      if (!getVideoId(sourceUrl)) return json(res, 400, { error: 'Invalid YouTube URL.' });
      if (end <= start || end - start > MAX_RENDER_SECONDS) return json(res, 400, { error: `Clip length must be between 1 and ${MAX_RENDER_SECONDS} seconds.` });
      const rendered = await createRenderedFile(sourceUrl, start, end, { captions, hookText, captionStyle });
      return streamVideoFile(req, res, rendered.output, rendered.workDir, false);
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
