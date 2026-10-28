# 0002 — Expire instances with leases, protected by self-preservation

- Status: Accepted
- Date: 2026-10-06

## Context

The registry must drop dead instances so that agents are not sent to unusable addresses. A network partition between the registry and the servers, however, looks exactly like every instance dying at once.

## Decision

We adopt Eureka's defaults:

| Setting | Value | Property |
| --- | --- | --- |
| Heartbeat interval | 30 s | `mcp-discovery.registry.renewal-interval` |
| Lease duration | 90 s | `mcp-discovery.registry.lease-duration` |
| Eviction pass | 60 s | `mcp-discovery.registry.eviction-interval` |
| Self-preservation threshold | 85 % of expected heartbeats | `mcp-discovery.registry.renewal-percent-threshold` |

The registry counts heartbeats per one-minute window. With N instances it expects N × 2 per minute; when the last window falls below 85 % of that, eviction is suspended.

## Alternatives considered

- **Active health checks by the registry** — costly at scale, and impossible when the registry cannot reach the servers.
- **Eviction without self-preservation** — a network partition would empty the whole registry within 90 s.

## Consequences

- After a crash, an instance can stay listed for about 150 s (lease plus eviction pass): agents must be able to try the next instance.
- During self-preservation, genuinely dead instances stay visible until heartbeats climb back above the threshold.
- A graceful shutdown (`DELETE`) removes the instance immediately.
