# Runtime image for kotlin-compiler-server, built from the artifacts of a HOST Gradle build.
#
# Why this exists: the upstream /Dockerfile runs ./gradlew inside Docker, and the Docker VM on this
# Mac has ~1.9 GiB RAM, so the in-container Gradle daemon (-Xmx6048M) is killed. See README.md.
#
# Build (the clone is the build context; this Dockerfile lives outside it):
#   docker build -f /path/to/learn-kotlin-wt/tools/compiler-server/Dockerfile \
#     -t kotlin-compiler-server:2.5.0-Beta2-host \
#     /path/to/kotlin-compiler-server
#
# Prerequisite: in the clone, `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew build -x test` with a
# JDK 17 toolchain available (see README.md).

FROM amazoncorretto:17-al2023 AS unpack
ARG KOTLIN_VERSION=2.5.0-Beta2
COPY build/libs/kotlin-compiler-server-${KOTLIN_VERSION}-SNAPSHOT.jar /tmp/app.jar
RUN mkdir -p /build/libs && (cd /build/libs && jar -xf /tmp/app.jar)

FROM amazoncorretto:17-al2023
ARG KOTLIN_VERSION=2.5.0-Beta2
RUN mkdir /kotlin-compiler-server
WORKDIR /kotlin-compiler-server

COPY --from=unpack /build/libs/BOOT-INF/lib /kotlin-compiler-server/lib
COPY --from=unpack /build/libs/META-INF /kotlin-compiler-server/META-INF
# Spring Boot loader classes live at the boot jar root. META-INF/services/java.nio.file.spi.FileSystemProvider
# names NestedFileSystemProvider from here; without this the KLIB loader fails at startup (ServiceConfigurationError).
COPY --from=unpack /build/libs/org /kotlin-compiler-server/org
COPY --from=unpack /build/libs/BOOT-INF/classes /kotlin-compiler-server

# Library folders referenced by application.properties (libraries.folder.*).
# The upstream Dockerfile omits the -compiler-plugins folder (kotlinx.serialization plugin); it is included here.
COPY ${KOTLIN_VERSION} /kotlin-compiler-server/${KOTLIN_VERSION}
COPY ${KOTLIN_VERSION}-js /kotlin-compiler-server/${KOTLIN_VERSION}-js
COPY ${KOTLIN_VERSION}-wasm /kotlin-compiler-server/${KOTLIN_VERSION}-wasm
COPY ${KOTLIN_VERSION}-compose-wasm /kotlin-compiler-server/${KOTLIN_VERSION}-compose-wasm
COPY ${KOTLIN_VERSION}-compose-wasm-compiler-plugins /kotlin-compiler-server/${KOTLIN_VERSION}-compose-wasm-compiler-plugins
COPY ${KOTLIN_VERSION}-compiler-plugins /kotlin-compiler-server/${KOTLIN_VERSION}-compiler-plugins

ENV PORT=8080

CMD ["java", "-noverify", \
    "-Dserver.port=${PORT}", \
    "-cp", "/kotlin-compiler-server:/kotlin-compiler-server/lib/*", \
    "com.compiler.server.CompilerApplicationKt"]
