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
package io.github.khaledamiri.mcpdiscovery.client.boot;

import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Collections;

/** Finds the address under which this machine is reachable by other machines. */
final class HostResolver {

    private HostResolver() {
    }

    /**
     * @param preferIpAddress return an IP address rather than a host name
     * @return the first non-loopback IPv4 address (or its host name), falling back to localhost
     */
    static String resolve(boolean preferIpAddress) {
        InetAddress address = firstNonLoopbackAddress();
        if (address == null) {
            try {
                address = InetAddress.getLocalHost();
            } catch (UnknownHostException e) {
                return "localhost";
            }
        }
        return preferIpAddress ? address.getHostAddress() : address.getCanonicalHostName();
    }

    private static InetAddress firstNonLoopbackAddress() {
        try {
            InetAddress fallback = null;
            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!nic.isUp() || nic.isLoopback() || nic.isVirtual()) {
                    continue;
                }
                for (InetAddress address : Collections.list(nic.getInetAddresses())) {
                    if (address instanceof Inet4Address && !address.isLoopbackAddress()
                            && !address.isLinkLocalAddress()) {
                        if (address.isSiteLocalAddress()) {
                            return address;
                        }
                        if (fallback == null) {
                            fallback = address;
                        }
                    }
                }
            }
            return fallback;
        } catch (SocketException e) {
            return null;
        }
    }
}
