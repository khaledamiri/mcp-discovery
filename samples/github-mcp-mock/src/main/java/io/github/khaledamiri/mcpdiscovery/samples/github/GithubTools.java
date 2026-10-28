/*
 * Copyright 2026 YOUR_NAME
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.github.khaledamiri.mcpdiscovery.samples.github;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** GitHub tools over a few in-memory repositories. */
@Component
public class GithubTools {

    public record Repository(String fullName, String language, int stars, String description) {
    }

    public record PullRequest(String repo, int number, String title, String state, String author) {
    }

    private final List<Repository> repositories = List.of(
            new Repository("khaledamiri/mcp-discovery", "Java", 42, "Service registry for MCP servers"),
            new Repository("acme/payments-service", "Java", 18, "Payment processing microservice"),
            new Repository("acme/web-portal", "TypeScript", 7, "Customer web portal"));

    private final List<PullRequest> pullRequests = List.of(
            new PullRequest("acme/payments-service", 31, "Retry failed card captures", "open", "alice"),
            new PullRequest("acme/payments-service", 29, "Upgrade to Java 21", "merged", "bob"),
            new PullRequest("acme/web-portal", 12, "Fix login redirect loop", "open", "carol"),
            new PullRequest("khaledamiri/mcp-discovery", 3, "Add web dashboard", "open", "khaledamiri"));

    private final AtomicInteger issueNumber = new AtomicInteger(200);
    private final Environment environment;

    public GithubTools(Environment environment) {
        this.environment = environment;
    }

    @Tool(name = "search_repositories", description = "Search GitHub repositories by name, language or description")
    public Map<String, Object> searchRepositories(
            @ToolParam(description = "Words to look for, for example: java payments") String query) {
        List<String> words = List.of(query.toLowerCase(Locale.ROOT).split("\\s+"));
        List<Repository> found = repositories.stream()
                .filter(r -> words.stream().allMatch(w ->
                        (r.fullName() + " " + r.language() + " " + r.description()).toLowerCase(Locale.ROOT).contains(w)))
                .toList();
        return answer(Map.of("total", found.size(), "repositories", found));
    }

    @Tool(name = "list_pull_requests", description = "List the pull requests of a GitHub repository, optionally filtered by state")
    public Map<String, Object> listPullRequests(
            @ToolParam(description = "owner/name, for example acme/payments-service") String repo,
            @ToolParam(description = "open, merged or all (default: open)", required = false) String state) {
        String wanted = state == null || state.isBlank() ? "open" : state;
        List<PullRequest> found = pullRequests.stream()
                .filter(p -> p.repo().equalsIgnoreCase(repo))
                .filter(p -> wanted.equals("all") || p.state().equals(wanted))
                .toList();
        return answer(Map.of("repo", repo, "state", wanted, "pullRequests", found));
    }

    @Tool(name = "create_issue", description = "Create an issue in a GitHub repository")
    public Map<String, Object> createIssue(
            @ToolParam(description = "owner/name") String repo,
            @ToolParam(description = "Issue title") String title,
            @ToolParam(description = "Issue body", required = false) String body) {
        int number = issueNumber.incrementAndGet();
        return answer(Map.of("repo", repo, "number", number, "title", title,
                "url", "https://github.com/" + repo + "/issues/" + number));
    }

    private Map<String, Object> answer(Object result) {
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("servedBy", environment.getProperty("mcp-discovery.client.instance-id", "github-mcp"));
        answer.put("result", result);
        return answer;
    }
}
