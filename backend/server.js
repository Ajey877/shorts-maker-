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
const MAX_RENDER_CONCURRENCY = Math.max(1, Number(process.env.MAX_RENDER_CONCURRENCY || 1));
let activeRenders = 0;

const HOOK_TERMS = /\b(how|why|secret|mistake|never|always|truth|problem|lesson|learned|changed|cost|saved|wrong|best|worst|important|actually|imagine|surprising|nobody|everyone|first|finally|before|after|because)\b/i;
const QUESTION_TERMS = /\?|\b(why|how|what|when|where|who)\b/i;

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
    const key = `${Math.round(s.start * 2)}:${s.text.toLowerCase()}`;
    if (seen.has(key)) return false;
    seen.add(key);
    return true;
  });
}

function scoreCandidate(text, duration, start, end) {
  const words = text.trim().split(/\s+/).filter(Boolean);
  const wordCount = words.length;
  const density = Math.min(1, wordCount / Math.max(1, duration * 2.2));
  const hook = HOOK_TERMS.test(text) ? 1 : 0;
  const question = QUESTION_TERMS.test(text) ? 1 : 0;
  const sentenceEnd = /[.!?]$/.test(text.trim()) ? 1 : 0;
  const filler = /\b(um|uh|you know|like)\b/gi;
  const fillerPenalty = Math.min(0.3, (text.match(filler) || []).length * 0.04);
  const score = Math.round(Math.max(0, Math.min(100, 45 + density * 22 + hook * 15 + question * 5 + sentenceEnd * 10 - fillerPenalty * 20)));
  return { score, wordCount, start, end };
}

function buildCandidates(segments, duration, requestedLength, count) {
  if (!segments.length) return [];
  const length = Math.min(requestedLength, Math.max(10, duration - 1));
  const candidates = [];
  for (let i = 0; i < segments.length; i += 1) {
    const center = segments[i].start + Math.min(3, (segments[i].end - segments[i].start) / 2);
    let start = Math.max(0, center - length * 0.22);
    let end = start + length;
    const windowSegments = segments.filter((s) => s.end >= start && s.start <= end);
    if (!windowSegments.length) continue;
    start = Math.max(0, windowSegments[0].start - 0.8);
    end = Math.min(duration, start + length);
    const text = windowSegments.map((s) => s.text).join(' ');
    const scored = scoreCandidate(text, end - start, start, end);
    candidates.push({ ...scored, text, start, end });
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
      hookHeadline: HOOK_TERMS.test(range.text) ? 'THIS CHANGES EVERYTHING' : 'WATCH THIS',
      startSeconds: Math.max(0, Math.floor(range.start)),
      endSeconds: Math.max(Math.floor(range.start) + 1, Math.floor(end)),
      durationSeconds: end - range.start,
      rangeFormatted: `${formatTime(range.start)} – ${formatTime(end)}`,
      viralityScore: range.score,
      whyViralReason: range.text ? 'Ranked from transcript signals including hook language, questions, speech density, and sentence completeness.' : 'Fallback segment selected because a usable transcript was unavailable.',
      keyTakeaway: firstWords || 'Standalone short-form moment.',
      suggestedHashtags: ['#Shorts', '#viral', '#youtubeshorts'],
      youtubeShortsDescription: range.text ? range.text.slice(0, 220) : 'Generated from a selected segment of the source video.',
      sampleSubtitles: transcriptToSubtitles(transcript, range.start, end)
    };
  });
}

function transcriptToSubtitles(segments, start, end) {
  return segments.filter((s) => s.end > start && s.start < end).slice(0, 80).map((s) => ({
    relativeSec: Math.max(0, s.start - start),
    text: s.text,
    highlightWord: s.text.split(/\s+/)[0] || ''
  }));
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

async function getTranscript(url, workDir) {
  const pattern = join(workDir, 'subs.%(ext)s');
  try {
    await runCommand(YTDLP, [
      '--skip-download', '--no-playlist', '--no-warnings', '--js-runtimes', 'node',
      '--write-auto-subs', '--write-subs', '--sub-langs', 'en.*,en', '--sub-format', 'vtt',
      '--force-overwrites', '-o', pattern, url
    ], { maxBuffer: 12 * 1024 * 1024, timeout: 120000 });
    const subtitleFile = readdirSync(workDir).find((name) => name.endsWith('.vtt'));
    if (!subtitleFile) return [];
    const text = require('fs').readFileSync(join(workDir, subtitleFile), 'utf8');
    return cleanTranscript(parseVtt(text));
  } catch (error) {
    console.warn('Transcript unavailable:', error.message);
    return [];
  }
}

async function renderClip(url, start, end, captions = true) {
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
    let transcript = [];
    if (captions) transcript = await getTranscript(url, workDir);
    await runCommand(YTDLP, [
      '--no-playlist', '--no-warnings', '--js-runtimes', 'node',
      '-f', 'best[ext=mp4][height<=720]/best[height<=720]/best',
      '--download-sections', `*${start}-${end}`,
      '--force-overwrites', '--no-part', '--merge-output-format', 'mp4',
      '--ffmpeg-location', FFMPEG, '-o', inputPattern, url
    ], { maxBuffer: 20 * 1024 * 1024, timeout: 180000 });
    const sourceName = readdirSync(workDir).find((name) => /^source\.(mp4|mkv|webm|mov)$/.test(name));
    if (!sourceName) throw new Error('yt-dlp completed but no source video was produced');

    const clipSubs = transcriptToSrt(transcript, start, end);
    const subtitlePath = join(workDir, 'captions.srt');
    const filters = ['scale=1080:1920:force_original_aspect_ratio=increase', 'crop=1080:1920'];
    if (captions && clipSubs) {
      await writeFile(subtitlePath, clipSubs, 'utf8');
      if (clipSubs.trim()) filters.push(`subtitles=${subtitlePath}:force_style='FontName=Arial,FontSize=18,Bold=1,Outline=2,Alignment=2,MarginV=120'`);
    }
    await runCommand(FFMPEG, [
      '-y', '-i', join(workDir, sourceName), '-t', String(end - start),
      '-vf', filters.join(','), '-c:v', 'libx264', '-preset', 'veryfast', '-crf', '23',
      '-c:a', 'aac', '-b:a', '128k', '-movflags', '+faststart', output
    ], { maxBuffer: 10 * 1024 * 1024, timeout: 240000 });
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

function streamFile(res, filePath, cleanupDir) {
  const size = statSync(filePath).size;
  cors(res);
  res.writeHead(200, {
    'Content-Type': 'video/mp4', 'Content-Length': size,
    'Content-Disposition': 'attachment; filename="clipmint-short.mp4"', 'Cache-Control': 'no-store'
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
    if (url.pathname === '/health') return json(res, 200, { ok: true, service: 'clipmint-render', ffmpeg: true, ytdlp: true, activeRenders });

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
        return json(res, 200, {
          video: { id, url: sourceUrl, title: info.title || 'YouTube video', channelName: info.uploader || info.channel || 'YouTube creator', durationSeconds: duration, thumbnailUrl: info.thumbnail || `https://i.ytimg.com/vi/${id}/hqdefault.jpg` },
          transcriptAvailable: transcript.length > 0,
          clips: makeClips(duration, count, length, transcript)
        });
      } finally { await rm(workDir, { recursive: true, force: true }).catch(() => {}); }
    }

    if (url.pathname === '/api/render' && req.method === 'GET') {
      const sourceUrl = url.searchParams.get('url') || '';
      const start = Math.max(0, Math.floor(validateFiniteNumber(url.searchParams.get('start'), 0)));
      const end = Math.floor(validateFiniteNumber(url.searchParams.get('end'), start + 15));
      const captions = url.searchParams.get('captions') !== '0';
      if (!getVideoId(sourceUrl)) return json(res, 400, { error: 'Invalid YouTube URL.' });
      if (end <= start || end - start > MAX_RENDER_SECONDS) return json(res, 400, { error: `Clip length must be between 1 and ${MAX_RENDER_SECONDS} seconds.` });
      const rendered = await renderClip(sourceUrl, start, end, captions);
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
