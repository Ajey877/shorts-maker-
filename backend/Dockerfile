FROM node:22-bookworm

RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg python3 python3-pip ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && python3 -m pip install --break-system-packages --no-cache-dir -U yt-dlp

WORKDIR /app
COPY package.json ./
COPY server.js ./

EXPOSE 10000
CMD ["node", "server.js"]
