FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
ARG GRADLE_FLAGS="--build-cache"
RUN --mount=type=bind,target=/src,rw \
    --mount=type=cache,target=/root/.gradle,id=proctor-gradle \
    sh ./gradlew bootJar --no-daemon --console=plain ${GRADLE_FLAGS} \
    && mkdir -p /out \
    && for dir in */; do name="${dir%/}"; if [ -f "$name/build/libs/$name.jar" ]; then cp "$name/build/libs/$name.jar" /out/; fi; done

FROM eclipse-temurin:21-jre AS extract
ARG SERVICE
WORKDIR /extract
COPY --from=build /out/${SERVICE}.jar service.jar
RUN java -Djarmode=tools -jar service.jar extract --destination app

FROM eclipse-temurin:21-jre AS service
RUN groupadd --system --gid 1001 app \
    && useradd --system --uid 1001 --gid app --no-create-home --shell /usr/sbin/nologin app \
    && mkdir -p /app /data \
    && chown app:app /data
WORKDIR /app
COPY --from=extract --chown=app:app /extract/app/ /app/
USER app
ENTRYPOINT ["java", "-jar", "/app/service.jar"]

FROM service AS face-service
COPY --chown=app:app face-service/models /app/models
