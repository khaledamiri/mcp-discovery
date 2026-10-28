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
package io.github.khaledamiri.mcpdiscovery.server;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import io.github.khaledamiri.mcpdiscovery.testapp.TestMcpDiscoveryApplication;

@SpringBootTest(classes = TestMcpDiscoveryApplication.class, properties = "mcp-discovery.security.api-key=test-secret")
@AutoConfigureMockMvc
class ServerApiIntegrationTest {

    private static final String REGISTRATION = """
            {
              "serverName": "jira-mcp",
              "instanceId": "jira-1",
              "endpoint": "http://10.0.2.15:8080/mcp",
              "version": "1.2.0",
              "tools": [
                {"name": "search_issues", "description": "Search Jira issues using JQL",
                 "inputSchema": {"type": "object"}}
              ]
            }
            """;

    @Autowired
    private MockMvc mvc;

    @Test
    void registerHeartbeatAndSearch() throws Exception {
        mvc.perform(post("/api/v1/servers").header(ApiKeyFilter.HEADER, "test-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTRATION))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/servers/jira-mcp/instances/jira-1"))
                .andExpect(jsonPath("$.instance.status").value("UP"))
                .andExpect(jsonPath("$.renewalIntervalSeconds").value(30));

        mvc.perform(put("/api/v1/servers/jira-mcp/instances/jira-1/heartbeat")
                        .header(ApiKeyFilter.HEADER, "test-secret"))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/v1/tools/search").param("q", "recherche jira"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serverName").value("jira-mcp"))
                .andExpect(jsonPath("$[0].toolName").value("search_issues"))
                .andExpect(jsonPath("$[0].endpoints[0]").value("http://10.0.2.15:8080/mcp"));
    }

    @Test
    void writesRequireTheApiKey() throws Exception {
        mvc.perform(post("/api/v1/servers").contentType(MediaType.APPLICATION_JSON).content(REGISTRATION))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void heartbeatForUnknownInstanceReturns404() throws Exception {
        mvc.perform(put("/api/v1/servers/jira-mcp/instances/unknown/heartbeat")
                        .header(ApiKeyFilter.HEADER, "test-secret"))
                .andExpect(status().isNotFound());
    }

    @Test
    void invalidRegistrationReturns400() throws Exception {
        mvc.perform(post("/api/v1/servers").header(ApiKeyFilter.HEADER, "test-secret")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"serverName\":\"jira\",\"endpoint\":\"stdio://local\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void refreshIsRejectedWhenProbingIsDisabled() throws Exception {
        mvc.perform(post("/api/v1/servers").header(ApiKeyFilter.HEADER, "test-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTRATION))
                .andExpect(status().isCreated());

        mvc.perform(post("/api/v1/servers/jira-mcp/instances/jira-1/tools/refresh")
                        .header(ApiKeyFilter.HEADER, "test-secret"))
                .andExpect(status().isConflict());
    }

    @Test
    void servesTheDashboard() throws Exception {
        mvc.perform(get("/mcp-discovery"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(content().string(containsString("const API = '/api/v1'")))
                .andExpect(content().string(not(containsString("__API_BASE__"))));
    }

    @Test
    void listsAllInstancesWithStatus() throws Exception {
        mvc.perform(post("/api/v1/servers").header(ApiKeyFilter.HEADER, "test-secret")
                        .contentType(MediaType.APPLICATION_JSON).content(REGISTRATION))
                .andExpect(status().isCreated());

        mvc.perform(get("/api/v1/instances"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].serverName").value("jira-mcp"));

        mvc.perform(get("/api/v1/registry/status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leaseDurationSeconds").value(90))
                .andExpect(jsonPath("$.serverTime").exists());
    }
}
