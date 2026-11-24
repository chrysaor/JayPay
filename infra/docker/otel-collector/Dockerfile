FROM busybox:1.38.0-musl@sha256:80e38f9a1b39188b8bdb28879178edb322a4402b081a9aedda1924609decd6f6 AS busybox

FROM otel/opentelemetry-collector-contrib:0.165.0@sha256:e40d61f10c9a617cca1ffd1be4d7e2b3a4b0f0001798ff07e58cabe82e618cea
COPY --from=busybox /bin/busybox /busybox
COPY config.yaml /etc/otelcol-contrib/config.yaml
USER 10001:10001
HEALTHCHECK --interval=5s --timeout=3s --start-period=20s --retries=12 CMD ["/busybox", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:13133/"]
