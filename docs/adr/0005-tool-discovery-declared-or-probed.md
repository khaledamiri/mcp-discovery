# 0005 — Learn tools from declarations or by probing `tools/list`

- Status: Accepted
- Date: 2026-10-06

## Context

An MCP server can add or remove tools in any release. A hand-written list drifts from the real code, while the protocol lets anyone ask the server directly.

## Decision

Three sources are accepted:

| Source | Where to configure it | When to use it |
| --- | --- | --- |
| Probing by the registry | `mcp-discovery.probe.enabled: true` on the registry | Default whenever the registry can reach the servers |
| Declaration in configuration | `mcp-discovery.client.tools` in the server's `application.yml` | MCP endpoint protected or unreachable from the registry |
| Declaration in code | Last argument of `RegistrationRequest` | MCP servers without Spring |

Probing runs `initialize` → `notifications/initialized` → `tools/list` (paginated) → session close, accepts JSON and SSE responses, and repeats every 5 minutes. An instance opts out with the metadata entry `mcp-discovery.probe: false`.

## Alternatives considered

- **Declaration only** — simple, but stale as soon as someone forgets to update it.
- **Probing only** — impossible when the MCP endpoint requires credentials the registry does not hold.

## Consequences

- With probing, the catalogue follows the real code with at most 5 minutes of lag.
- Probing sends no authentication header, so it only works with endpoints reachable without credentials.
- Combining probing and declaration makes no sense: the declaration is overwritten on the next pass.
- A failed probe is recorded in `lastProbeError` without changing the instance status.
