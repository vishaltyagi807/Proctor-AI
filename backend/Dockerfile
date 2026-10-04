ARG JAVA_MODULES=java.base,java.compiler,java.desktop,java.instrument,java.logging,java.management,java.naming,java.net.http,java.prefs,java.rmi,java.scripting,java.security.jgss,java.security.sasl,java.sql,java.sql.rowset,java.transaction.xa,java.xml,jdk.attach,jdk.charsets,jdk.crypto.ec,jdk.jdi,jdk.jfr,jdk.management,jdk.naming.dns,jdk.net,jdk.unsupported,jdk.zipfs
ARG JLINK_FLAGS="--strip-debug --no-header-files --no-man-pages --compress=zip-6 --generate-cds-archive"

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
ARG GRADLE_FLAGS="--build-cache"
RUN --mount=type=bind,target=/src,rw \
    --mount=type=cache,target=/root/.gradle,id=proctor-gradle \
    sh ./gradlew bootJar --no-daemon --console=plain ${GRADLE_FLAGS} \
    && mkdir -p /out \
    && for dir in */; do name="${dir%/}"; if [ -f "$name/build/libs/$name.jar" ]; then cp "$name/build/libs/$name.jar" /out/; fi; done

FROM eclipse-temurin:21-jdk-alpine AS jre-musl
ARG JAVA_MODULES
ARG JLINK_FLAGS
RUN jlink --add-modules "${JAVA_MODULES}" ${JLINK_FLAGS} --output /jre

FROM eclipse-temurin:21-jdk AS jre-glibc
ARG JAVA_MODULES
ARG JLINK_FLAGS
RUN jlink --add-modules "${JAVA_MODULES}" ${JLINK_FLAGS} --output /jre

FROM eclipse-temurin:21-jdk-alpine AS extract
ARG SERVICE
WORKDIR /extract
RUN apk add --no-cache zip
COPY --from=build /out/${SERVICE}.jar service.jar
RUN java -Djarmode=tools -jar service.jar extract --destination app \
    && rm service.jar \
    && for jar in app/lib/onnxruntime-*.jar; do \
         [ -f "$jar" ] || continue; \
         case "$(uname -m)" in \
           x86_64) keep=linux-x64 ;; \
           aarch64) keep=linux-aarch64 ;; \
           *) echo "Unsupported architecture $(uname -m)" >&2; exit 1 ;; \
         esac; \
         unzip -Z1 "$jar" 'ai/onnxruntime/native/*' | grep -v "^ai/onnxruntime/native/$keep/" | grep -v '^ai/onnxruntime/native/$' > /tmp/drop || true; \
         [ ! -s /tmp/drop ] || zip -q -d "$jar" -@ < /tmp/drop; \
         unzip -Z1 "$jar" | grep -q "^ai/onnxruntime/native/$keep/libonnxruntime.so$"; \
       done

FROM busybox:1.37-musl AS busybox

FROM alpine:3.22 AS service
ENV JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:$PATH \
    LANG=C.UTF-8
RUN addgroup -S -g 1001 app \
    && adduser -S -D -H -u 1001 -G app -s /sbin/nologin app \
    && mkdir -p /app /data \
    && chown app:app /data
COPY --from=jre-musl /jre /opt/java
WORKDIR /app
COPY --from=extract /extract/app/ /app/
USER app
ENTRYPOINT ["java", "-jar", "/app/service.jar"]

FROM alpine:3.22 AS face-dirs
RUN mkdir -p /data && chown 1001:1001 /data

FROM gcr.io/distroless/cc-debian12:nonroot AS face-service
ENV JAVA_HOME=/opt/java \
    PATH=/opt/java/bin:/usr/local/bin:/usr/bin:/bin \
    LANG=C.UTF-8
COPY --from=busybox /bin/busybox /usr/local/bin/wget
COPY --from=jre-glibc /jre /opt/java
COPY --from=face-dirs --chown=1001:1001 /data /data
WORKDIR /app
COPY --from=extract /extract/app/ /app/
COPY face-service/models /app/models
USER 1001:1001
ENTRYPOINT ["java", "-jar", "/app/service.jar"]
