FROM node:24-alpine AS build
WORKDIR /app
COPY package.json package-lock.json ./
RUN npm ci
COPY . .
RUN npm run build

FROM nginxinc/nginx-unprivileged:1.31-alpine
USER root
RUN apk upgrade --no-cache
ENV NGINX_ENTRYPOINT_LOCAL_RESOLVERS=1 \
    JAYPAY_MEMBERSHIP_URL=http://membership-service:8080 \
    JAYPAY_BANKING_URL=http://banking-service:8080 \
    JAYPAY_MONEY_URL=http://money-service:8080 \
    JAYPAY_REMITTANCE_URL=http://remittance-service:8080 \
    JAYPAY_PAYMENT_URL=http://payment-service:8080
COPY nginx/default.conf.template /etc/nginx/templates/default.conf.template
COPY --from=build /app/dist /usr/share/nginx/html
EXPOSE 8080
USER 101
HEALTHCHECK --interval=10s --timeout=3s --retries=5 CMD wget -q -O /dev/null http://127.0.0.1:8080/ || exit 1
