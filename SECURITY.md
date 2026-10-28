# Security Policy

## Supported versions

| Version | Supported |
| --- | --- |
| 0.1.x | Yes |
| < 0.1 | No |

## Reporting a vulnerability

Please do **not** open a public issue for security problems.

Report it privately through [GitHub private vulnerability reporting](https://github.com/khaledamiri/mcp-discovery/security/advisories/new). Include the affected version, a description of the issue, and steps to reproduce it.

You can expect an acknowledgement within 5 working days. Once the issue is confirmed, a fix is released and the reporter is credited in the advisory, unless they prefer otherwise.

## Deployment recommendations

- Always set `MCP_DISCOVERY_API_KEY` when the registry is reachable from a shared network.
- Serve the registry over HTTPS (for example behind a reverse proxy): the API key travels in a header.
- Only let trusted MCP servers register: tool descriptions are read by LLMs and can carry injected instructions.
- Never store secrets in instance metadata; every registry client can read it.
