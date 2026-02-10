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
package org.apache.nifi.extension.definition.extraction;

import org.apache.nifi.extension.definition.ExtensionDefinition;
import org.apache.nifi.extension.definition.ExtensionType;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtensionDefinitionFactoryTest {

    @Test
    void testDiscoverExtensionsWithNoExtensions() throws IOException {
        // Use the current class loader which won't have any NiFi extensions
        final ExtensionDefinitionFactory factory = new ExtensionDefinitionFactory(getClass().getClassLoader());

        // Verify that each extension type can be queried without error (even if empty)
        for (final ExtensionType extensionType : ExtensionType.values()) {
            final Set<ExtensionDefinition> definitions = factory.discoverExtensions(extensionType);
            assertNotNull(definitions, "Definitions should not be null for " + extensionType);
        }
    }

    @Test
    void testAllExtensionTypesHaveInterfaceMappings() throws IOException {
        final ExtensionDefinitionFactory factory = new ExtensionDefinitionFactory(getClass().getClassLoader());

        // Verify that all extension types are supported (no exceptions thrown)
        for (final ExtensionType extensionType : ExtensionType.values()) {
            // This will throw an exception if the extension type is not mapped in INTERFACE_NAMES
            final Set<ExtensionDefinition> definitions = factory.discoverExtensions(extensionType);
            assertNotNull(definitions);
        }
    }

    @Test
    void testConnectorExtensionTypeSupported() throws IOException {
        final ExtensionDefinitionFactory factory = new ExtensionDefinitionFactory(getClass().getClassLoader());

        // Verify CONNECTOR type specifically works
        final Set<ExtensionDefinition> definitions = factory.discoverExtensions(ExtensionType.CONNECTOR);
        assertNotNull(definitions);
        // With a standard classloader, there should be no Connector implementations
        assertTrue(definitions.isEmpty());
    }
}

