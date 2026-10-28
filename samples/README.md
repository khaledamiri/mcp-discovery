# MCP Discovery demo

A complete, runnable scenario: a registry built with Spring Boot, mock Jira and GitHub MCP servers that register themselves, and an agent that finds tools through the registry and calls them.

```
                       ┌──────────────────────────┐
  jira-mcp-1 :8081 ──▶ │                          │ ◀── demo-agent
  jira-mcp-2 :8083 ──▶ │  registry :8761          │     "recherche jira" → jira-mcp / search_issues
  github-mcp :8082 ──▶ │  dashboard at /          │     then tools/call directly on jira-mcp-1 or -2
                       └──────────────────────────┘
```

| Module | What it is |
| --- | --- |
| `registry` | A Spring Boot app with `@EnableMcpDiscoveryServer`, probing enabled, dashboard at `/` |
| `jira-mcp-mock` | A real MCP server built with **Spring AI** (Streamable HTTP), whose tools return fake data: `search_issues`, `get_issue`, `create_issue` |
| `github-mcp-mock` | Same for GitHub: `search_repositories`, `list_pull_requests`, `create_issue` |
| `demo-agent` | Command-line agent: searches by need, picks an instance at random, calls the tool, fails over |

Each mock is a regular Spring AI MCP server: tools are `@Tool` methods, exposed by a `ToolCallbackProvider` bean, and `spring.ai.mcp.server.protocol: STREAMABLE` serves them on `/mcp`. Only the data behind the tools is fake. Replace the in-memory lists with calls to the real Jira or GitHub APIs and you have production servers.

MCP Discovery adds one dependency to each mock, `mcp-discovery-client-spring-boot-starter`, configured in `application.yml`. The mocks declare no tools there: the registry discovers them by calling `tools/list`.

The samples use Spring Boot 3.5.14 and Spring AI 1.1.8 (the Spring AI line for Spring Boot 3.x; Spring AI 2.x requires Spring Boot 4).

The demo registry lowers the self-preservation threshold to 50 % (see its `application.yml`): with only three instances, the default 85 % would read a single stopped instance as a network partition.

The demo API key is `demo-key`. It is a default for this demo only; set `MCP_DISCOVERY_API_KEY` to change it.

## Run it

### With Docker

```bash
docker compose -f samples/docker-compose.yml up --build -d     # registry + 3 mock servers
open http://localhost:8761                                     # dashboard
docker compose -f samples/docker-compose.yml run --rm demo-agent
docker compose -f samples/docker-compose.yml down              # stop everything
```

### Without Docker

Requires Java 21, Maven 3.9+ and `curl`. From the repository root:

```bash
./samples/run-local.sh            # builds, starts everything, runs the agent; Ctrl-C to stop
./samples/run-local.sh --no-build # same, without rebuilding
```

On Windows, or to see each log in its own window, run these in five terminals after `mvn install` at the root and `mvn -f samples/pom.xml package`:

```bash
java -jar samples/registry/target/registry.jar
PORT=8081 MCP_INSTANCE_ID=jira-mcp-1 java -jar samples/jira-mcp-mock/target/jira-mcp-mock.jar
PORT=8083 MCP_INSTANCE_ID=jira-mcp-2 java -jar samples/jira-mcp-mock/target/jira-mcp-mock.jar
PORT=8082 java -jar samples/github-mcp-mock/target/github-mcp-mock.jar
java -jar samples/demo-agent/target/demo-agent.jar
```

## The scenario

### 1. Registration

Open the dashboard at `http://localhost:8761`. Within a few seconds you should see:

- `jira-mcp` with 2 instances and `github-mcp` with 1 instance, all `UP`;
- three green bars at the top, one per instance;
- "No tools known yet" at first, then about 20 s later the tools appear: the registry probed each server with `tools/list`.

In the logs of a mock server, look for `Registered jira-mcp/jira-mcp-1`.

### 2. Discovery and calls

Run the agent. For each need, it prints the candidates the registry found, the instance it chose, and the answer:

```
==============================================================================
Need: "recherche jira"
  candidate  jira-mcp     search_issues          score  5.00  2 instance(s)
  candidate  github-mcp   search_repositories    score  1.50  1 instance(s)
  chosen     jira-mcp / search_issues at http://localhost:8083/mcp
  arguments  {"jql":"project = DEMO AND status = Open"}
  served by  jira-mcp-2
  | {
  |   "jql" : "project = DEMO AND status = Open",
  |   "total" : 2,
  |   "issues" : [ ...DEMO-1, DEMO-4... ]
  | }
```

The other needs are "get jira issue", "create jira issue" (note that `github-mcp / create_issue` comes second: the word "jira" decides), "pull requests github" and "search repositories java". The last block sends six calls to `search_issues` and shows that both Jira instances answer.

Try your own searches in the dashboard's "Find a tool" box: `create issue` returns both servers, `pull requests` only GitHub.

### 3. Experiments

| Try this | What you should see |
| --- | --- |
| Stop one Jira instance (`docker compose -f samples/docker-compose.yml stop jira-mcp-2`, or kill its process) | Its bar turns orange after about 45 s, red after 90 s, and the instance disappears within the next eviction pass (15 s in this demo). Run the agent meanwhile: if it picks the dead instance, it logs the failure and uses the other one. |
| Stop two of the three instances | After a minute, the dashboard shows the self-preservation banner: too few heartbeats arrive, so the registry assumes a network problem and stops evicting. Start them again and the banner goes away. |
| Stop it gracefully instead (Ctrl-C on a local process) | The instance disappears at once: it deregisters on shutdown. |
| In the dashboard, enter the API key `demo-key`, then "Take out of service" on `jira-mcp-1` | The instance stays listed as `OUT OF SERVICE`; the agent's load-balancing block now only shows `jira-mcp-2`. "Put back in service" restores it. |
| Restart the registry (`docker compose -f samples/docker-compose.yml restart registry`) | The dashboard is empty for a moment, then every server comes back within 30 s: each heartbeat receives `404` and the client registers again. |
| Start a mock with a wrong key (`MCP_DISCOVERY_API_KEY=wrong`) | Its log shows `Registration of jira-mcp rejected` with HTTP 401; it does not retry and never appears in the dashboard. |
| Start a mock with `MOCK_LATENCY=2s` | Calls served by that instance are visibly slower; the registry is not involved in the call. |

### 4. Talk to a mock directly

The mocks speak real MCP, so you can call them by hand:

```bash
SESSION=$(curl -si http://localhost:8081/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -d '{"jsonrpc":"2.0","id":1,"method":"initialize","params":{"protocolVersion":"2025-06-18","capabilities":{},"clientInfo":{"name":"curl","version":"1"}}}' \
  | grep -i '^mcp-session-id' | cut -d' ' -f2 | tr -d '\r')

curl -s http://localhost:8081/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SESSION" -H 'MCP-Protocol-Version: 2025-06-18' \
  -d '{"jsonrpc":"2.0","method":"notifications/initialized"}'

curl -sN http://localhost:8081/mcp -H 'Content-Type: application/json' -H 'Accept: application/json, text/event-stream' \
  -H "Mcp-Session-Id: $SESSION" -H 'MCP-Protocol-Version: 2025-06-18' \
  -d '{"jsonrpc":"2.0","id":2,"method":"tools/call","params":{"name":"get_issue","arguments":{"key":"DEMO-1"}}}'
```

Spring AI answers requests as a Server-Sent Events stream: the response is on the `data:` line.

Any MCP client (the MCP Java SDK, an IDE, an MCP inspector) can connect to `http://localhost:8081/mcp` the same way.
