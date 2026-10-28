# MCP Discovery

[![CI](https://github.com/khaledamiri/mcp-discovery/actions/workflows/ci.yml/badge.svg)](https://github.com/khaledamiri/mcp-discovery/actions/workflows/ci.yml)
[![Maven Central](https://img.shields.io/maven-central/v/io.github.khaledamiri/mcp-discovery-core)](https://central.sonatype.com/namespace/io.github.khaledamiri)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)
![Java](https://img.shields.io/badge/java-21%2B-orange)

**Service registry and tool discovery for [Model Context Protocol](https://modelcontextprotocol.io) servers, as a Java library.**

Eureka answers *"where is my microservice?"*. MCP Discovery answers *"which MCP server can do what I need, and where is a healthy instance of it?"*

```
                    ┌──────────────────────────┐
  register ───────▶ │                          │ ◀─────── "search jira issues"
  heartbeat ──────▶ │      MCP Discovery       │ ───────▶ jira-mcp / search_issues
  tools/list ◀───── │  leases · health · tools │          → http://10.0.2.15:8080/mcp
                    └──────────────────────────┘          → http://10.0.2.16:8080/mcp
          ▲                  ▲                  ▲
     Jira MCP           GitLab MCP        OpenSearch MCP
```

## Modules

| Artifact | Use it to | Depends on |
|---|---|---|
| `mcp-discovery-spring-boot-starter` | Embed a registry in a Spring Boot app with `@EnableMcpDiscoveryServer` | Spring Boot 3.5, core |
| `mcp-discovery-client-spring-boot-starter` | Register a Spring Boot MCP server from `application.yml`, with no code | Spring Boot 3.5, client |
| `mcp-discovery-client` | Register an MCP server (with automatic heartbeats) or search tools from an agent | core, JDK `HttpClient` |
| `mcp-discovery-core` | Use the registry, search engine and MCP probe in plain Java | Jackson only |
| `mcp-discovery-server` | Run a ready-made registry (Docker image, not on Maven Central) | starter |

All published modules require Java 21.

## Run a registry

Add the starter to a Spring Boot web application:

```xml
<dependency>
    <groupId>io.github.khaledamiri</groupId>
    <artifactId>mcp-discovery-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

```java
@SpringBootApplication
@EnableMcpDiscoveryServer
public class RegistryApplication {
    public static void main(String[] args) {
        SpringApplication.run(RegistryApplication.class, args);
    }
}
```

Or run the standalone server:

```bash
docker run -p 8761:8761 ghcr.io/khaledamiri/mcp-discovery
# or, from source
mvn -pl mcp-discovery-server -am spring-boot:run
```

## Try it

The [samples](samples/README.md) folder runs a complete scenario: a registry, mock Jira and GitHub MCP servers, and an agent that discovers and calls their tools — with Docker Compose or a single script.

## Dashboard

The registry ships with a web dashboard, like Eureka's: open `http://localhost:8761/` on the standalone server, or `/mcp-discovery` in an application using the starter.

It shows the registry health (instances, heartbeats, self-preservation), one bar per instance coloured by heartbeat freshness, each server with its instances, versions, lease countdown and tools, and a tool search box. With the API key entered, you can take an instance out of service, put it back, or deregister it.

A "Connect to this registry" section lists the addresses to use — registry URL, REST endpoints, health check — with copy buttons and a ready-to-paste `application.yml` snippet. The same addresses are printed in the log at startup:

```
  MCP Discovery registry is running
    Dashboard     http://localhost:8761/
    Registry URL  http://localhost:8761   (mcp-discovery.client.registry-url)
    REST API      http://localhost:8761/api/v1
    API key       required for writes (X-Mcp-Discovery-Key)
```

The page is a single static file with no external resources; it reads the same REST API as any client. Disable it with `mcp-discovery.dashboard.enabled=false`.

## Register a Spring Boot MCP server (application.yml)

```xml
<dependency>
    <groupId>io.github.khaledamiri</groupId>
    <artifactId>mcp-discovery-client-spring-boot-starter</artifactId>
    <version>0.1.0</version>
</dependency>
```

```yaml
spring:
  application:
    name: jira-mcp
server:
  port: 8080
mcp-discovery:
  client:
    registry-url: http://localhost:8761
    api-key: ${MCP_DISCOVERY_API_KEY}
    metadata:
      team: platform
```

That is all. Once the application is ready, it registers as `jira-mcp` at `http://<its IP>:8080/mcp`, sends heartbeats, re-registers if the registry restarts, and deregisters on shutdown. If the registry is unreachable at startup, the application starts anyway and registration is retried in the background. A full example with every option is in [`examples/jira-mcp-application.yml`](examples/jira-mcp-application.yml).

| Property | Default | Description |
|---|---|---|
| `mcp-discovery.client.enabled` | `true` | Create the client |
| `mcp-discovery.client.register` | `true` | Register this application (`false` for agents that only search) |
| `mcp-discovery.client.registry-url` | `http://localhost:8761` | Registry URL |
| `mcp-discovery.client.api-key` | empty | Shared secret (`X-Mcp-Discovery-Key`) |
| `mcp-discovery.client.server-name` | `spring.application.name` | Logical server name |
| `mcp-discovery.client.instance-id` | generated | Stable instance id |
| `mcp-discovery.client.endpoint` | derived | Full MCP endpoint advertised to agents |
| `mcp-discovery.client.hostname` | first non-loopback IP | Host used to derive the endpoint |
| `mcp-discovery.client.prefer-ip-address` | `true` | Derive the host as an IP rather than a host name |
| `mcp-discovery.client.scheme` / `path` | `http` / `/mcp` | Used to derive the endpoint |
| `mcp-discovery.client.version` | empty | Server version |
| `mcp-discovery.client.metadata.*` | empty | Free-form metadata (never secrets) |
| `mcp-discovery.client.tools[*]` | empty | `name`, `title`, `description`, `input-schema` |
| `mcp-discovery.client.retry-interval` | `PT30S` | Delay between registration attempts |
| `mcp-discovery.client.heartbeat-interval` | from registry | Heartbeat override |

An agent can inject the same `McpDiscoveryClient` bean to search tools; set `mcp-discovery.client.register=false` if it is not an MCP server itself.

## Register an MCP server (plain Java)

```xml
<dependency>
    <groupId>io.github.khaledamiri</groupId>
    <artifactId>mcp-discovery-client</artifactId>
    <version>0.1.0</version>
</dependency>
```

```java
McpDiscoveryClient discovery = McpDiscoveryClient.builder("http://discovery:8761")
        .apiKey(System.getenv("MCP_DISCOVERY_API_KEY"))
        .build();

Registration registration = discovery.register(new RegistrationRequest(
        "jira-mcp",                     // server name
        null,                           // instance id (generated)
        "http://10.0.2.15:8080/mcp",    // Streamable HTTP endpoint
        "1.2.0",                        // version
        null,                           // status, defaults to UP
        Map.of("team", "platform"),     // metadata (never secrets)
        List.of(new ToolDescriptor("search_issues", "Search issues",
                "Search Jira issues using JQL", Map.of("type", "object")))));

// On shutdown: stops heartbeats and deregisters
Runtime.getRuntime().addShutdownHook(new Thread(discovery::close));
```

Heartbeats are sent in the background. If the registry restarts or the lease expires, the client registers again with the same instance id.

## Discover a tool from an agent

```java
List<ToolMatch> matches = discovery.searchTools("recherche jira", 5);
ToolMatch best = matches.get(0);
// best.serverName() = "jira-mcp", best.toolName() = "search_issues"
// best.endpoints()  = healthy instances, pick one and connect with any MCP client
```

## Use the core without Spring

```java
McpRegistry registry = new McpRegistry(RegistrySettings.defaults(), Clock.systemUTC());
RegistryMaintenance maintenance = new RegistryMaintenance(registry, Duration.ofSeconds(60), null, null);
maintenance.start();

registry.register(request);
List<ToolMatch> matches = new KeywordToolSearchEngine().search("search jira", registry.allInstances(), 5);
```

Implement `ToolSearchEngine` to plug in embeddings, or `ToolsProbe` for custom transports or authentication. With the starter, declaring your own bean replaces the default one.

## Features

- **Eureka-style leases**: heartbeat every 30 s, eviction after 90 s without heartbeat.
- **Self-preservation**: when far fewer heartbeats arrive than expected (network partition), eviction is suspended instead of wiping the registry.
- **Capability search** over tool names, titles, descriptions and server names, with camelCase/snake_case splitting and basic English/French support.
- **Active probing** (optional): MCP handshake over Streamable HTTP, `tools/list` with pagination, JSON and SSE responses.
- **Only healthy instances are returned**, grouped per tool with all their endpoints for client-side load balancing.

How it differs from the [official MCP Registry](https://registry.modelcontextprotocol.io): the official registry is a public catalogue of servers and how to install them. MCP Discovery is the runtime layer inside your platform: running instances, health, and live tools.

## API

| Method | Path | Description |
|---|---|---|
| `POST` | `/api/v1/servers` | Register (or re-register) an instance |
| `GET` | `/api/v1/servers` | List servers with instance counts |
| `GET` | `/api/v1/servers/{name}/instances?status=UP` | List instances of a server |
| `GET` | `/api/v1/servers/{name}/instances/{id}` | Get one instance |
| `PUT` | `/api/v1/servers/{name}/instances/{id}/heartbeat` | Renew the lease |
| `PUT` | `/api/v1/servers/{name}/instances/{id}/status` | Set `UP`, `DOWN`, `STARTING`, `OUT_OF_SERVICE` |
| `PUT` | `/api/v1/servers/{name}/instances/{id}/tools` | Replace the tool list |
| `POST` | `/api/v1/servers/{name}/instances/{id}/tools/refresh` | Probe the instance now (`tools/list`) |
| `DELETE` | `/api/v1/servers/{name}/instances/{id}` | Deregister (graceful shutdown) |
| `GET` | `/api/v1/tools/search?q=...&limit=10` | Capability search over UP instances |
| `GET` | `/api/v1/instances` | Every registered instance, whatever its status |
| `GET` | `/api/v1/registry/status` | Instance counts and self-preservation state |
| `GET` | `/actuator/health` | Health of the registry (standalone server only) |

Errors are returned as [RFC 9457](https://www.rfc-editor.org/rfc/rfc9457) problem details.

## Configuration

| Property | Default | Description |
|---|---|---|
| `mcp-discovery.registry.lease-duration` | `PT90S` | Eviction delay without heartbeat |
| `mcp-discovery.registry.renewal-interval` | `PT30S` | Expected heartbeat interval |
| `mcp-discovery.registry.eviction-interval` | `PT60S` | Eviction task period |
| `mcp-discovery.registry.self-preservation` | `true` | Enable self-preservation |
| `mcp-discovery.registry.renewal-percent-threshold` | `0.85` | Fraction of expected heartbeats below which eviction stops |
| `mcp-discovery.probe.enabled` | `false` | Actively list tools on instances (`MCP_DISCOVERY_PROBE_ENABLED`) |
| `mcp-discovery.probe.interval` | `PT5M` | Probe period |
| `mcp-discovery.probe.timeout` | `PT10S` | Probe HTTP timeout |
| `mcp-discovery.security.api-key` | empty | Shared secret for writes, sent as `X-Mcp-Discovery-Key` (`MCP_DISCOVERY_API_KEY`) |
| `mcp-discovery.security.protect-reads` | `false` | Also require the key for reads |
| `mcp-discovery.dashboard.enabled` | `true` | Serve the web dashboard |
| `mcp-discovery.dashboard.path` | `/mcp-discovery` (`/` in the standalone server) | Dashboard URL path |

Instances can opt out of probing with the metadata entry `"mcp-discovery.probe": "false"`.

## Security notes

- Tool descriptions are read by LLMs. A malicious server can register descriptions containing hidden instructions (*tool poisoning*). Only let trusted servers register: enable the API key, restrict network access, and review new servers.
- Metadata is readable by every client. Never store tokens or credentials in it.
- The API key is a minimal safeguard. For production, put MCP Discovery behind your identity provider (OAuth2, mTLS).
- Only Streamable HTTP servers can be registered; `stdio` servers have no network endpoint.

## Build

```bash
mvn verify                      # build and test every module
mvn -Prelease deploy            # sign and upload to Maven Central (maintainers)
```

Releases are automated: pushing a tag `vX.Y.Z` publishes the libraries to Maven Central and the server image to GitHub Container Registry (see `.github/workflows/release.yml`).

## Roadmap

- Expose MCP Discovery itself as an MCP server (`find_tools`) so agents can discover tools natively
- Embedding-based search engine
- Peer replication for high availability
- Per-tool access policies

## Documentation

- [Architecture overview](docs/architecture.md)
- [Architecture Decision Records](docs/adr/README.md)
- [Changelog](CHANGELOG.md)
- [Security policy](SECURITY.md)

## Contributing

Issues and pull requests are welcome: read [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md) first.

## License

Copyright 2026 YOUR_NAME

Licensed under the [Apache License, Version 2.0](LICENSE). See [NOTICE](NOTICE).

MCP Discovery is an independent project, not affiliated with Netflix, Anthropic, the Model Context Protocol project or the rust-mcp-stack mcp-discovery CLI.
