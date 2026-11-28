FROM envoyproxy/envoy:distroless-v1.40.2@sha256:09ca6f2140a61ad8df11779a4a30d52b48433d444466328494f9188c9c35eded AS envoy

FROM envoyproxy/gateway:v1.10.0@sha256:c2243d43518bed91d3ed8c2db849a4b7a14ac9b96eb019f2ab104bb1ca8572b1
COPY --from=envoy /usr/local/bin/envoy /opt/envoy-gateway/envoy-versions/1.40.2/bin/envoy
COPY envoy-gateway.yaml /etc/envoy-gateway/envoy-gateway.yaml
COPY config /etc/envoy-gateway/config
