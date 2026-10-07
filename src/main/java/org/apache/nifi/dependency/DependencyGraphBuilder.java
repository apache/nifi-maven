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
package org.apache.nifi.dependency;

import org.apache.maven.RepositoryUtils;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.resolver.filter.ArtifactFilter;
import org.apache.maven.project.DefaultDependencyResolutionRequest;
import org.apache.maven.project.DependencyResolutionException;
import org.apache.maven.project.DependencyResolutionRequest;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingRequest;
import org.apache.maven.project.ProjectDependenciesResolver;
import org.eclipse.aether.DefaultRepositorySystemSession;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.util.graph.manager.DependencyManagerUtils;
import org.eclipse.aether.util.graph.transformer.ConflictResolver;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Builds the dependency graph of a project through Maven's {@link ProjectDependenciesResolver}, the way
 * maven-dependency-tree's {@code DependencyGraphBuilder} and {@code DependencyCollectorBuilder} did, which this
 * replaces now that maven-dependency-tree is to be retired. Only POMs are downloaded, no artifact files.
 */
@Named
@Singleton
public class DependencyGraphBuilder {

    private final ProjectDependenciesResolver projectDependenciesResolver;

    @Inject
    public DependencyGraphBuilder(final ProjectDependenciesResolver projectDependenciesResolver) {
        this.projectDependenciesResolver = Objects.requireNonNull(projectDependenciesResolver, "projectDependenciesResolver");
    }

    /**
     * Builds the graph of the project of the request after conflict resolution, as Maven resolves it for the build.
     *
     * @param buildingRequest the request whose project and repository session to use
     * @param filter the artifacts to keep with their subtrees, or {@code null} for all
     * @return the root node, which stands for the project
     * @throws DependencyGraphException if the dependencies cannot be collected
     */
    public DependencyNode buildDependencyGraph(final ProjectBuildingRequest buildingRequest, final ArtifactFilter filter) throws DependencyGraphException {
        return build(buildingRequest.getProject(), buildingRequest.getRepositorySession(), filter);
    }

    /**
     * Builds the verbose graph of the project of the request: a dependency that loses a version conflict stays in it,
     * without its own dependencies.
     *
     * @param buildingRequest the request whose project and repository session to use
     * @param filter the artifacts to keep with their subtrees, or {@code null} for all
     * @return the root node, which stands for the project
     * @throws DependencyGraphException if the dependencies cannot be collected
     */
    public DependencyNode collectDependencyGraph(final ProjectBuildingRequest buildingRequest, final ArtifactFilter filter) throws DependencyGraphException {
        final DefaultRepositorySystemSession session = new DefaultRepositorySystemSession(buildingRequest.getRepositorySession());
        session.setConfigProperty(ConflictResolver.CONFIG_PROP_VERBOSE, true);
        session.setConfigProperty(DependencyManagerUtils.CONFIG_PROP_VERBOSE, true);
        return build(buildingRequest.getProject(), session, filter);
    }

    private DependencyNode build(final MavenProject project, final RepositorySystemSession session, final ArtifactFilter filter) throws DependencyGraphException {
        final DependencyResolutionRequest request = new DefaultDependencyResolutionRequest(project, session);
        // only download the POMs, not the artifacts
        request.setResolutionFilter((node, parents) -> false);
        try {
            return toNode(null, projectDependenciesResolver.resolve(request).getDependencyGraph(), project.getArtifact(), filter);
        } catch (final DependencyResolutionException e) {
            throw new DependencyGraphException("Could not resolve following dependencies: " + e.getResult().getUnresolvedDependencies(), e);
        }
    }

    private static DependencyNode toNode(final DependencyNode parent, final org.eclipse.aether.graph.DependencyNode node, final Artifact artifact, final ArtifactFilter filter) {
        final DependencyNode current = new DependencyNode(parent, artifact);
        final List<DependencyNode> children = new ArrayList<>(node.getChildren().size());
        for (final org.eclipse.aether.graph.DependencyNode child : node.getChildren()) {
            final Artifact childArtifact = toArtifact(child.getDependency());
            if (filter == null || filter.include(childArtifact)) {
                children.add(toNode(current, child, childArtifact, filter));
            }
        }
        current.setChildren(children);
        return current;
    }

    private static Artifact toArtifact(final org.eclipse.aether.graph.Dependency dependency) {
        final Artifact artifact = RepositoryUtils.toArtifact(dependency.getArtifact());
        artifact.setScope(dependency.getScope());
        artifact.setOptional(dependency.isOptional());
        return artifact;
    }
}
