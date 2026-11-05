import assert from "node:assert/strict"
import test, { describe, it } from "node:test"
import {
  createCoursePackSource,
  isTrustedSourceUrl,
  parseGitHubUrl,
  resolvePackDownloadUrl
} from "../source.ts"

describe("GitHub and Source Metadata", () => {
  describe("parseGitHubUrl", () => {
    it("parses standard GitHub repository URLs", () => {
      const parsed = parseGitHubUrl("https://github.com/learn-code/kotlin-pack")
      assert.deepEqual(parsed, {
        owner: "learn-code",
        repo: "kotlin-pack",
        kind: "repo"
      })
    })

    it("parses GitHub repository URLs ending in .git", () => {
      const parsed = parseGitHubUrl("https://github.com/learn-code/kotlin-pack.git")
      assert.deepEqual(parsed, {
        owner: "learn-code",
        repo: "kotlin-pack",
        kind: "repo"
      })
    })

    it("parses GitHub release tag URLs", () => {
      const parsed = parseGitHubUrl("https://github.com/learn-code/kotlin-pack/releases/tag/v1.2.0")
      assert.deepEqual(parsed, {
        owner: "learn-code",
        repo: "kotlin-pack",
        ref: "v1.2.0",
        kind: "release"
      })
    })

    it("parses GitHub blob URLs", () => {
      const parsed = parseGitHubUrl(
        "https://github.com/learn-code/kotlin-pack/blob/develop/packages/course.json"
      )
      assert.deepEqual(parsed, {
        owner: "learn-code",
        repo: "kotlin-pack",
        ref: "develop",
        path: "packages/course.json",
        kind: "blob"
      })
    })

    it("parses raw.githubusercontent.com URLs", () => {
      const parsed = parseGitHubUrl(
        "https://raw.githubusercontent.com/learn-code/kotlin-pack/v2.0.0/dist/pack.json"
      )
      assert.deepEqual(parsed, {
        owner: "learn-code",
        repo: "kotlin-pack",
        ref: "v2.0.0",
        path: "dist/pack.json",
        kind: "raw"
      })
    })

    it("returns null for non-GitHub URLs", () => {
      assert.equal(parseGitHubUrl("https://gitlab.com/owner/repo"), null)
      assert.equal(parseGitHubUrl("not-a-url"), null)
    })
  })

  describe("isTrustedSourceUrl", () => {
    it("approves trusted HTTPS GitHub domains", () => {
      assert.equal(isTrustedSourceUrl("https://github.com/learn-code/pack"), true)
      assert.equal(isTrustedSourceUrl("https://raw.githubusercontent.com/learn-code/pack/main/pack.json"), true)
    })

    it("rejects non-HTTPS URLs", () => {
      assert.equal(isTrustedSourceUrl("http://github.com/learn-code/pack"), false)
      assert.equal(isTrustedSourceUrl("ftp://github.com/learn-code/pack"), false)
    })

    it("rejects untrusted domains", () => {
      assert.equal(isTrustedSourceUrl("https://evil.com/fake-pack.json"), false)
      assert.equal(isTrustedSourceUrl("https://github.com.malicious.com/pack"), false)
    })

    it("supports custom trusted hosts whitelist", () => {
      assert.equal(isTrustedSourceUrl("https://content.learn-code.dev/pack.json", ["content.learn-code.dev"]), true)
      assert.equal(isTrustedSourceUrl("https://github.com/pack", ["content.learn-code.dev"]), false)
    })
  })

  describe("resolvePackDownloadUrl", () => {
    it("resolves repository root to raw default manifest URL", () => {
      const resolved = resolvePackDownloadUrl("https://github.com/learn-code/kotlin-pack")
      assert.equal(
        resolved,
        "https://raw.githubusercontent.com/learn-code/kotlin-pack/main/course-pack.json"
      )
    })

    it("resolves release tag URL to raw tag manifest URL", () => {
      const resolved = resolvePackDownloadUrl("https://github.com/learn-code/kotlin-pack/releases/tag/v1.5.0")
      assert.equal(
        resolved,
        "https://raw.githubusercontent.com/learn-code/kotlin-pack/v1.5.0/course-pack.json"
      )
    })

    it("resolves blob URL to raw URL directly", () => {
      const resolved = resolvePackDownloadUrl(
        "https://github.com/learn-code/kotlin-pack/blob/feature-x/custom-manifest.json"
      )
      assert.equal(
        resolved,
        "https://raw.githubusercontent.com/learn-code/kotlin-pack/feature-x/custom-manifest.json"
      )
    })

    it("preserves non-GitHub URLs or raw URLs", () => {
      const direct = "https://example.com/custom-pack.json"
      assert.equal(resolvePackDownloadUrl(direct), direct)
    })
  })

  describe("createCoursePackSource", () => {
    it("creates a canonical source object from a GitHub URL", () => {
      const source = createCoursePackSource("https://github.com/learn-code/kotlin-pack/releases/tag/v1.0.0")
      assert.deepEqual(source, {
        url: "https://github.com/learn-code/kotlin-pack",
        ref: "v1.0.0",
        commit: undefined,
        path: "course-pack.json"
      })
    })
  })
})
