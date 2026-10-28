# 0006 — Accept only Streamable HTTP servers

- Status: Accepted
- Date: 2026-10-06

## Context

MCP defines two transports: `stdio`, where the client launches the server as a local process, and Streamable HTTP, where the server listens on the network. A `stdio` server has no network address to publish.

## Decision

The `endpoint` must be an absolute `http` or `https` URL with a host; anything else is rejected with `400`. URLs carrying credentials (`https://user:pwd@host`) are rejected too, since every registry client could read them.

## Alternatives considered

- **Register launch commands for `stdio` servers** — that is an installation catalogue, the role of the official MCP Registry.
- **Support the legacy HTTP+SSE transport** — deprecated by the MCP specification in favour of Streamable HTTP.

## Consequences

- A `stdio` server needs an HTTP adapter in front of it before it can register.
- The probe ([0005](0005-tool-discovery-declared-or-probed.md)) speaks a single protocol, version `2025-06-18`.
- The published address must be reachable by agents: `localhost` inside a container is a frequent mistake.
