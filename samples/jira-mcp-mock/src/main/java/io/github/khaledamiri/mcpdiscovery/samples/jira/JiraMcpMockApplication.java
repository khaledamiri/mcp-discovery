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

import org.springframework.ai.tool.ToolCallbackProvider;
import org.springframework.ai.tool.method.MethodToolCallbackProvider;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

/**
 * Jira MCP server built with Spring AI. The tools are real MCP tools; only their data is fake.
 * The MCP Discovery client starter registers the server in the registry from application.yml.
 */
@SpringBootApplication
public class JiraMcpMockApplication {

    public static void main(String[] args) {
        SpringApplication.run(JiraMcpMockApplication.class, args);
    }

    /** Exposes every @Tool method of JiraTools through the Spring AI MCP server. */
    @Bean
    ToolCallbackProvider jiraToolCallbacks(JiraTools tools) {
        return MethodToolCallbackProvider.builder().toolObjects(tools).build();
    }
}
