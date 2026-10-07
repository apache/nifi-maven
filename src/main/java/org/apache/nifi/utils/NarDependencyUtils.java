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
package org.apache.nifi.utils;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectBuildingResult;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.artifact.ArtifactType;
import org.eclipse.aether.artifact.ArtifactTypeRegistry;


public class NarDependencyUtils {
    public static final String NAR = "nar";
    public static final String COMPILE_STRING = "compile";

    /**
     * Builds the project of the NAR into the request, and gives the request a repository session in which the
     * dependencies of NARs are listed.
     *
     * @param narRequest the request to build the project with and to prepare
     * @param project the project of the NAR
     * @param projectBuilder builds the project
     * @throws ProjectBuildingException if the project cannot be built
     */
    public static void prepareNarRequest(ProjectBuildingRequest narRequest, MavenProject project, ProjectBuilder projectBuilder) throws ProjectBuildingException {

        final Artifact projectArtifact = project.getArtifact();
        final ProjectBuildingResult narResult = projectBuilder.build(projectArtifact, narRequest);
        narRequest.setProject(narResult.getProject());

        // get the artifact handler for excluding dependencies
        final ArtifactHandler narHandler = excludesDependencies(projectArtifact);
        projectArtifact.setArtifactHandler(narHandler);

        // nar artifacts by nature includes dependencies, however this prevents the
        // transitive dependencies from being resolved and listed. here we are
        // overriding the artifact type of all nars, in a copy of the session used
        // for this request only, so the dependencies can be listed. this is important
        // because nar dependencies will be used as the parent classloader for this nar
        // and seeing what dependencies are provided is critical.
        final RepositorySystemSession session = narRequest.getRepositorySession();
        final ArtifactTypeRegistry types = session.getArtifactTypeRegistry();
        final ArtifactType narType = RepositoryUtils.newArtifactType(NAR, narHandler);
        final DefaultRepositorySystemSession narSession = new DefaultRepositorySystemSession(session);
        narSession.setArtifactTypeRegistry(id -> NAR.equals(id) ? narType : types.get(id));
        narRequest.setRepositorySession(narSession);
    }

    public static void ensureSingleNarDependencyExists(MavenProject project) throws MojoExecutionException {
        boolean found = false;
        for (final Dependency dependency : project.getDependencies()) {
            if (NAR.equals(dependency.getType())) {
                if (found) {
                    throw new MojoExecutionException("Project can only have one NAR dependency.");
                }
                found = true;
            }
        }
        if (!found) {
            throw new MojoExecutionException("Project does not have any NAR dependencies.");
        }
    }

    /**
     * Creates a new ArtifactHandler for the specified Artifact that overrides the includeDependencies flag. When set, this flag prevents transitive
     * dependencies from being printed in dependencies plugin.
     *
     * @param artifact The artifact
     * @return The handler for the artifact
     */
    private static ArtifactHandler excludesDependencies(final Artifact artifact) {
        final ArtifactHandler orig = artifact.getArtifactHandler();

        return new ArtifactHandler() {
            @Override
            public String getExtension() {
                return orig.getExtension();
            }

            @Override
            public String getDirectory() {
                return orig.getDirectory();
            }

            @Override
            public String getClassifier() {
                return orig.getClassifier();
            }

            @Override
            public String getPackaging() {
                return orig.getPackaging();
            }

            // mark dependencies as excluded, so they will appear in tree listing
            @Override
            public boolean isIncludesDependencies() {
                return false;
            }

            @Override
            public String getLanguage() {
                return orig.getLanguage();
            }

            @Override
            public boolean isAddedToClasspath() {
                return orig.isAddedToClasspath();
            }
        };
    }
}
