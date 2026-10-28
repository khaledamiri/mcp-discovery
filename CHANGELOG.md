# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Added

- Web dashboard for the registry (`mcp-discovery.dashboard.*`), served at `/` by the standalone server.
- `GET /api/v1/instances` endpoint; registry status now includes lease settings and server time.

## [0.1.0] - unreleased

### Added

- In-memory registry with Eureka-style leases, heartbeats and self-preservation.
- Capability search across tool names, titles, descriptions and server names.
- Active tool probing over MCP Streamable HTTP (`tools/list`, JSON and SSE).
- Java client with background heartbeats and automatic re-registration.
- Spring Boot starter for the registry (`@EnableMcpDiscoveryServer`).
- Spring Boot starter for MCP servers, configured from `application.yml`.
- Standalone server and Docker image.

[Unreleased]: https://github.com/khaledamiri/mcp-discovery/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/khaledamiri/mcp-discovery/releases/tag/v0.1.0
