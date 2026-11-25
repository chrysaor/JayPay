FROM eclipse-temurin:25.0.5.1_2-jre-noble@sha256:2ec98aa522c472deadfd2b7c5b39bca680e33ef522a53a72f10789bdf89a6bb5 AS extract
WORKDIR /workspace
COPY app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25.0.5.1_2-jre-noble@sha256:2ec98aa522c472deadfd2b7c5b39bca680e33ef522a53a72f10789bdf89a6bb5
RUN groupadd --system --gid 10001 jaypay \
    && useradd --system --uid 10001 --gid jaypay --no-create-home --home-dir /app jaypay
WORKDIR /app
COPY --from=extract /workspace/extracted/dependencies/ ./
COPY --from=extract /workspace/extracted/spring-boot-loader/ ./
COPY --from=extract /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extract /workspace/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
HEALTHCHECK --interval=10s --timeout=5s --start-period=180s --retries=6 \
    CMD ["bash", "-c", "exec 3<>/dev/tcp/127.0.0.1/8080 && printf 'GET /actuator/health HTTP/1.0\\r\\nHost: localhost\\r\\n\\r\\n' >&3 && head -n 1 <&3 | grep -q ' 200 '"]
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=50", "-XX:+ExitOnOutOfMemoryError", "-jar", "app.jar"]
