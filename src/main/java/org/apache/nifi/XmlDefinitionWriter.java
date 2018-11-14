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
import org.apache.nifi.extension.definition.ExtensionType;
import org.apache.nifi.extension.definition.Restriction;
import org.apache.nifi.extension.definition.Restrictions;
import org.apache.nifi.extension.definition.ServiceAPIDefinition;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamWriter;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

public class XmlDefinitionWriter {

    public void writeDefinition(final Collection<ExtensionDefinition> definitions, final File file) throws IOException {
        final Map<ExtensionType, List<ExtensionDefinition>> definitionMap = definitions.stream()
            .collect(Collectors.groupingBy(ExtensionDefinition::getExtensionType));

        writeDefinition(definitionMap, file);
    }

    public void writeDefinition(final Map<ExtensionType, ? extends Collection<ExtensionDefinition>> definitions, final File file) throws IOException {
        Objects.requireNonNull(definitions);
        Objects.requireNonNull(file);

        if (definitions.isEmpty()) {
            return;
        }

        try (final OutputStream fileOut = new FileOutputStream(file)) {
             final XMLStreamWriter writer = XMLOutputFactory.newInstance().createXMLStreamWriter(fileOut, "UTF-8");
             try {
                 writer.writeStartElement("extensions");

                 writer.writeStartElement("processors");
                 writeDefinitions(ExtensionType.PROCESSOR, definitions.get(ExtensionType.PROCESSOR), writer);
                 writer.writeEndElement();

                 writer.writeStartElement("controllerServices");
                 writeDefinitions(ExtensionType.CONTROLLER_SERVICE, definitions.get(ExtensionType.CONTROLLER_SERVICE), writer);
                 writer.writeEndElement();

                 writer.writeStartElement("reportingTasks");
                 writeDefinitions(ExtensionType.REPORTING_TASK, definitions.get(ExtensionType.REPORTING_TASK), writer);
                 writer.writeEndElement();

                 writer.writeEndElement();
             } finally {
                 writer.close();
             }
        } catch (XMLStreamException e) {
            throw new IOException(e);
        }
    }

    private void writeDefinitions(final ExtensionType extensionType, final Collection<ExtensionDefinition> definitions, final XMLStreamWriter writer) throws XMLStreamException {
        if (definitions == null) {
            return;
        }

        final String tagName;
        switch (extensionType) {
            case PROCESSOR:
                tagName = "processor";
                break;
            case CONTROLLER_SERVICE:
                tagName = "controllerService";
                break;
            case REPORTING_TASK:
                tagName = "reportingTask";
                break;
            default:
                throw new AssertionError("Encountered unknown Extension Type " + extensionType);
        }

        for (final ExtensionDefinition definition : definitions) {
            writer.writeStartElement(tagName);

            writeTextElement(writer, "name", definition.getExtensionName());
            writeTextElement(writer, "description", definition.getCapabilityDescription());

            writer.writeStartElement("tags");
            for (final String tag : definition.getTags()) {
                writeTextElement(writer, "tag", tag);
            }
            writer.writeEndElement();

            final Restrictions restrictions = definition.getRestrictions();
            if (restrictions == null) {
                writer.writeEmptyElement("restrictions");
            } else {
                writer.writeStartElement("restrictions");

                writeTextElement(writer, "explanation", restrictions.getGeneralRestrictionExplanation());
                final Set<Restriction> specificRestrictions = restrictions.getRestrictions();
                for (final Restriction restriction : specificRestrictions) {
                    writer.writeStartElement("restriction");
                    writeTextElement(writer, "identifier", restriction.getIdentifier());
                    writeTextElement(writer, "explanation", restriction.getExplanation());
                    writer.writeEndElement();
                }

                writer.writeEndElement();
            }

            if (extensionType == ExtensionType.CONTROLLER_SERVICE) {
                writer.writeStartElement("providedServiceAPIs");

                final Set<ServiceAPIDefinition> serviceDefinitions = definition.getProvidedServiceAPIs();
                if (serviceDefinitions != null) {
                    for (final ServiceAPIDefinition serviceDefinition : serviceDefinitions) {
                        writer.writeStartElement("service");

                        writeTextElement(writer, "className", serviceDefinition.getServiceAPIClassName());
                        writeTextElement(writer, "groupId", serviceDefinition.getServiceGroupId());
                        writeTextElement(writer, "artifactId", serviceDefinition.getServiceArtifactId());
                        writeTextElement(writer, "version", serviceDefinition.getServiceVersion());

                        writer.writeEndElement();
                    }
                }

                writer.writeEndElement();
            }

            writer.writeEndElement();
        }
    }

    private void writeTextElement(final XMLStreamWriter writer, final String tagName, final String text) throws XMLStreamException {
        writer.writeStartElement(tagName);

        if (text != null) {
            writer.writeCharacters(text);
        }

        writer.writeEndElement();
    }
}
