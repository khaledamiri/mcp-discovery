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
package io.github.khaledamiri.mcpdiscovery.client;

/** Raised when the registry cannot be reached or rejects a request. */
public class McpDiscoveryClientException extends RuntimeException {

    private static final long serialVersionUID = 1L;

    private final int statusCode;

    /**
     * @param message    error description
     * @param statusCode HTTP status, or -1 when no response was received
     * @param cause      underlying cause, may be null
     */
    public McpDiscoveryClientException(String message, int statusCode, Throwable cause) {
        super(message, cause);
        this.statusCode = statusCode;
    }

    /** @return the HTTP status returned by the registry, or -1 when no response was received */
    public int getStatusCode() {
        return statusCode;
    }
}
