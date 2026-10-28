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
package io.github.khaledamiri.mcpdiscovery.samples.jira;

import java.time.Duration;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.ai.tool.annotation.Tool;
import org.springframework.ai.tool.annotation.ToolParam;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/**
 * Jira tools over an in-memory issue list. Every answer includes "servedBy", the instance that
 * handled the call, so that load balancing between instances is visible.
 */
@Component
public class JiraTools {

    public record Issue(String key, String project, String summary, String status, String priority, String assignee) {
    }

    private static final Pattern PROJECT = Pattern.compile("project\\s*=\\s*\"?([A-Za-z0-9]+)\"?", Pattern.CASE_INSENSITIVE);
    private static final Pattern STATUS = Pattern.compile("status\\s*=\\s*(?:\"([^\"]+)\"|([A-Za-z]+))", Pattern.CASE_INSENSITIVE);

    private final List<Issue> issues = new CopyOnWriteArrayList<>(List.of(
            new Issue("DEMO-1", "DEMO", "Login page returns HTTP 500", "Open", "High", "alice"),
            new Issue("DEMO-2", "DEMO", "Add dark mode to the dashboard", "In Progress", "Medium", "bob"),
            new Issue("DEMO-3", "DEMO", "Upgrade Spring Boot to 3.5", "Done", "Low", "alice"),
            new Issue("DEMO-4", "DEMO", "Payment confirmation email not sent", "Open", "Critical", "carol"),
            new Issue("OPS-7", "OPS", "Disk usage alert on db-01", "Open", "High", "dave")));
    private final AtomicInteger sequence = new AtomicInteger(100);
    private final Environment environment;

    public JiraTools(Environment environment) {
        this.environment = environment;
    }

    @Tool(name = "search_issues", description = "Search Jira issues using JQL, for example: project = DEMO AND status = Open")
    public Map<String, Object> searchIssues(@ToolParam(description = "JQL query") String jql) {
        Matcher project = PROJECT.matcher(jql);
        Matcher status = STATUS.matcher(jql);
        String wantedProject = project.find() ? project.group(1).toUpperCase(Locale.ROOT) : null;
        String wantedStatus = status.find() ? (status.group(1) != null ? status.group(1) : status.group(2)) : null;
        boolean structured = wantedProject != null || wantedStatus != null;

        List<Issue> found = new ArrayList<>();
        for (Issue issue : issues) {
            boolean match = structured
                    ? (wantedProject == null || issue.project().equals(wantedProject))
                      && (wantedStatus == null || issue.status().equalsIgnoreCase(wantedStatus))
                    : issue.summary().toLowerCase(Locale.ROOT).contains(jql.toLowerCase(Locale.ROOT));
            if (match) {
                found.add(issue);
            }
        }
        return answer(Map.of("jql", jql, "total", found.size(), "issues", found));
    }

    @Tool(name = "get_issue", description = "Get one Jira issue by its key, for example DEMO-1")
    public Map<String, Object> getIssue(@ToolParam(description = "Issue key") String key) {
        String wanted = key.toUpperCase(Locale.ROOT);
        Issue issue = issues.stream().filter(i -> i.key().equals(wanted)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No issue " + wanted));
        return answer(issue);
    }

    @Tool(name = "create_issue", description = "Create a new Jira issue in a project")
    public Map<String, Object> createIssue(
            @ToolParam(description = "Project key, for example DEMO") String project,
            @ToolParam(description = "One-line summary") String summary,
            @ToolParam(description = "Low, Medium, High or Critical", required = false) String priority) {
        String key = project.toUpperCase(Locale.ROOT);
        Issue issue = new Issue(key + "-" + sequence.incrementAndGet(), key, summary, "Open",
                priority == null || priority.isBlank() ? "Medium" : priority, "unassigned");
        issues.add(issue);
        return answer(issue);
    }

    /** Wraps a result with the instance name, after the optional simulated latency. */
    private Map<String, Object> answer(Object result) {
        Duration latency = environment.getProperty("mock.mcp.latency", Duration.class, Duration.ZERO);
        if (!latency.isZero()) {
            try {
                Thread.sleep(latency.toMillis());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        Map<String, Object> answer = new LinkedHashMap<>();
        answer.put("servedBy", environment.getProperty("mcp-discovery.client.instance-id", "jira-mcp"));
        answer.put("result", result);
        return answer;
    }
}
