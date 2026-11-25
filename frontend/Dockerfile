ARG APP

FROM node:24-alpine AS build
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

FROM nginxinc/nginx-unprivileged:1.31-alpine-slim AS runtime
USER root
RUN apk upgrade --no-cache
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1

FROM runtime AS web
ENV JAYPAY_MEMBERSHIP_URL=http://membership-service:8080 \
    JAYPAY_BANKING_URL=http://banking-service:8080 \
    JAYPAY_MONEY_URL=http://money-service:8080 \
    JAYPAY_REMITTANCE_URL=http://remittance-service:8080 \
    JAYPAY_PAYMENT_URL=http://payment-service:8080

FROM runtime AS admin
ENV JAYPAY_MEMBERSHIP_URL=http://membership-service:8080 \
    JAYPAY_PAYMENT_URL=http://payment-service:8080 \
    JAYPAY_MONEY_AGGREGATION_URL=http://money-aggregation-service:8080 \
    JAYPAY_MONEY_QUERY_URL=http://money-query-service:8080 \
    JAYPAY_AUTH_URL=http://auth-service:8080

FROM ${APP}
ARG APP
COPY apps/${APP}/nginx.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/apps/${APP}/dist /usr/share/nginx/html
USER 101
