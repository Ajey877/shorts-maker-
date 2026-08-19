FROM node:22-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg python3 python3-pip ca-certificates curl unzip \
    && rm -rf /var/lib/apt/lists/* \
    && curl -fsSL https://deno.land/install.sh | DENO_INSTALL=/usr/local sh \
    && ln -sf /usr/local/bin/deno /usr/bin/deno \
    && python3 -m pip install --break-system-packages --no-cache-dir -U "yt-dlp[default]"

WORKDIR /app
COPY package.json ./
COPY server.js ./

EXPOSE 10000
CMD ["node", "server.js"]
