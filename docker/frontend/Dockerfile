# syntax=docker/dockerfile:1
# Contexto de construcción: ./frontend  (ver docker-compose.yml)
# Contrato con vecindad-frontend.zip: proyecto Angular llamado "vecindad" => dist/vecindad/browser
FROM node:22-alpine AS build
WORKDIR /app
COPY package*.json ./
RUN --mount=type=cache,target=/root/.npm npm install --no-audit --no-fund
COPY . .
RUN npm run build -- --configuration production

FROM nginx:1.27-alpine
RUN printf 'server {\n  listen 80;\n  root /usr/share/nginx/html;\n  index index.html;\n  location / { try_files $uri $uri/ /index.html; }\n  location = /ngsw.json { add_header Cache-Control "no-cache"; }\n}\n' > /etc/nginx/conf.d/default.conf
COPY --from=build /app/dist/vecindad/browser /usr/share/nginx/html
EXPOSE 80
