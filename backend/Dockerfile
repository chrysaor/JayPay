FROM gcr.io/distroless/base-nossl-debian13@sha256:f21fdfe4ede467d9ea8a6caaec1f8381ea4502243a3e8bb1c43df80c6fbaaeae AS base

FROM debian:trixie-slim@sha256:8aa5a7bf5a1955bddbe7d74fcdd0b91ab30e74362a8b73af672b0481b35b44a0 AS locale

FROM busybox:1.38.0-musl@sha256:80e38f9a1b39188b8bdb28879178edb322a4402b081a9aedda1924609decd6f6 AS busybox

FROM eclipse-temurin:25.0.5.1_2-jdk-noble@sha256:c7eef3dfeb81468533ea48c67bddaa8c7ca916454259e4d0d734d12bcff8e125 AS jdk

FROM jdk AS runtime
COPY --from=base /etc/passwd /etc/group /runtime/etc/
COPY --from=locale /usr/lib/locale/C.utf8 /runtime/usr/lib/locale/C.utf8
RUN jlink --output /runtime/opt/java/openjdk \
      --add-modules java.base,java.compiler,java.desktop,java.instrument,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,jdk.jfr,jdk.localedata,jdk.management,jdk.management.jfr,jdk.naming.dns,jdk.net,jdk.security.jgss,jdk.unsupported \
      --include-locales=en,ko --strip-debug --no-man-pages --no-header-files --generate-cds-archive \
    && echo 'jaypay:x:10001:10001::/app:/sbin/nologin' >>/runtime/etc/passwd \
    && echo 'jaypay:x:10001:' >>/runtime/etc/group

FROM jdk AS extract
WORKDIR /workspace
COPY app.jar ./
COPY layers/ layers/
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted \
    && for layer in service jpa; do \
         mkdir -p "shared/$layer/lib"; \
         if [ -f "layers/$layer.txt" ]; then \
           while read -r jar; do \
             if [ -f "extracted/dependencies/lib/$jar" ]; then mv "extracted/dependencies/lib/$jar" "shared/$layer/lib/"; fi; \
           done <"layers/$layer.txt"; \
         fi; \
       done

FROM base
COPY --from=busybox /bin/busybox /busybox
COPY --from=runtime /runtime/ /
ENV JAVA_HOME=/opt/java/openjdk \
    PATH=/opt/java/openjdk/bin:/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin \
    LANG=C.UTF-8 \
    TZ=Etc/UTC
WORKDIR /app
COPY --from=extract /workspace/shared/service/ ./
COPY --from=extract /workspace/shared/jpa/ ./
COPY --from=extract /workspace/extracted/dependencies/ ./
COPY --from=extract /workspace/extracted/spring-boot-loader/ ./
COPY --from=extract /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extract /workspace/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=180s --retries=6 \
    CMD ["/busybox", "wget", "-q", "-O", "/dev/null", "http://127.0.0.1:9080/actuator/health"]
ENTRYPOINT ["java", "-Duser.country=US", "-XX:MaxRAMPercentage=50", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
