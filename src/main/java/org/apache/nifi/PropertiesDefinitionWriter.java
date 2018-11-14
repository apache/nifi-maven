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

import org.apache.nifi.extension.definition.ExtensionDefinition;
import org.apache.nifi.extension.definition.Restriction;
import org.apache.nifi.extension.definition.Restrictions;
import org.apache.nifi.extension.definition.ServiceAPIDefinition;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Objects;
import java.util.Properties;

public class PropertiesDefinitionWriter {

    public void writeDefinition(final ExtensionDefinition definition, final File file) throws IOException {
        Objects.requireNonNull(definition);

        final String capabilityDescription = definition.getCapabilityDescription();

        final Properties properties = new Properties();
        if (capabilityDescription != null) {
            properties.setProperty("capability.description", capabilityDescription);
        }

        int i=0;
        for (final String tag : definition.getTags()) {
            properties.setProperty("tags." + (i++), tag);
        }

        final Restrictions restrictions = definition.getRestrictions();
        if (restrictions != null) {
            final String restrictedDescription = restrictions.getGeneralRestrictionExplanation();

            if (restrictedDescription != null) {
                properties.setProperty("restricted.description", restrictedDescription);
            }

            for (final Restriction restriction : restrictions.getRestrictions()) {
                properties.setProperty("restriction." + restriction.getIdentifier(), restriction.getExplanation());
            }
        }

        int serviceIndex = 0;
        for (final ServiceAPIDefinition apiDefinition : definition.getProvidedServiceAPIs()) {
            properties.setProperty("service.definition." + serviceIndex + ".class", apiDefinition.getServiceAPIClassName());
            properties.setProperty("service.definition." + serviceIndex + ".groupId", apiDefinition.getServiceGroupId());
            properties.setProperty("service.definition." + serviceIndex + ".artifactId", apiDefinition.getServiceArtifactId());
            properties.setProperty("service.definition." + serviceIndex + ".version", apiDefinition.getServiceVersion());

            serviceIndex++;
        }

        try (final OutputStream fos = new FileOutputStream(file)) {
            properties.store(fos, null);
        }
    }
}
