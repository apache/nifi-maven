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
package org.apache.nifi.extension.definition;

import org.apache.nifi.extension.definition.extraction.StandardServiceAPIDefinition;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceDefinitionSetProviderTest {

    private static final String GROUP_ID = "org.apache.nifi";
    private static final String ARTIFACT_ID = "nifi-standard-services-api-nar";
    private static final String VERSION = "2.0.0";

    private static final String OTHER_GROUP_ID = "com.example";
    private static final String OTHER_ARTIFACT_ID = "example-services-api-nar";
    private static final String OTHER_VERSION = "1.0.0";

    private static final String SSL_CONTEXT_PROVIDER = "org.apache.nifi.ssl.SSLContextProvider";
    private static final String SELF_CONTAINED_USER_SERVICE = "org.apache.nifi.user.SelfContainedUserService";
    private static final String USER_SERVICE = "org.apache.nifi.user.UserService";

    @Test
    void testNewSetIsEmpty() {
        final Set<ServiceAPIDefinition> serviceApis = ServiceDefinitionSetProvider.newSet();

        assertTrue(serviceApis.isEmpty());
    }

    @Test
    void testNewSetReturnsIndependentSets() {
        final Set<ServiceAPIDefinition> firstSet = ServiceDefinitionSetProvider.newSet();
        final Set<ServiceAPIDefinition> secondSet = ServiceDefinitionSetProvider.newSet();
        firstSet.add(definition(SSL_CONTEXT_PROVIDER));

        assertNotSame(firstSet, secondSet);
        assertTrue(secondSet.isEmpty());
    }

    @Test
    void testDefinitionsOrderedByClassName() {
        final Set<ServiceAPIDefinition> serviceApis = ServiceDefinitionSetProvider.newSet();
        serviceApis.add(definition(USER_SERVICE));
        serviceApis.add(definition(SELF_CONTAINED_USER_SERVICE));
        serviceApis.add(definition(SSL_CONTEXT_PROVIDER));

        final List<String> expected = List.of(SSL_CONTEXT_PROVIDER, SELF_CONTAINED_USER_SERVICE, USER_SERVICE);
        final List<String> ordered = serviceApis.stream()
                .map(ServiceAPIDefinition::getServiceAPIClassName)
                .toList();
        assertEquals(expected, ordered);
    }

    @Test
    void testDefinitionsSharingClassNameOrderedByArtifactCoordinates() {
        final Set<ServiceAPIDefinition> serviceApis = ServiceDefinitionSetProvider.newSet();
        serviceApis.add(definition(SSL_CONTEXT_PROVIDER, GROUP_ID, ARTIFACT_ID, VERSION));
        serviceApis.add(definition(SSL_CONTEXT_PROVIDER, GROUP_ID, ARTIFACT_ID, OTHER_VERSION));
        serviceApis.add(definition(SSL_CONTEXT_PROVIDER, OTHER_GROUP_ID, OTHER_ARTIFACT_ID, VERSION));

        // Definitions differing only in artifact coordinates must all be retained, rather than being treated as
        // duplicates of the definition that shares their class name.
        final List<String> expected = List.of(
                coordinates(OTHER_GROUP_ID, OTHER_ARTIFACT_ID, VERSION),
                coordinates(GROUP_ID, ARTIFACT_ID, OTHER_VERSION),
                coordinates(GROUP_ID, ARTIFACT_ID, VERSION)
        );
        final List<String> ordered = serviceApis.stream()
                .map(definition -> coordinates(definition.getServiceGroupId(), definition.getServiceArtifactId(), definition.getServiceVersion()))
                .toList();
        assertEquals(expected, ordered);
    }

    @Test
    void testEqualDefinitionsDeduplicated() {
        final Set<ServiceAPIDefinition> serviceApis = ServiceDefinitionSetProvider.newSet();
        serviceApis.add(definition(SSL_CONTEXT_PROVIDER));
        serviceApis.add(definition(SSL_CONTEXT_PROVIDER));

        assertEquals(1, serviceApis.size());
    }

    private ServiceAPIDefinition definition(final String serviceApiClassName) {
        return definition(serviceApiClassName, GROUP_ID, ARTIFACT_ID, VERSION);
    }

    private ServiceAPIDefinition definition(final String serviceApiClassName, final String groupId, final String artifactId, final String version) {
        return new StandardServiceAPIDefinition(serviceApiClassName, groupId, artifactId, version);
    }

    private String coordinates(final String groupId, final String artifactId, final String version) {
        return "%s:%s:%s".formatted(groupId, artifactId, version);
    }
}
