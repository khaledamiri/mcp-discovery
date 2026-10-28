# 0010 — Distribute on Maven Central under Apache 2.0

- Status: Accepted
- Date: 2026-10-06

## Context

Any Java project should be able to use the library with no special setup, while the author's copyright stays protected.

## Decision

- Publish to Maven Central through the Central Portal under `io.github.khaledamiri`, verified automatically through GitHub sign-in.
- Release on a `vX.Y.Z` tag: GitHub Actions signs with GPG, uploads the libraries and pushes the Docker image to GHCR. The first release is validated by hand (`autoPublish: false`).
- License under Apache 2.0, with the copyright notice in `NOTICE` and in each source file header.
- Name the project "MCP Discovery", not after Eureka, to avoid confusion with Netflix's project; state that it is unrelated to the `rust-mcp-stack/mcp-discovery` CLI.
- Position it as complementary to the official MCP Registry: that registry is a public catalogue of installable servers, MCP Discovery is the runtime layer (live instances, health, current tools).

## Alternatives considered

- **JitPack** — no setup, but less trusted and one more repository for every user to declare.
- **MIT** — equally permissive, without Apache 2.0's patent clause.
- **AGPL-3.0** — stronger protection against closed commercial reuse, but a barrier to enterprise adoption.

## Consequences

- A version published on Maven Central is permanent: a mistake requires a new version.
- mvnrepository.com indexes the library automatically, with a delay of up to two days.
- `-SNAPSHOT` versions exist only locally, after `mvn install`.
