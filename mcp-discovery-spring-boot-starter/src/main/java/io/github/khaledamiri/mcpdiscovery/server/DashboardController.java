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

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.ClassPathResource;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Serves the web dashboard, a single self-contained page that reads the registry REST API.
 * The path is {@code mcp-discovery.dashboard.path} ({@code /mcp-discovery} by default).
 */
@RestController
public class DashboardController {

    private static final String PAGE = "mcp-discovery/dashboard.html";
    private static final Pattern SAFE_CONTEXT_PATH = Pattern.compile("[A-Za-z0-9/_.~-]*");
    private static final String CSP = "default-src 'none'; script-src 'unsafe-inline'; style-src 'unsafe-inline'; "
            + "connect-src 'self'; img-src 'self' data:; form-action 'self'; frame-ancestors 'none'; base-uri 'none'";

    private final String html;

    public DashboardController() {
        try (InputStream in = new ClassPathResource(PAGE).getInputStream()) {
            this.html = new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Cannot load " + PAGE, e);
        }
    }

    @GetMapping(value = "${mcp-discovery.dashboard.path:/mcp-discovery}", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> dashboard(HttpServletRequest request) {
        String contextPath = request.getContextPath();
        if (contextPath == null || !SAFE_CONTEXT_PATH.matcher(contextPath).matches()) {
            contextPath = "";
        }
        return ResponseEntity.ok()
                .cacheControl(CacheControl.noCache())
                .header("Content-Security-Policy", CSP)
                .header("X-Content-Type-Options", "nosniff")
                .header("Referrer-Policy", "no-referrer")
                .body(html.replace("__API_BASE__", contextPath + "/api/v1"));
    }
}
