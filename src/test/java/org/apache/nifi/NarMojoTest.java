/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.apache.nifi;

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class NarMojoTest {

    @Test
    void testValidateManifestEntryKeysAcceptsValidKeys() throws MojoExecutionException {
        final Map<String, String> entries = new LinkedHashMap<>();
        entries.put("vendor", "Acme Corp");
        entries.put("support-url", "https://acme.corp/support");
        entries.put("build_number", "42");
        entries.put("Tier-1", "gold");
        entries.put("ABC123", "test");

        NarMojo.validateManifestEntryKeys(entries);
    }

    @Test
    void testValidateManifestEntryKeysRejectsEmptyKey() {
        final Map<String, String> entries = new HashMap<>();
        entries.put("", "some-value");

        final MojoExecutionException exception = assertThrows(MojoExecutionException.class,
                () -> NarMojo.validateManifestEntryKeys(entries));
        assertEquals("Manifest entry key must not be null or empty", exception.getMessage());
    }

    @Test
    void testValidateManifestEntryKeysRejectsKeyWithSpaces() {
        final Map<String, String> entries = new HashMap<>();
        entries.put("my key", "value");

        final MojoExecutionException exception = assertThrows(MojoExecutionException.class,
                () -> NarMojo.validateManifestEntryKeys(entries));
        assertEquals("Manifest entry key 'my key' contains invalid characters. Keys must match [A-Za-z0-9_-]+", exception.getMessage());
    }

    @Test
    void testValidateManifestEntryKeysRejectsKeyWithColon() {
        final Map<String, String> entries = new HashMap<>();
        entries.put("key:name", "value");

        final MojoExecutionException exception = assertThrows(MojoExecutionException.class,
                () -> NarMojo.validateManifestEntryKeys(entries));
        assertEquals("Manifest entry key 'key:name' contains invalid characters. Keys must match [A-Za-z0-9_-]+", exception.getMessage());
    }

    @Test
    void testValidateManifestEntryKeysRejectsKeyWithDot() {
        final Map<String, String> entries = new HashMap<>();
        entries.put("key.name", "value");

        final MojoExecutionException exception = assertThrows(MojoExecutionException.class,
                () -> NarMojo.validateManifestEntryKeys(entries));
        assertEquals("Manifest entry key 'key.name' contains invalid characters. Keys must match [A-Za-z0-9_-]+", exception.getMessage());
    }

    @Test
    void testValidateManifestEntryKeysAcceptsEmptyMap() throws MojoExecutionException {
        NarMojo.validateManifestEntryKeys(Map.of());
    }
}
