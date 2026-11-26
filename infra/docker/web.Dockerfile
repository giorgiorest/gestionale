# syntax=docker/dockerfile:1
# Build della web app Angular e servizio dei file statici con nginx.
# Contesto di build: radice del repository.
# Configurazione a runtime (config.json) dalle variabili: WEB_API_URL, OIDC_AUTHORITY, OIDC_CLIENT_ID.

# I file statici sono indipendenti dall'architettura: build una volta sola sulla piattaforma della build.
FROM --platform=$BUILDPLATFORM docker.io/library/node:26-alpine AS build
WORKDIR /workspace
COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci
COPY apps/web/ ./
RUN npm run build -- --configuration production

FROM docker.io/library/nginx:alpine
COPY infra/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY infra/docker/web/40-runtime-config.sh /docker-entrypoint.d/40-runtime-config.sh
# Fine riga Unix anche se il repository è stato scaricato su Windows con CRLF.
RUN sed -i 's/\r$//' /docker-entrypoint.d/40-runtime-config.sh && chmod 0755 /docker-entrypoint.d/40-runtime-config.sh
COPY --from=build /workspace/dist/web/browser /usr/share/nginx/html
EXPOSE 80
