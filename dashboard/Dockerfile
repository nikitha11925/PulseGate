# Single-stage: build the static bundle and serve it with `vite preview`.
# (We keep node_modules so vite.config.js — which imports @vitejs/plugin-react — loads at preview time.)
FROM node:22-alpine
WORKDIR /app
COPY package.json ./
RUN npm install
COPY . .
RUN npm run build
EXPOSE 5173
# Proxy + host/port come from vite.config.js (preview block).
CMD ["npm", "run", "preview", "--", "--host", "0.0.0.0", "--port", "5173"]
