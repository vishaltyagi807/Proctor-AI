FROM eclipse-temurin:21-jre
RUN useradd --system --uid 1001 --no-create-home app && mkdir -p /app /data && chown app /data
WORKDIR /app
COPY build/libs/*.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
