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

import java.util.Comparator;
import java.util.Set;
import java.util.TreeSet;

/**
 * Provides the Set implementation used to order collected Service API Definitions
 */
public final class ServiceDefinitionSetProvider {
    /**
     * Orders definitions by Service API class name and artifact coordinates
     */
    private static final Comparator<ServiceAPIDefinition> CLASS_NAME_ORDER = Comparator.comparing(ServiceAPIDefinition::getServiceAPIClassName)
            .thenComparing(ServiceAPIDefinition::getServiceGroupId)
            .thenComparing(ServiceAPIDefinition::getServiceArtifactId)
            .thenComparing(ServiceAPIDefinition::getServiceVersion);

    private ServiceDefinitionSetProvider() {
    }

    /**
     * Returns a new empty Set that orders Service API Definitions when added
     */
    public static Set<ServiceAPIDefinition> newSet() {
        return new TreeSet<>(CLASS_NAME_ORDER);
    }
}
