# kotlin-compiler-server for learn-kotlin

Runs JetBrains' [kotlin-compiler-server](https://github.com/JetBrains/kotlin-compiler-server) on this Mac so the
app can compile, grade (`/api/compiler/test`) and highlight learner code. Phase 0 spike results and the API
notes are in `docs/spike-report.md` (one folder up, in `spike/docs/`).

Current state: container `kotlin-compiler-server`, image `kotlin-compiler-server:2.5.0-Beta2-host`, listening on
`http://127.0.0.1:8080`.

## Contents

| Path | What it is |
|---|---|
| `run.sh` | (Re)creates the container with the required flags. Builds the image first if it is missing, or with `--rebuild`. |
| `Dockerfile` | Runtime image built from the host Gradle output (the clone's `build/`). Differs from upstream; see below. |
| `spike-results/cases/` | Request payloads used in the spike (JSON bodies for `/test`, `/run`, `/highlight`). |
| `spike-results/responses/` | Responses captured from the server for those payloads. |
| `spike-results/results.tsv` | One line per probe: HTTP status, seconds, outcome. |

## Prerequisites

1. **Docker Desktop** running. The Docker VM on this machine has about 1.9 GiB of RAM (`docker info`).
2. **The clone** at `$KCS_CLONE` (upstream commit
   `325cdb872475891da66fc4c9b05763de8f011e16`, 2026-10-21). Pin this commit; upstream has no release tags.
3. **Host build output.** The upstream `/Dockerfile` runs the Gradle build inside Docker, and that build died
   (Gradle daemon disappeared, consistent with the 1.9 GiB VM limit against the daemon's `-Xmx6048M`). The
   working path is to build on the host:

   ```sh
   # JDK 17 is required by the project's toolchain (buildSrc). It was installed with:
   #   brew install openjdk@17          (17.0.21)
   # JDK 21 runs Gradle itself.
   cd "$KCS_CLONE"
   JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew build -x test \
     -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17
   ```

   This produces `build/libs/kotlin-compiler-server-2.5.0-Beta2-SNAPSHOT.jar` and the library folders
   `2.5.0-Beta2*` in the clone root. The build takes about 3 minutes on this Mac.

## Run

```sh
./run.sh             # recreate the container; builds the image if missing
./run.sh --rebuild   # rebuild the image from the clone's build output, then recreate the container
```

`run.sh` starts the container with:

```
docker run -d --name kotlin-compiler-server --restart unless-stopped \
  --memory=2g --memory-swap=2g -p 127.0.0.1:8080:8080 kotlin-compiler-server:2.5.0-Beta2-host
```

- `--memory=2g` is the requested cap. `--memory-swap=2g` is added so the container cannot swap beyond it.
- The port is bound to `127.0.0.1` only. The Android emulator reaches the host at `10.0.2.2`, which maps to the
  host's loopback, so the app still works. The server has no authentication, so it must not listen on a LAN
  address.
- `KCS_PORT=8090 ./run.sh` uses a different host port.

Check it:

```sh
curl -s http://127.0.0.1:8080/health          # OK
curl -s http://127.0.0.1:8080/versions        # [{"version":"2.5.0-Beta2","stdlibVersion":"2.5.0-Beta2"}]
docker logs --tail 50 kotlin-compiler-server
docker stats --no-stream kotlin-compiler-server
```

Stop it with `docker rm -f kotlin-compiler-server`. Restart it (which also kills any stuck child JVM) with
`./run.sh`.

## Differences from the upstream Dockerfile

`Dockerfile` here is the upstream runtime stage, fed from the host build, with two additions:

1. **Spring Boot loader classes.** The upstream stage copies `META-INF/` but not the boot jar's root `org/` tree.
   `META-INF/services/java.nio.file.spi.FileSystemProvider` names `NestedFileSystemProvider`, which lives in that
   tree. Without it the server crashes at start with `ServiceConfigurationError` while loading Kotlin libraries
   (seen in this spike). The upstream image has the same omission, by inspection. I did not build upstream to
   confirm it.
2. **The `2.5.0-Beta2-compiler-plugins` folder.** The upstream stage omits it, but `application.properties` points
   at it, and it contains the kotlinx.serialization compiler plugin. Included here; `@Serializable` compiles with it.

## Security notes (read before exposing anything)

Verified in the spike (details in `docs/spike-report.md`):

- **No authentication**, and CORS allows `*`. Anyone who can reach the port can run code. Keep the loopback bind.
- **Learner code runs as root** inside the container. It can read `/etc/passwd`, `/root`, and the server's own
  application directory. Environment variables are cleared, so no secrets leak that way.
- **Outbound internet is reachable** from learner code (`example.com` returned 200). Egress is not restricted.
- **File names are not sanitized.** An absolute name such as `/tmp/x.kt` in a request is written outside the input
  directory. The app must send fixed file names only.
- **A shutdown hook that loops forever** survives the 10 s SIGTERM. The request then never returns and the server
  lock stays held until the child JVM is killed (`./run.sh` does it). Normal infinite loops are cut off at about
  10 s.
- **Results can be forged.** The server parses the first JSON document on the child's stdout, and learner code
  runs in the same JVM as the test runner, so learner code can print a passing result. Treat PASS as the learner's
  own claim. This is acceptable for one person's practice and not for any shared or scored use.

These are why code exercises stay local-only (loopback, one user) until isolation is fixed; see the plan's section 6.

## Upgrading the Kotlin version

The image's Kotlin version is whatever the clone builds. To change it:

1. `git -C "$KCS_CLONE" checkout <commit>`.
2. Rebuild the host output (see Prerequisites), then `./run.sh --rebuild`.
3. Update `KOTLIN_VERSION` and the `-host` image tag in `run.sh` and the `ARG` default in `Dockerfile`, and
   re-check `GET /versions`.

The clone's HEAD targets Kotlin `2.5.0-Beta2`, which is a beta compiler. The app plan targets Kotlin 2.2, so
expect small diagnostic and API differences until the server's version is aligned with the app's.

## Troubleshooting

- **Port 8080 already in use.** The container `dreamairi-cloud-server-api-1` maps host port 8080 (it is currently
  exited). Stop it, or run with `KCS_PORT`.
- **Startup crash mentioning `ServiceConfigurationError` or `NestedFileSystemProvider`.** The image was built from
  an older Dockerfile. Run `./run.sh --rebuild`.
- **Requests hang and CPU stays near 200%.** A learner child JVM ignored SIGTERM. `./run.sh` restarts the container.
- **Docker build of the upstream Dockerfile dies with "Gradle build daemon disappeared".** Expected on this VM; use
  the host build above.
