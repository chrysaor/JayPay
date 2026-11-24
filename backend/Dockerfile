FROM eclipse-temurin:25-jre AS extract
WORKDIR /workspace
COPY app.jar app.jar
RUN java -Djarmode=tools -jar app.jar extract --layers --destination extracted

FROM eclipse-temurin:25-jre
RUN groupadd --system --gid 10001 jaypay \
    && useradd --system --uid 10001 --gid jaypay --no-create-home --home-dir /app jaypay
WORKDIR /app
COPY --from=extract /workspace/extracted/dependencies/ ./
COPY --from=extract /workspace/extracted/spring-boot-loader/ ./
COPY --from=extract /workspace/extracted/snapshot-dependencies/ ./
COPY --from=extract /workspace/extracted/application/ ./
USER 10001:10001
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
