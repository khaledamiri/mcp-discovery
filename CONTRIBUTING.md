# Contributing to MCP Discovery

Thanks for taking the time to contribute. Bug reports, documentation fixes and pull requests are all welcome.

## Before you start

- Search the [existing issues](https://github.com/khaledamiri/mcp-discovery/issues) to avoid duplicates.
- For a significant change (new module, public API change, new dependency), open an issue first so we can agree on the approach. Architecture changes come with a new [ADR](docs/adr/README.md).
- Security problems are not reported in public issues: see [SECURITY.md](SECURITY.md).

## Development setup

Requirements: Java 21 and Maven 3.9+.

```bash
git clone https://github.com/khaledamiri/mcp-discovery.git
cd mcp-discovery
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
