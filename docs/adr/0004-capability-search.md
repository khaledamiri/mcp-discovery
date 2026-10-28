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
