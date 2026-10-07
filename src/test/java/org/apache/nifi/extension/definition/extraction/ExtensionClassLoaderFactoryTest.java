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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DefaultArtifact;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuilder;
import org.apache.nifi.dependency.DependencyGraphBuilder;
import org.apache.maven.project.DefaultProjectBuildingRequest;
import org.eclipse.aether.RepositorySystem;
import org.eclipse.aether.RepositorySystemSession;
import org.eclipse.aether.repository.RemoteRepository;
import org.eclipse.aether.resolution.ArtifactRequest;
import org.eclipse.aether.resolution.ArtifactResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.File;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExtensionClassLoaderFactoryTest {

    @Mock private Log log;
    @Mock private RepositorySystem repositorySystem;
    private final RemoteRepository remoteRepository = new RemoteRepository.Builder("central", "default", "https://repo.example.org/maven2").build();
    @Mock private ArtifactHandlerManager artifactHandlerManager;
    @Mock private DependencyGraphBuilder dependencyGraphBuilder;
    @Mock private MavenProject project;
    @Mock private ProjectBuilder projectBuilder;
    @Mock private RepositorySystemSession repositorySession;
    private Artifact artifact1;
    private Artifact artifact2;
    private Artifact artifact3;

    // Test Subject
    private ExtensionClassLoaderFactory factory;

    @BeforeEach
    void setUp() throws Exception {
        artifact1 = projectArtifact();
        artifact2 = localRepositoryDependencyArtifact();
        artifact3 = remoteRepositoryDependencyArtifact();

        when(project.getRemoteProjectRepositories()).thenReturn(Collections.singletonList(remoteRepository));
        when(repositorySystem.resolveArtifact(any(RepositorySystemSession.class), any(ArtifactRequest.class)))
                .thenAnswer(args -> resolved(args.getArgument(1, ArtifactRequest.class)));

        factory = ExtensionClassLoaderFactory
                .builder()
                .log(log)
                .project(project)
                .projectBuilder(projectBuilder)
                .dependencyGraphBuilder(dependencyGraphBuilder)
                .artifactHandlerManager(artifactHandlerManager)
                .repositorySystem(repositorySystem)
                .projectBuildingRequest(new DefaultProjectBuildingRequest())
                .repositorySession(repositorySession)
                .build();
    }

    @Test
    void createClassLoaderTest() throws Exception {
        Set<Artifact> dependencyArtifacts = new TreeSet<>();
        dependencyArtifacts.add(localRepositoryDependencyArtifact());
        dependencyArtifacts.add(remoteRepositoryDependencyArtifact());

        ExtensionClassLoader classLoader = factory.createClassLoader(dependencyArtifacts, null, artifact1);

        String[] expectedURLs = new String[]{
                "/path/to/service-api-nar",
                "/path/to/service-nar"
        };
        assertEquals(expectedURLs.length, classLoader.getURLs().length);
        List<String> expectedUrlsList = Arrays.asList(expectedURLs);
        List<String> actualUrlsList = Arrays.stream(classLoader.getURLs()).map(URL::getFile).collect(Collectors.toList());
        assertTrue(expectedUrlsList.containsAll(actualUrlsList));

        InOrder inOrder = inOrder(repositorySystem);
        for (Artifact expected : List.of(artifact2, artifact3)) {
            inOrder.verify(repositorySystem).resolveArtifact(eq(repositorySession), argThat(arg ->
                expected.getArtifactId().equals(arg.getArtifact().getArtifactId())
                        && Collections.singletonList(remoteRepository).equals(arg.getRepositories())
            ));
        }
        verifyNoMoreInteractions(repositorySystem);
    }

    private Artifact projectArtifact() {
        Artifact artifact = new DefaultArtifact(
                "org.apache.nifi",
                "processor-nar",
                "1.0.0",
                "compile",
                "nar",
                "arbitrary",
                mock(ArtifactHandler.class)
        );
        artifact.setFile(new File("/path/to/" + artifact.getArtifactId()));
        return artifact;
    }

    private Artifact localRepositoryDependencyArtifact() {
        Artifact artifact = new DefaultArtifact(
                "org.apache.nifi",
                "service-api-nar",
                "1.0.0",
                "compile",
                "nar",
                "arbitrary",
                mock(ArtifactHandler.class)
        );
        artifact.setFile(null);
        return artifact;
    }

    private Artifact remoteRepositoryDependencyArtifact() {
        Artifact artifact = new DefaultArtifact(
                "org.apache.nifi",
                "service-nar",
                "1.0.0",
                "provided",
                "nar",
                "arbitrary",
                mock(ArtifactHandler.class)
        );
        artifact.setFile(null);
        return artifact;
    }

    private ArtifactResult resolved(ArtifactRequest request) {
        ArtifactResult result = new ArtifactResult(request);
        result.setArtifact(request.getArtifact().setFile(new File("/path/to/" + request.getArtifact().getArtifactId())));
        return result;
    }
}