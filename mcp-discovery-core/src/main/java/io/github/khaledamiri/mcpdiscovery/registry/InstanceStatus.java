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
package io.github.khaledamiri.mcpdiscovery.registry;

/** Lifecycle status of a registered MCP server instance. */
public enum InstanceStatus {
    /** Ready to receive traffic; the only status returned by tool search. */
    UP,
    /** Registered but still initialising. */
    STARTING,
    /** Running but deliberately removed from rotation (maintenance, drain). */
    OUT_OF_SERVICE,
    /** Reported unhealthy by the instance itself. */
    DOWN
}
