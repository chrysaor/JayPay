ARG APP

FROM node:26.13.1-alpine@sha256:6bd9ab43c0ec0190ca7b2d193906cb8b7edf6e7f4afa43ddeccd6452710bffb5 AS build
WORKDIR /app
COPY package.json package-lock.json ./
COPY apps/web/package.json apps/web/
COPY apps/admin/package.json apps/admin/
COPY packages/api/package.json packages/api/
COPY packages/ui/package.json packages/ui/
RUN npm ci
COPY . .
ARG APP
RUN test -n "$APP" || { echo "Build with --build-arg APP=web or APP=admin" >&2; exit 1; }
RUN npm run build --workspace "apps/$APP"

FROM nginx:1.31.10-alpine-slim@sha256:c293b28729906d97fa5302fce3bd79bd27a3ea9a5d9604e21c6ff0ebf14549fc AS runtime
RUN rm /etc/nginx/conf.d/default.conf && chown 101:101 /etc/nginx/conf.d
COPY nginx.conf /etc/nginx/nginx.conf
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1

ARG APP
COPY site.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/apps/${APP}/dist /usr/share/nginx/html
USER 101
