# Architecture Decision Records

This folder records the architecture decisions behind MCP Discovery, one decision per file, following the [MADR](https://adr.github.io/madr/) format. A decision is never deleted: when it changes, its status becomes *Superseded* and a new record links to it.

| ADR | Decision | Status |
| --- | --- | --- |
| [0001](0001-registry-not-gateway.md) | Pure registry: agents call MCP servers directly, no gateway | Accepted |
| [0002](0002-leases-heartbeats-self-preservation.md) | 90 s leases, 30 s heartbeats, self-preservation at 85 % | Accepted |
| [0003](0003-in-memory-state.md) | In-memory state, rebuilt by automatic re-registration | Accepted |
| [0004](0004-capability-search.md) | Weighted keyword search behind a pluggable engine | Accepted |
| [0005](0005-tool-discovery-declared-or-probed.md) | Tools declared by servers or probed with `tools/list` | Accepted |
| [0006](0006-streamable-http-only.md) | Only Streamable HTTP servers can register | Accepted |
| [0007](0007-shared-api-key.md) | Shared API key `X-Mcp-Discovery-Key` for writes | Accepted |
| [0008](0008-multi-instance-client-side-load-balancing.md) | Instances grouped per server, load balancing in the agent | Accepted |
| [0009](0009-modular-library-spring-boot-starters.md) | Five modules, framework-free core, Spring Boot starters | Accepted |
| [0010](0010-distribution-license-positioning.md) | Maven Central under `io.github`, Apache 2.0, Docker image on GHCR | Accepted |

To propose a new decision, copy [template.md](template.md) to the next number and open a pull request.
