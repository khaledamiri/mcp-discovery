# 0001 — Use a pure registry, not a gateway

- Status: Accepted
- Date: 2026-10-06

## Context

Agents need to find MCP servers without knowing their addresses in advance. Two models exist: a registry that hands out addresses, or a gateway that receives every call and forwards it.

## Decision

MCP Discovery is a registry, modelled on Netflix Eureka. It answers "who can do X, and where?" and then steps out of the loop: `tools/call` requests go from the agent straight to the MCP server.

## Alternatives considered

- **MCP gateway** — centralises authentication, logging and quotas, but sits on the path of every call as a bottleneck and a single point of failure.
- **Static addresses in each agent** — simple, but every new instance or address change means reconfiguring every agent.

## Consequences

- A registry outage blocks discovery, not calls in progress: an agent that already holds an address keeps working.
- The registry never sees tool arguments or results, so no business data flows through it.
- Authentication to backend systems (for example a Jira token) stays inside each MCP server.
- Centralised audit and quotas are out of scope; a gateway can still be combined with MCP Discovery.
