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

import java.util.Set;

public interface ExtensionDefinition {
    /**
     * @return the extension's capability description
     */
    String getCapabilityDescription();

    /**
     * @return the set of Tags associated with the extension
     */
    Set<String> getTags();

    /**
     * @return the Restrictions that are placed on the Extension
     */
    Restrictions getRestrictions();

    /**
     * @return the type of Extension
     */
    ExtensionType getExtensionType();

    /**
     * @return the Set of all Services API's that this extension provides. Note that this will be an empty set for
     * any Extension for which {@link #getExtensionType()} is not {@link ExtensionType#CONTROLLER_SERVICE}.
     */
    Set<ServiceAPIDefinition> getProvidedServiceAPIs();

    /**
     * @return the name of the Extension
     */
    String getExtensionName();
}
