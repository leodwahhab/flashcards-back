# syntax=docker/dockerfile:1
# Build multi-stage; as imagens eclipse-temurin são multi-arch (amd64 e arm64, ex.: OCI Ampere A1).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN --mount=type=cache,target=/root/.m2 sh mvnw -q -B dependency:go-offline
COPY src/ src/
# Os testes rodam local/CI; o build da imagem só empacota. O jar fica em target/ com o nome do artefato.
RUN --mount=type=cache,target=/root/.m2 sh mvnw -q -B -DskipTests package \
    && cp target/flashcards-api-*.jar /workspace/app.jar

FROM eclipse-temurin:21-jre
RUN useradd --system --uid 10001 --create-home app \
    && mkdir -p /data/media \
    && chown app /data/media
USER app
WORKDIR /app
COPY --from=build /workspace/app.jar app.jar
ENV APP_MEDIA_DIR=/data/media \
    JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=60"
VOLUME /data/media
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
