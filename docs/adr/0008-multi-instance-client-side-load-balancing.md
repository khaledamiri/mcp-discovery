# 0008 — Group instances per server and balance load in the agent

- Status: Accepted
- Date: 2026-10-06

## Context

An MCP server often runs as several copies, for load or during a rolling upgrade. Each copy can fail, be drained, or run a different version.

## Decision

- Instances of one server share a `serverName` and differ by `instanceId`; each has its own lease, status and tool list.
- Search groups results per (server, tool) and lists the endpoints of the `UP` instances exposing that tool.
- When instances describe a tool differently, the most recently updated description wins.
- Status `OUT_OF_SERVICE` removes an instance from search without stopping it.
- The agent picks the instance: random order, then the next instance on failure.

## Alternatives considered

- **Load balancing in the registry (one address returned)** — cannot be done well without seeing calls, which contradicts [0001](0001-registry-not-gateway.md).
- **One result per instance** — the agent would receive the same tool N times.

## Consequences

- Endpoints come back in a fixed order (sorted by `instanceId`): an agent that always takes the first one concentrates load on one instance.
- An MCP session (`Mcp-Session-Id`) exists only on the instance that opened it: the agent must keep that instance for the whole session.
- Two instances registered with the same `instanceId` overwrite each other: leave the id empty or use a unique value such as `${HOSTNAME}`.
- The self-preservation threshold ([0002](0002-leases-heartbeats-self-preservation.md)) scales with the instance count.
