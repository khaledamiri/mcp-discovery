# 0007 — Protect writes with a shared API key

- Status: Accepted
- Date: 2026-10-06

## Context

Tool descriptions are read by LLMs. A rogue server could hide malicious instructions in them (tool poisoning) and hijack agents, so the registry must control who can register.

## Decision

- One key, chosen by the registry operator, generated once (`openssl rand -hex 32`) and handed to authorised MCP servers.
- Writes (register, heartbeat, status, tools, deregister) require the `X-Mcp-Discovery-Key` header; reads stay open by default.
- The registry reads the key from `mcp-discovery.security.api-key` (`MCP_DISCOVERY_API_KEY`); without a key, protection is off.
- Comparison runs in constant time; a missing or wrong key returns `401`.
- `mcp-discovery.security.protect-reads: true` also protects search and lookups.

## Alternatives considered

- **OAuth2 or mTLS in 0.1** — the right production target, but a disproportionate setup cost to get started.
- **One key per server** — safer, but needs key management the first release does not have.

## Consequences

- Rotating the key means updating it everywhere at once; servers not yet updated are rejected, then evicted.
- Any key holder can register any server name, including someone else's.
- Over plain HTTP the key travels in clear text: put an HTTPS reverse proxy in front on any shared network.
- Metadata is readable by every client and must never contain secrets.
