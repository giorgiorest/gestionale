# syntax=docker/dockerfile:1
# Build della web app Angular e servizio dei file statici con nginx.
# Contesto di build: radice del repository.

FROM node:22-alpine AS build
WORKDIR /workspace
COPY apps/web/package.json apps/web/package-lock.json ./
RUN npm ci
COPY apps/web/ ./
RUN npm run build -- --configuration production

FROM nginx:alpine
COPY infra/docker/nginx.conf /etc/nginx/conf.d/default.conf
COPY --from=build /workspace/dist/web/browser /usr/share/nginx/html
EXPOSE 80
