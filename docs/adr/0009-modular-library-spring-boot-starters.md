# 0009 — Ship a modular library with Spring Boot starters

- Status: Accepted
- Date: 2026-10-06

## Context

Three audiences use MCP Discovery: people hosting the registry, people building MCP servers, and people writing agents. Each should only pull in what it needs.

## Decision

| Module | For | Depends on | Activation |
| --- | --- | --- | --- |
| `mcp-discovery-core` | Plain Java use | Jackson | Code |
| `mcp-discovery-client` | MCP servers and agents without Spring | core | `McpDiscoveryClient.builder(…)` |
| `mcp-discovery-spring-boot-starter` | Registry hosts | Spring Boot 3.5, core | `@EnableMcpDiscoveryServer` |
| `mcp-discovery-client-spring-boot-starter` | Spring Boot MCP servers and agents | Spring Boot 3.5, client | Dependency + `application.yml` |
| `mcp-discovery-server` | Operators (not on Maven Central) | server starter | `java -jar` or Docker |

- The core runs its own maintenance on daemon threads and never requires `@EnableScheduling`.
- The registry only starts with `@EnableMcpDiscoveryServer`, like `@EnableEurekaServer`: a stray dependency opens no endpoint.
- The client starter registers automatically, but only on `ApplicationReadyEvent`, once the web server is listening.
- When the registry is unreachable at startup, registration retries every 30 s in the background; a `401` stops the retries.
- Every starter bean can be replaced by declaring your own.

## Alternatives considered

- **A single Spring Boot application** — cannot be reused as a dependency.
- **A Spring-dependent core** — would exclude MCP servers written without Spring.

## Consequences

- The client starter registers an application but does not make it an MCP server: Spring AI or the MCP Java SDK is still required.
- Without `spring.application.name` or `mcp-discovery.client.server-name`, the application refuses to start.
- Java 21 is the minimum for every module.
