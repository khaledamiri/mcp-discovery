#!/usr/bin/env bash
# Adds the English documentation and the standard files of a professional open-source project:
#
#   docs/adr/            Architecture Decision Records (English), one file per decision + template
#   docs/architecture.md Architecture overview with Mermaid diagrams (rendered by GitHub)
#   CONTRIBUTING.md, CODE_OF_CONDUCT.md, SECURITY.md, CHANGELOG.md
#   .github/             issue forms, pull request template, Dependabot, CODEOWNERS
#   .editorconfig        consistent formatting across editors
#   README.md            badges + documentation section (inserted once)
#
# Usage:   ./scripts/professionalize.sh            # creates missing files, never overwrites
#          ./scripts/professionalize.sh --force    # overwrites existing files
#          OWNER=my-github-user ./scripts/professionalize.sh
# Requires bash and perl (macOS, Linux, Git Bash on Windows).
set -euo pipefail

OWNER="${OWNER:-khaledamiri}"
REPO="${REPO:-mcp-discovery}"
FORCE=0
[ "${1:-}" = "--force" ] && FORCE=1

cd "$(dirname "$0")/.."
command -v perl >/dev/null || { echo "perl is required" >&2; exit 1; }

created=0; skipped=0
write() {
  local file="$1"
  if [ -e "$file" ] && [ "$FORCE" -ne 1 ]; then
    cat >/dev/null
    echo "  skip     $file (exists, use --force to overwrite)"
    skipped=$((skipped + 1))
    return
  fi
  mkdir -p "$(dirname "$file")"
  perl -pe "s/__OWNER__/${OWNER}/g; s/__REPO__/${REPO}/g" > "$file"
  echo "  write    $file"
  created=$((created + 1))
}

echo "Professionalizing ${OWNER}/${REPO}"

# ======================================================================================
# Architecture Decision Records
# ======================================================================================

write docs/adr/README.md <<'EOF'
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
EOF

write docs/adr/template.md <<'EOF'
# NNNN — Title in the imperative ("Use X for Y")

- Status: Proposed | Accepted | Superseded by [NNNN](NNNN-title.md)
- Date: YYYY-MM-DD

## Context

What problem are we solving, and which forces (constraints, requirements, trade-offs) shape the answer?

## Decision

What we decided, stated so that someone can implement it.

## Alternatives considered

- **Option** — why it was not chosen.

## Consequences

- What becomes easier, what becomes harder, and what we must now watch for.
EOF

write docs/adr/0001-registry-not-gateway.md <<'EOF'
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
EOF

write docs/adr/0002-leases-heartbeats-self-preservation.md <<'EOF'
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
EOF

write docs/adr/0003-in-memory-state.md <<'EOF'
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
EOF

write docs/adr/0004-capability-search.md <<'EOF'
# 0004 — Search tools by capability with weighted keywords

- Status: Accepted
- Date: 2026-10-06

## Context

This is the main difference from Eureka: an agent knows its task, not the name of the server able to perform it. Queries arrive in several languages, while tool descriptions are mostly in English.

## Decision

`KeywordToolSearchEngine` implements the `ToolSearchEngine` interface:

1. Tokenise the query, split camelCase and snake_case, strip accents.
2. Drop stop words ("I", "need", "mcp", "tool"…) and map a few French verbs to English ("recherche" → `search`).
3. Score each query term: 3 points in the tool name, 2 in its title or the server name, 1 in the description, half of that for a prefix match.
4. Multiply the total by the share of query terms matched, so tools covering the whole request rank first.

Only instances with status `UP` are considered.

## Alternatives considered

- **Embedding search** — better across languages and synonyms, but brings a model and a heavy dependency into the core library.
- **LLM selection** — the most precise, but slow, costly and non-deterministic for a directory.

## Consequences

- No dependency and reproducible, unit-testable results.
- Quality depends heavily on tool descriptions: a vague description is hard to find.
- Multilingual coverage is limited to a small synonym list.
- An embedding engine can replace the default by declaring a `ToolSearchEngine` bean.
EOF

write docs/adr/0005-tool-discovery-declared-or-probed.md <<'EOF'
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
EOF

write docs/adr/0006-streamable-http-only.md <<'EOF'
# 0006 — Accept only Streamable HTTP servers

- Status: Accepted
- Date: 2026-10-06

## Context

MCP defines two transports: `stdio`, where the client launches the server as a local process, and Streamable HTTP, where the server listens on the network. A `stdio` server has no network address to publish.

## Decision

The `endpoint` must be an absolute `http` or `https` URL with a host; anything else is rejected with `400`. URLs carrying credentials (`https://user:pwd@host`) are rejected too, since every registry client could read them.

## Alternatives considered

- **Register launch commands for `stdio` servers** — that is an installation catalogue, the role of the official MCP Registry.
- **Support the legacy HTTP+SSE transport** — deprecated by the MCP specification in favour of Streamable HTTP.

## Consequences

- A `stdio` server needs an HTTP adapter in front of it before it can register.
- The probe ([0005](0005-tool-discovery-declared-or-probed.md)) speaks a single protocol, version `2025-06-18`.
- The published address must be reachable by agents: `localhost` inside a container is a frequent mistake.
EOF

write docs/adr/0007-shared-api-key.md <<'EOF'
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
EOF

write docs/adr/0008-multi-instance-client-side-load-balancing.md <<'EOF'
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
EOF

write docs/adr/0009-modular-library-spring-boot-starters.md <<'EOF'
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
EOF

write docs/adr/0010-distribution-license-positioning.md <<'EOF'
# 0010 — Distribute on Maven Central under Apache 2.0

- Status: Accepted
- Date: 2026-10-06

## Context

Any Java project should be able to use the library with no special setup, while the author's copyright stays protected.

## Decision

- Publish to Maven Central through the Central Portal under `io.github.__OWNER__`, verified automatically through GitHub sign-in.
- Release on a `vX.Y.Z` tag: GitHub Actions signs with GPG, uploads the libraries and pushes the Docker image to GHCR. The first release is validated by hand (`autoPublish: false`).
- License under Apache 2.0, with the copyright notice in `NOTICE` and in each source file header.
- Name the project "MCP Discovery", not after Eureka, to avoid confusion with Netflix's project; state that it is unrelated to the `rust-mcp-stack/mcp-discovery` CLI.
- Position it as complementary to the official MCP Registry: that registry is a public catalogue of installable servers, MCP Discovery is the runtime layer (live instances, health, current tools).

## Alternatives considered

- **JitPack** — no setup, but less trusted and one more repository for every user to declare.
- **MIT** — equally permissive, without Apache 2.0's patent clause.
- **AGPL-3.0** — stronger protection against closed commercial reuse, but a barrier to enterprise adoption.

## Consequences

- A version published on Maven Central is permanent: a mistake requires a new version.
- mvnrepository.com indexes the library automatically, with a delay of up to two days.
- `-SNAPSHOT` versions exist only locally, after `mvn install`.
EOF

# ======================================================================================
# Architecture overview
# ======================================================================================

write docs/architecture.md <<'EOF'
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
EOF

# ======================================================================================
# Community files
# ======================================================================================

write CONTRIBUTING.md <<'EOF'
# Contributing to MCP Discovery

Thanks for taking the time to contribute. Bug reports, documentation fixes and pull requests are all welcome.

## Before you start

- Search the [existing issues](https://github.com/__OWNER__/__REPO__/issues) to avoid duplicates.
- For a significant change (new module, public API change, new dependency), open an issue first so we can agree on the approach. Architecture changes come with a new [ADR](docs/adr/README.md).
- Security problems are not reported in public issues: see [SECURITY.md](SECURITY.md).

## Development setup

Requirements: Java 21 and Maven 3.9+.

```bash
git clone https://github.com/__OWNER__/__REPO__.git
cd __REPO__
mvn verify                     # build and run every test
```

Run the registry locally with `mvn -pl mcp-discovery-server -am spring-boot:run`.

## Pull requests

1. Fork the repository and create a branch from `main` (`fix/heartbeat-retry`, `feat/embedding-search`).
2. Keep the change focused: one pull request, one topic.
3. Add or update tests; `mvn verify` must pass.
4. Update the documentation and add an entry under **Unreleased** in [CHANGELOG.md](CHANGELOG.md).
5. Write commits in the [Conventional Commits](https://www.conventionalcommits.org) style (`feat:`, `fix:`, `docs:`, `refactor:`, `test:`).

## Coding conventions

- Follow the style of the surrounding code; [.editorconfig](.editorconfig) sets indentation and line endings.
- `mcp-discovery-core` and `mcp-discovery-client` stay framework-free: no Spring imports there.
- Public classes and methods carry Javadoc; the release build fails on Javadoc errors.
- Every source file starts with the Apache 2.0 license header.

## License

By contributing, you agree that your contributions are licensed under the [Apache License 2.0](LICENSE), as stated in section 5 of the license.
EOF

write CODE_OF_CONDUCT.md <<'EOF'
# Code of Conduct

This project adopts the [Contributor Covenant, version 2.1](https://www.contributor-covenant.org/version/2/1/code_of_conduct/) as its code of conduct.

In short: be respectful, welcome newcomers, accept constructive criticism, and focus on what is best for the community. Harassment and personal attacks are not tolerated.

To report unacceptable behaviour, contact the maintainer privately through the GitHub profile [@__OWNER__](https://github.com/__OWNER__). Reports are handled confidentially.
EOF

write SECURITY.md <<'EOF'
# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| 0.1.x | Yes |
| < 0.1 | No |

## Reporting a vulnerability

Please do **not** open a public issue for security problems.

Report it privately through [GitHub private vulnerability reporting](https://github.com/__OWNER__/__REPO__/security/advisories/new). Include the affected version, a description of the issue, and steps to reproduce it.

You can expect an acknowledgement within 5 working days. Once the issue is confirmed, a fix is released and the reporter is credited in the advisory, unless they prefer otherwise.

## Deployment recommendations

- Always set `MCP_DISCOVERY_API_KEY` when the registry is reachable from a shared network.
- Serve the registry over HTTPS (for example behind a reverse proxy): the API key travels in a header.
- Only let trusted MCP servers register: tool descriptions are read by LLMs and can carry injected instructions.
- Never store secrets in instance metadata; every registry client can read it.
EOF

write CHANGELOG.md <<'EOF'
# Changelog

All notable changes to this project are documented in this file.

The format follows [Keep a Changelog](https://keepachangelog.com/en/1.1.0/), and the project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

## [0.1.0] - unreleased

### Added

- In-memory registry with Eureka-style leases, heartbeats and self-preservation.
- Capability search across tool names, titles, descriptions and server names.
- Active tool probing over MCP Streamable HTTP (`tools/list`, JSON and SSE).
- Java client with background heartbeats and automatic re-registration.
- Spring Boot starter for the registry (`@EnableMcpDiscoveryServer`).
- Spring Boot starter for MCP servers, configured from `application.yml`.
- Standalone server and Docker image.

[Unreleased]: https://github.com/__OWNER__/__REPO__/compare/v0.1.0...HEAD
[0.1.0]: https://github.com/__OWNER__/__REPO__/releases/tag/v0.1.0
EOF

# ======================================================================================
# GitHub configuration
# ======================================================================================

write .github/ISSUE_TEMPLATE/bug_report.yml <<'EOF'
name: Bug report
description: Something does not work as documented
labels: [bug]
body:
  - type: textarea
    id: what-happened
    attributes:
      label: What happened?
      description: What did you do, what did you expect, and what happened instead?
    validations:
      required: true
  - type: textarea
    id: reproduce
    attributes:
      label: Steps to reproduce
      description: Configuration (without secrets), requests sent, minimal code.
    validations:
      required: true
  - type: input
    id: version
    attributes:
      label: MCP Discovery version
      placeholder: 0.1.0
    validations:
      required: true
  - type: dropdown
    id: module
    attributes:
      label: Module
      options:
        - mcp-discovery-core
        - mcp-discovery-client
        - mcp-discovery-spring-boot-starter
        - mcp-discovery-client-spring-boot-starter
        - mcp-discovery-server / Docker image
  - type: input
    id: environment
    attributes:
      label: Environment
      placeholder: Java 21, Spring Boot 3.5.6, Linux
  - type: textarea
    id: logs
    attributes:
      label: Logs
      description: Relevant log lines or stack trace (the first "Caused by" is usually enough).
      render: text
EOF

write .github/ISSUE_TEMPLATE/feature_request.yml <<'EOF'
name: Feature request
description: Suggest an improvement
labels: [enhancement]
body:
  - type: textarea
    id: problem
    attributes:
      label: Problem
      description: What are you trying to do, and what makes it hard today?
    validations:
      required: true
  - type: textarea
    id: proposal
    attributes:
      label: Proposed solution
    validations:
      required: true
  - type: textarea
    id: alternatives
    attributes:
      label: Alternatives considered
EOF

write .github/ISSUE_TEMPLATE/config.yml <<'EOF'
blank_issues_enabled: false
contact_links:
  - name: Security vulnerability
    url: https://github.com/__OWNER__/__REPO__/security/advisories/new
    about: Report security problems privately, never in a public issue.
  - name: Questions and ideas
    url: https://github.com/__OWNER__/__REPO__/discussions
    about: Ask questions and discuss ideas with the community.
EOF

write .github/pull_request_template.md <<'EOF'
## What does this change?

<!-- One or two sentences. Link the issue: "Closes #123". -->

## How was it tested?

<!-- Tests added or updated, manual checks. -->

## Checklist

- [ ] `mvn verify` passes
- [ ] Tests cover the change
- [ ] Documentation updated (README, Javadoc, ADR if architecture changes)
- [ ] Entry added under **Unreleased** in `CHANGELOG.md`
EOF

write .github/dependabot.yml <<'EOF'
version: 2
updates:
  - package-ecosystem: maven
    directory: /
    schedule:
      interval: weekly
    groups:
      spring-boot:
        patterns: ["org.springframework.boot*"]
  - package-ecosystem: github-actions
    directory: /
    schedule:
      interval: monthly
  - package-ecosystem: docker
    directory: /
    schedule:
      interval: monthly
EOF

write .github/CODEOWNERS <<'EOF'
# Every change is reviewed by the maintainer.
* @__OWNER__
EOF

write .editorconfig <<'EOF'
root = true

[*]
charset = utf-8
end_of_line = lf
insert_final_newline = true
trim_trailing_whitespace = true
indent_style = space
indent_size = 4

[*.{yml,yaml,json}]
indent_size = 2

[*.md]
trim_trailing_whitespace = false
EOF

# ======================================================================================
# README: badges and documentation section (inserted only once)
# ======================================================================================

if [ -f README.md ]; then
  if ! grep -q "img.shields.io" README.md; then
    OWNER="$OWNER" REPO="$REPO" perl -0pi -e '
      my ($o, $r) = ($ENV{OWNER}, $ENV{REPO});
      my $badges = "[![CI](https://github.com/$o/$r/actions/workflows/ci.yml/badge.svg)](https://github.com/$o/$r/actions/workflows/ci.yml)\n"
                 . "[![Maven Central](https://img.shields.io/maven-central/v/io.github.$o/mcp-discovery-core)](https://central.sonatype.com/namespace/io.github.$o)\n"
                 . "[![License](https://img.shields.io/badge/license-Apache%202.0-blue)](LICENSE)\n"
                 . "![Java](https://img.shields.io/badge/java-21%2B-orange)\n";
      s/\A(# [^\n]+\n)/$1\n$badges/;
    ' README.md
    echo "  update   README.md (badges)"
  fi
  if ! grep -q "^## Documentation" README.md; then
    perl -0pi -e '
      my $doc = "## Documentation\n\n"
              . "- [Architecture overview](docs/architecture.md)\n"
              . "- [Architecture Decision Records](docs/adr/README.md)\n"
              . "- [Changelog](CHANGELOG.md)\n"
              . "- [Security policy](SECURITY.md)\n\n";
      s/^## Contributing\n/$doc## Contributing\n/m;
    ' README.md
    perl -0pi -e 's/^Issues and pull requests are welcome\.[^\n]*\n/Issues and pull requests are welcome: read [CONTRIBUTING.md](CONTRIBUTING.md) and the [Code of Conduct](CODE_OF_CONDUCT.md) first.\n/m' README.md
    echo "  update   README.md (documentation section)"
  fi
fi

echo
echo "Done: $created file(s) written, $skipped skipped."
cat <<EOF

Next steps on GitHub (Settings of ${OWNER}/${REPO}):
  1. Security > Private vulnerability reporting: enable it (used by SECURITY.md).
  2. General > Features: enable Discussions (linked from the issue chooser).
  3. Branches: protect "main" (require pull requests and the CI check).
  4. Commit:  git add . && git commit -m "docs: add ADRs, community files and project metadata"
EOF
