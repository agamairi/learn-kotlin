# Phase 0 runner spike: kotlin-compiler-server

Date: 2026-10-24. Server: JetBrains kotlin-compiler-server at commit `325cdb872475891da66fc4c9b05763de8f011e16`
(2026-10-21), built as Kotlin `2.5.0-Beta2`. Run on this Mac in Docker, capped at 2 GiB, bound to `127.0.0.1:8080`.
Raw payloads and responses: `/Users/agamairi/development/learn-kotlin-wt/spike/tools/compiler-server/spike-results/`
(`cases/`, `responses/`, `results.tsv`). Container setup: `/Users/agamairi/development/learn-kotlin-wt/spike/tools/compiler-server/README.md`.

## Verdict

**go-with-limits for v1 single-user use, on loopback only.** The server compiles, grades and highlights correctly,
and it bounds a normal infinite loop (10 s), an out-of-memory bomb (0.75 s) and `System.exit` (the learner gets an
error, not a pass). It does not meet the plan's bar for code exercises beyond the laptop. Three findings decide that:
(1) a learner shutdown hook that never returns survives the timeout, so the runner stops answering until the child
process is killed; (2) learner code can forge the JUnit result by printing JSON before the runner prints its own,
and the server accepts it, so a PASS is only the learner's own claim; (3) learner code runs as root with outbound
internet and readable server files, and file names from the request are used unsanitized (a request can write
outside its temp directory). Per the plan (section 6), code exercises therefore stay on a localhost runner, used by
one person, until isolation is fixed. The server has no authentication, so it must never listen on a LAN address.

## Results for the 9 checklist items

Latency is client-side wall time from curl. "Healthy after" means `GET /health` returned `OK` and the next compile
succeeded.

| # | Item | Result | HTTP | Time | What the server returned | Healthy after |
|---|---|---|---|---|---|---|
| 1 | Correct solution + tests | **PASS** | 200 | cold 1.69 s; warm 0.35 to 0.49 s (median 0.39 s, 5 runs) | `testResults`: both tests `status: "OK"` | yes |
| 2 | Compile error | **PASS** | 200 | 0.18 s | `errors["Solution.kt"]`: `severity: "ERROR"`, 0-based `interval` line 0, ch 33 | yes |
| 3 | Infinite loop (normal) | **PASS** | 200 | 10.19 s | `text`: `<errStream>Evaluation stopped while it's taking too long...</errStream>`, no `testResults`; no child JVM left | yes |
| 3b | Infinite loop in a shutdown hook (added) | **FAIL** | no response in 60 s | >60 s | Child ignores SIGTERM and spins on both CPUs; the server's lock is held, so every compile queues. Recovered only after `kill -9` of the child (then 0.30 s) | after manual kill |
| 4 | Memory bomb | **PASS** | 200 | 0.75 s | `status: "ERROR"`, `java.lang.OutOfMemoryError: Java heap space` (child heap is 32 MB) | yes; server RSS 624 to 752 MiB, 0 restarts |
| 5 | `System.exit(0)` in learner code | **PASS** (cannot fake a pass) | 200 | 0.52 s | top-level `exception`: `MismatchedInputException` "No content to map due to end-of-input" | yes |
| 6 | Learner prints fake PASS lines | **PARTIAL** | 200 | 0.39 s | Printed lines are ignored (both tests `FAIL`, `expected:<5> but was:<0>`). But JSON printed during class initialisation is accepted as the result: both failing tests reported `OK` (6b, 0.41 s). Printing JSON then `Runtime.halt(0)` also forges a pass (6c) | yes |
| 7 | Learner reads files / test file | **FAIL** (not bounded) | 200 | 1.12 s | Runs as root. Reads `/etc/passwd`, `/root`, `/kotlin-compiler-server` (jars, `application.properties`, `logs/`). Test source `.kt` is not on disk; compiled test `.class` is on the classpath and readable. Env vars cleared (0). `https://example.com` returns 200. Learner code can call the server itself at `host.docker.internal:8080` (200). Metadata IP `169.254.169.254` refused on this Docker Desktop | yes |
| 7b | File-name path traversal (added) | **FAIL** | 200 | 0.64 s | Absolute name `/tmp/traversal-probe-marker.txt` was written outside the input directory (marker removed afterwards) | yes |
| 8 | Reported Kotlin version | **PASS** (note: beta) | 200 | not timed | `GET /versions`: `{"version":"2.5.0-Beta2","stdlibVersion":"2.5.0-Beta2"}` | yes |
| 9 | `/highlight` on broken code | **PASS** | 200 | 0.15 s | Four diagnostics: 2 `WARNING` "Variable is unused.", 2 `ERROR` (type mismatch, unresolved reference), with 0-based line and ch | yes |

Also measured: container start to `/health` OK in 2.3 s (2.8 s via `run.sh`). A quick request queued behind the
infinite loop (item 3) took 9.56 s, so the server handles one request at a time. The `/run` output flood ended as
an `OutOfMemoryError` in the child (0.40 s), not the 100 KB output limit.

## Setup, and what I changed to get here

- **Docker build failed.** `docker-image-build.sh` (upstream `Dockerfile`) died at `./gradlew build -x test` with
  "Gradle build daemon disappeared". The Docker VM has 1.91 GiB of RAM, and the project's `gradle.properties` asks
  for a 6 GB daemon heap. That is consistent with an OOM kill, though I did not confirm it.
- **Host build used.** As instructed, I built on the host. The project's toolchain requires JDK 17 (`buildSrc`),
  and only JDK 21 was installed. The automatic toolchain download failed, so I installed `openjdk@17` (17.0.21)
  with Homebrew. This changed the Mac; remove it with `brew uninstall openjdk@17` if unwanted. The build is:
  `JAVA_HOME=/opt/homebrew/opt/openjdk@21 ./gradlew build -x test -Dorg.gradle.java.installations.paths=/opt/homebrew/opt/openjdk@17`
  (2m50s). I did not change the clone's source.
- **Runtime image.** `spike/tools/compiler-server/Dockerfile` is the upstream runtime stage fed from the host build,
  with two fixes the upstream file lacks: the Spring Boot loader classes at the jar root `org/` (without them the
  server crashes at start with `ServiceConfigurationError`; by inspection the upstream image would too, not built to
  confirm), and the `2.5.0-Beta2-compiler-plugins` folder (contains the kotlinx.serialization plugin, which
  `@Serializable` needs; verified to work with it).
- **Container flags.** As requested (`--name kotlin-compiler-server --restart unless-stopped --memory=2g`, host
  port 8080), plus two additions: `--memory-swap=2g` (so it cannot swap past the cap) and `-p 127.0.0.1:8080:8080`
  (loopback only; the emulator reaches it at `10.0.2.2`).
- **Port conflict.** A stopped container, `dreamairi-cloud-server-api-1`, maps host port 8080. It is not running now.
- The container is running and healthy at the end of the spike. I did not commit or push anything.

## API

All endpoints are unauthenticated JSON over HTTP. Upstream sources: `src/main/kotlin/com/compiler/server/controllers/CompilerRestController.kt`,
`api/RunRequest.kt`, `api/TestRequest.kt`, `common/.../Project.kt`, `model/ExecutionResult.kt`, `model/TestDescription.kt`.

### Shared shapes

- **File:** `{"name": "Solution.kt", "text": "<source>"}`. Name must be a plain file name (see item 7b).
- **Diagnostic (`ErrorDescriptor`):** `{"interval": {"start": {"line", "ch"}, "end": {"line", "ch"}}, "message", "severity": "ERROR"|"WARNING"|"INFO", "className"}`. Lines and columns are **0-based**.
- **`errors`:** object keyed by file name, each an array of diagnostics. Warnings are included; filter them out for compile-error handling.
- **`exception`:** `null` or `{"message", "fullName", "stackTrace": [...], "cause", "localizedMessage"}` (a crash in the runner or the compiler, not a learner test failure).
- **`text`:** program stdout wrapped as `<outStream>...</outStream>`; stderr as `<errStream>...</errStream>`. Timeout and output-limit messages are wrapped in `<errStream>`. Strip the tags before display.
- **`testResults`** (test endpoint only, omitted when empty): object keyed by class name, each an array of
  `{"output", "className", "methodName", "executionTime" (ms), "exception", "comparisonFailure", "status": "OK"|"FAIL"|"ERROR"}`.
  `comparisonFailure` is `{"message", "fullName", "stackTrace", "cause", "expected", "actual"}`. For `assertEquals` on
  non-strings, `expected` and `actual` are empty; use `message` (e.g. `expected:<5> but was:<0>`).

### `POST /api/compiler/run`

Request (`RunRequest`): `{"args": "", "files": [...], "compilerArguments": {}}`. Optional query `addByteCode=false`.
Runs the file that contains `fun main`. Response: `{"errors", "exception", "jvmByteCode", "text"}`.

Example request (`spike-results/cases/run-hello.json`, pretty-printed):

```json
{
  "args": "",
  "files": [
    {
      "name": "Main.kt",
      "text": "fun main() {\n    println(\"Hello from /run\")\n}\n"
    }
  ],
  "compilerArguments": {}
}
```

Example response (`spike-results/responses/run-hello.json`, HTTP 200, 0.37 s):

```json
{"errors":{"Main.kt":[]},"exception":null,"jvmByteCode":null,"text":"<outStream>Hello from /run\n</outStream>"}
```

### `POST /api/compiler/test`

Request (`TestRequest`): same shape as `/run`. Put the learner file and the test file in `files`; they are compiled
together. Response: `{"testResults", "errors", "exception", "text"}`.

Example request (`spike-results/cases/01-correct.json`, pretty-printed):

```json
{
  "args": "",
  "files": [
    {
      "name": "Solution.kt",
      "text": "fun sum(a: Int, b: Int): Int = a + b\n"
    },
    {
      "name": "SumTest.kt",
      "text": "import kotlin.test.*\n\nclass SumTest {\n    @Test fun addsTwoNumbers() {\n        assertEquals(5, sum(2, 3))\n    }\n\n    @Test fun addsNegatives() {\n        assertEquals(-1, sum(2, -3))\n    }\n}\n"
    }
  ],
  "compilerArguments": {}
}
```

Example response (`spike-results/responses/01-correct-warm1.json`, HTTP 200, 0.49 s):

```json
{"testResults":{"SumTest":[{"output":"","className":"SumTest","methodName":"addsTwoNumbers","executionTime":4,"exception":null,"comparisonFailure":null,"status":"OK"},{"output":"","className":"SumTest","methodName":"addsNegatives","executionTime":0,"exception":null,"comparisonFailure":null,"status":"OK"}]},"errors":{"Solution.kt":[],"SumTest.kt":[]}}
```

Other `/test` responses, for reference: a compile error gives no `testResults` and an `ERROR` in `errors`
(`02-compile-error.json`); a timeout gives `text` with the timeout message and no `testResults` (`03-infinite-loop.json`);
a memory bomb gives `status: "ERROR"` with `exception.fullName: "java.lang.OutOfMemoryError"` (`04-memory-bomb.json`).

### `POST /api/compiler/highlight`

Request (`Project`): `{"args": "", "files": [...]}`. Do not send `compilerArguments` as an object: `Project`
expects a list there, and omitting it works. `confType` defaults to `java`. Response: a map from file name to diagnostics
(no outer `errors` wrapper).

Example request (`spike-results/cases/09-highlight-broken.json`, pretty-printed):

```json
{
  "args": "",
  "files": [
    {
      "name": "Solution.kt",
      "text": "fun sum(a: Int, b: Int): Int {\n    val unused = 3\n    val s: Int = \"text\"\n    return a + undefinedName\n}\n"
    }
  ]
}
```

Example response (`spike-results/responses/09-highlight-broken.json`, HTTP 200, 0.15 s, compacted):

```json
{"Solution.kt":[{"interval":{"start":{"line":1,"ch":8},"end":{"line":1,"ch":14}},"message":"Variable is unused.","severity":"WARNING","className":"WARNING"},{"interval":{"start":{"line":2,"ch":8},"end":{"line":2,"ch":9}},"message":"Variable is unused.","severity":"WARNING","className":"WARNING"},{"interval":{"start":{"line":2,"ch":15},"end":{"line":2,"ch":16}},"message":"Initializer type mismatch: expected 'Int', actual 'String'.","severity":"ERROR","className":"Solution"},{"interval":{"start":{"line":3,"ch":15},"end":{"line":3,"ch":28}},"message":"Unresolved reference 'undefinedName'.","severity":"ERROR","className":"Solution"}]}
```

### `GET /health` and `GET /versions`

`GET /health` returns `OK` (text). `GET /versions` returns `[{"version":"2.5.0-Beta2","stdlibVersion":"2.5.0-Beta2"}]`.

## Test framework rules for content authors

Verified by running the cases above:

1. **JUnit 4 only.** `import kotlin.test.*` works (on the JVM, `kotlin.test.Test` is a typealias for the JUnit 4 annotation), and so
   does `import org.junit.Test` with `org.junit.Assert`. JUnit 5 (`org.junit.jupiter.api.Test`) is not on the classpath
   and gives a compile error (`Unresolved reference 'jupiter'`, item 11a), not a silent skip.
2. **Test classes must be top-level in the default package** (no `package` line). The runner only loads top-level
   `.class` files that have a JUnit 4 `@Test` method. A test class with a `package` line is silently skipped, and the
   response has no `testResults` and no error (item 11b). Non-test helper code can be packaged; the server's own
   koans tests import a packaged util, though I did not run that path.
3. **Silent empty suite.** A request with no runnable test class also returns no `testResults` and no error (11c).
   The app must treat this as an error, never as a pass.
4. **Both learner and test files go in `files`.** They are compiled together into one output directory. Every
   top-level class with a JUnit 4 `@Test` runs, so a learner-supplied test class also runs. The grader must check
   that the expected test names all appear with `OK`.
5. **File names are plain names** such as `Solution.kt` and `SumTest.kt`. Paths are not sanitized (item 7b).
6. **`kotlin.test` assertions** produce `FAIL` with `message` (e.g. `expected:<5> but was:<0>`). An exception that is
   not an assertion produces `ERROR` (e.g. OOM).
7. **Libraries verified on the classpath:** kotlin-stdlib, kotlin.test with kotlin-test-junit, JUnit 4.13.2,
   kotlinx.serialization (compiler plugin and JSON, item 13). Other libraries listed in the server's dependency file
   were not checked individually.
8. **Output** printed inside a test is captured in that test's `output` field (wrapped in `<outStream>` tags).

## Measured latencies

| Measurement | Seconds |
|---|---|
| Container start to `/health` OK | 2.3 (2.8 via `run.sh`) |
| First compile after start (cold), correct solution + 2 tests | 1.69 |
| Warm runs (5), same request | 0.491, 0.374, 0.416, 0.391, 0.347 (median 0.39, mean 0.40) |
| `/highlight`, broken file | 0.15 |
| Compile error (`/test`) | 0.18 |
| `/run`, hello world | 0.37 |
| Infinite loop, normal (`/test`) | 10.19 (server timeout is 10 s) |
| Memory bomb (`/test`) | 0.75 |
| `System.exit(0)` | 0.52 |
| Quick request queued behind an infinite loop | 9.56 |

Container memory: about 624 MiB RSS at idle, 752 to 775 MiB under the tests above (cap 2 GiB). The JVM heap defaults
to 25% of the cap (about 490 MiB). The child JVMs run with a 32 MB heap.

## Recommendations for `KotlinCompilerServerRunner`

Map the response to `RunOutcome` in this order. Fail closed: when unsure, return `Unavailable`.

1. **Transport failures** (connection refused, DNS, client timeout) and non-2xx status: `Unavailable(reason)`.
   Use a client timeout of **30 s**. A request can wait behind a previous one (up to about 10 s) and then run (up to
   about 10.5 s), so 20 s is too tight.
2. **Timeout:** `text` contains `Evaluation stopped while it's taking too long` (match the text, not the tags):
   `Timeout`. Show a "your code ran too long" message.
3. **Output limit:** `text` contains `log size exceeded`: `Timeout` with an output reason, or add a `reason` field.
4. **Compile error:** a file's `errors` entry has `severity: "ERROR"` and there is no `testResults`:
   `CompileError(diagnostics)`. Use the learner file's entries only. Convert 0-based line and ch to 1-based for display.
   If the error is in the test file, return `Unavailable` (an authoring bug) and log it.
5. **Top-level `exception` present:** `Unavailable(reason)`. If `fullName` is `MismatchedInputException` with
   "No content to map", say "the program ended the process early" (`System.exit`). A `Crashed` outcome would be
   cleaner if the UI needs it.
6. **`testResults` present:** `Passed` only if every expected test (from the content's metadata) appears and is `OK`.
   Otherwise `TestsFailed` with the per-test `message`. An `ERROR` status counts as a failure.
7. **No `testResults` and no errors, or an empty one:** `Unavailable("no tests ran")`. Never `Passed` (items 11b and 11c).
8. **Do not parse `text` for PASS or FAIL.** Only `testResults` is structured. Results are forgeable (item 6b, 6c),
   so treat PASS as the learner's own claim. Use `ignoreUnknownKeys = true`; the server returns fields the app
   does not model (`jvmByteCode`, `TestDescription` fields).

Client-side rules for v1:

- Serialize requests in the app (disable Run while one is in flight). The server runs one compile at a time.
- Check `GET /health` (2 s timeout) before Run, so an unreachable server shows `Unavailable` quickly.
- Cap source size in the app (the server accepted a 5 MB body; there is no limit). 64 KB is plenty for exercises.
- Use fixed file names generated by the app (`Solution.kt`, `SolutionTest.kt`), never names from content.
- Strip `<outStream>` and `<errStream>` tags before showing `text` or `output`.
- Expect the first compile after a container start to take about 1.7 s; optionally warm up from Settings "Test connection".
- Keep the runner behind the `CodeRunner` interface, as the plan says, so a fixed server or a different runner can
  replace it.

## Open issues and what would unblock Phase 2

Requirements before the runner is reachable from anywhere but the laptop, or before code exercises go beyond one user:

1. **Shutdown-hook hang (item 3b).** Upstream `JavaExecutor` calls `destroy()` (SIGTERM) and then closes a reader
   that is blocked in `readLine`. Likely fix, not tested: `destroyForcibly()` after a short wait, and close the reader
   only after the child exits. The server lock (`@Synchronized` in `KotlinEnvironment.synchronize`) also has no
   timeout, so one stuck child blocks everything.
2. **Result forgery (items 6b, 6c).** A fix needs a result channel learner code cannot write, which requires running
   the runner outside the learner's JVM. Not solvable inside this design. Acceptable only for self-practice.
3. **Containment (item 7).** Run as a non-root user, read-only root filesystem with a tmpfs `/tmp`, no outbound
   network (iptables rules in the `DOCKER-USER` chain for the container), and no route to the server's own API from
   learner code.
4. **Path sanitization (item 7b).** Reject file names containing `/`, `\`, or `..` in `writeToIoFiles`
   (`src/main/kotlin/com/compiler/server/compiler/components/CliUtils.kt`).
5. **Request size limit.** Cap the body at the server (the spike accepted 5 MB).
6. **Kotlin version.** The clone builds `2.5.0-Beta2`, a beta, and the app plan targets Kotlin 2.2. Pin the server to a
   commit (`325cdb8`) and decide whether the app or the content compiler should match its diagnostics.
7. **Upstream Dockerfile.** The `org/` loader and `-compiler-plugins` omissions (see Setup) should be reported
   upstream or patched in the local build.

## Not tested

- Native (off-heap) memory bombs: `-Xmx32M` does not cap them, and they could reach the 2 GiB container limit. Not run,
  to avoid killing the container.
- Thread and fork bombs.
- Production-like cloud behaviour (Cloud Run, its metadata server, and cold starts). The plan lists cold starts for
  Cloud Run as unmeasured; this spike measured only local Docker Desktop.
- Concurrency beyond two requests.
- Client code: no Kotlin code was written in the app. The `RunOutcome` mapping above is a recommendation, not an
  implementation.

## Files

- Report: `/Users/agamairi/development/learn-kotlin-wt/spike/docs/spike-report.md`
- Container script: `/Users/agamairi/development/learn-kotlin-wt/spike/tools/compiler-server/run.sh`
- Container README: `/Users/agamairi/development/learn-kotlin-wt/spike/tools/compiler-server/README.md`
- Runtime Dockerfile: `/Users/agamairi/development/learn-kotlin-wt/spike/tools/compiler-server/Dockerfile`
- Evidence: `/Users/agamairi/development/learn-kotlin-wt/spike/tools/compiler-server/spike-results/` (`cases/`, `responses/`, `results.tsv`)
- Clone (not modified, except build output and generated, gitignored properties): `/Users/agamairi/development/tools/kotlin-compiler-server`
