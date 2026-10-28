# Architecture

MCP Discovery is a service registry for [Model Context Protocol](https://modelcontextprotocol.io) servers. It tells agents which server can perform a task and where a healthy instance of it runs; agents then call the server directly ([ADR 0001](adr/0001-registry-not-gateway.md)).

## Components

```mermaid
flowchart LR
    subgraph Servers["MCP servers"]
        J[jira-mcp]
        G[gitlab-mcp]
    end
    R[(MCP Discovery<br/>registry)]
    A[Agent]

    J -- register / heartbeat 30 s --> R
    G -- register / heartbeat 30 s --> R
    A -- search by capability --> R
    R -. healthy endpoints .-> A
    A == tools/call, direct ==> J
```

## Lifecycle of an instance

```mermaid
sequenceDiagram
    participant S as jira-mcp
    participant R as MCP Discovery
    participant A as Agent

    S->>R: POST /api/v1/servers (X-Mcp-Discovery-Key)
    R-->>S: 201 instanceId, renewal every 30 s
    opt probing enabled
        R->>S: initialize + tools/list
        S-->>R: tools
    end
    loop every 30 s
        S->>R: PUT /heartbeat
        R-->>S: 204 (404 = register again)
    end
    A->>R: GET /api/v1/tools/search?q=jira
    R-->>A: search_issues + healthy endpoints
    A->>S: tools/call (MCP, direct)
    S-->>A: result
    S->>R: DELETE /instances/{id} on shutdown
    Note over R: no heartbeat for 90 s → eviction
```

## Modules

| Module | Role |
| --- | --- |
| `mcp-discovery-core` | Registry, leases, self-preservation, tool search, MCP probe — plain Java |
| `mcp-discovery-client` | HTTP client: registration, heartbeats, re-registration, search |
| `mcp-discovery-spring-boot-starter` | Embeds the registry in a Spring Boot app (`@EnableMcpDiscoveryServer`) |
| `mcp-discovery-client-spring-boot-starter` | Registers a Spring Boot MCP server from `application.yml` |
| `mcp-discovery-server` | Ready-to-run registry, shipped as a Docker image |

The reasoning behind each choice is in the [Architecture Decision Records](adr/README.md).
