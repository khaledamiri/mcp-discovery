# 0003 — Keep state in memory and rebuild it by re-registration

- Status: Accepted
- Date: 2026-10-06

## Context

The registry's state is ephemeral by nature: it describes the instances alive right now. A state saved and reloaded later would already be stale.

## Decision

Instances live in a thread-safe two-level map (`serverName` → `instanceId` → instance), with no database. When a heartbeat receives `404`, the client re-registers on its own with the same `instanceId`, so the registry rebuilds itself within one heartbeat interval (30 s) after a restart.

## Alternatives considered

- **Persistence in PostgreSQL or Redis** — one more infrastructure dependency, for data that would be stale on reload.
- **Replicated registry cluster in 0.1** — the right target for high availability, but too complex for a first release.

## Consequences

- Minimal deployment: one jar or one Docker image.
- A single registry is a single point of failure for discovery (see [0001](0001-registry-not-gateway.md) for the limited impact).
- Manual changes made through the API, such as an `OUT_OF_SERVICE` status, are lost on restart.
- Capacity is bounded by one JVM's memory, which is ample for thousands of instances.
