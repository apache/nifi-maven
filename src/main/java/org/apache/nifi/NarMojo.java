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

import org.apache.maven.archiver.MavenArchiveConfiguration;
import org.apache.maven.archiver.MavenArchiver;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.DependencyResolutionRequiredException;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.artifact.handler.manager.ArtifactHandlerManager;
import org.apache.maven.artifact.installer.ArtifactInstaller;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.repository.ArtifactRepositoryFactory;
import org.apache.maven.artifact.resolver.ArtifactCollector;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.dependency.utils.DependencyStatusSets;
import org.apache.maven.plugin.dependency.utils.DependencyUtil;
import org.apache.maven.plugin.dependency.utils.filters.DestFileFilter;
import org.apache.maven.plugin.dependency.utils.resolvers.ArtifactsResolver;
import org.apache.maven.plugin.dependency.utils.resolvers.DefaultArtifactsResolver;
import org.apache.maven.plugin.dependency.utils.translators.ArtifactTranslator;
import org.apache.maven.plugin.dependency.utils.translators.ClassifierTypeTranslator;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.MavenProjectHelper;
import org.apache.maven.project.ProjectBuilder;
import org.apache.maven.shared.artifact.filter.collection.ArtifactFilterException;
import org.apache.maven.shared.artifact.filter.collection.ArtifactIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ArtifactsFilter;
import org.apache.maven.shared.artifact.filter.collection.ClassifierFilter;
import org.apache.maven.shared.artifact.filter.collection.FilterArtifacts;
import org.apache.maven.shared.artifact.filter.collection.GroupIdFilter;
import org.apache.maven.shared.artifact.filter.collection.ProjectTransitivityFilter;
import org.apache.maven.shared.artifact.filter.collection.ScopeFilter;
import org.apache.maven.shared.artifact.filter.collection.TypeFilter;
import org.apache.maven.shared.dependency.tree.DependencyTreeBuilder;
import org.apache.nifi.extension.definition.ExtensionDefinition;
import org.apache.nifi.extension.definition.ExtensionType;
import org.apache.nifi.extension.definition.ServiceAPIDefinition;
import org.apache.nifi.extension.definition.extraction.ExtensionClassLoader;
import org.apache.nifi.extension.definition.extraction.ExtensionClassLoaderFactory;
import org.apache.nifi.extension.definition.extraction.ExtensionDefinitionFactory;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.jar.ManifestException;
import org.codehaus.plexus.archiver.manager.ArchiverManager;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;
import org.eclipse.aether.RepositorySystemSession;

import javax.xml.stream.XMLOutputFactory;
import javax.xml.stream.XMLStreamWriter;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Packages the current project as an Apache NiFi Archive (NAR).
 *
 * The following code is derived from maven-dependencies-plugin and
 * maven-jar-plugin. The functionality of CopyDependenciesMojo and JarMojo was
 * simplified to the use case of NarMojo.
 *
 */
@Mojo(name = "nar", defaultPhase = LifecyclePhase.PACKAGE, threadSafe = true, requiresDependencyResolution = ResolutionScope.RUNTIME)
public class NarMojo extends AbstractMojo {
    private static final String SERVICES_DIRECTORY = "META-INF/services/";
    private static final String DOCUMENTATION_WRITER_CLASS_NAME = "org.apache.nifi.documentation.xml.XmlDocumentationWriter";

    private static final String[] DEFAULT_EXCLUDES = new String[]{"**/package.html"};
    private static final String[] DEFAULT_INCLUDES = new String[]{"**/**"};

    private static final String BUILD_TIMESTAMP_FORMAT = "yyyy-MM-dd'T'HH:mm:ss'Z'";

    /**
     * POM
     *
     */
    @Parameter(defaultValue = "${project}", readonly = true, required = true)
    protected MavenProject project;

    @Parameter(defaultValue = "${session}", readonly = true, required = true)
    protected MavenSession session;

    /**
     * List of files to include. Specified as fileset patterns.
     */
    @Parameter(property = "includes")
    protected String[] includes;
    /**
     * List of files to exclude. Specified as fileset patterns.
     */
    @Parameter(property = "excludes")
    protected String[] excludes;
    /**
     * Name of the generated NAR.
     *
     */
    @Parameter(alias = "narName", property = "nar.finalName", defaultValue = "${project.build.finalName}", required = true)
    protected String finalName;

    /**
     * The Jar archiver.
     *
     * \@\component role="org.codehaus.plexus.archiver.Archiver" roleHint="jar"
     */
    @Component(role = org.codehaus.plexus.archiver.Archiver.class, hint = "jar")
    private JarArchiver jarArchiver;
    /**
     * The archive configuration to use.
     *
     * See <a
     * href="http://maven.apache.org/shared/maven-archiver/index.html">the
     * documentation for Maven Archiver</a>.
     *
     */
    @Parameter(property = "archive")
    protected final MavenArchiveConfiguration archive = new MavenArchiveConfiguration();
    /**
     * Path to the default MANIFEST file to use. It will be used if
     * <code>useDefaultManifestFile</code> is set to <code>true</code>.
     *
     */
    @Parameter(property = "defaultManifestFiles", defaultValue = "${project.build.outputDirectory}/META-INF/MANIFEST.MF", readonly = true, required = true)
    protected File defaultManifestFile;

    /**
     * Set this to <code>true</code> to enable the use of the
     * <code>defaultManifestFile</code>.
     *
     * @since 2.2
     */
    @Parameter(property = "nar.useDefaultManifestFile", defaultValue = "false")
    protected boolean useDefaultManifestFile;

    @Component
    protected MavenProjectHelper projectHelper;

    /**
     * Whether creating the archive should be forced.
     *
     */
    @Parameter(property = "nar.forceCreation", defaultValue = "false")
    protected boolean forceCreation;

    /**
     * Classifier to add to the artifact generated. If given, the artifact will
     * be an attachment instead.
     *
     */
    @Parameter(property = "classifier")
    protected String classifier;

    @Component
    protected ArtifactInstaller installer;

    @Component
    protected ArtifactRepositoryFactory repositoryFactory;

    /**
     * This only applies if the classifier parameter is used.
     *
     */
    @Parameter(property = "mdep.failOnMissingClassifierArtifact", defaultValue = "true", required = false)
    protected boolean failOnMissingClassifierArtifact = true;

    /**
     * Comma Separated list of Types to include. Empty String indicates include
     * everything (default).
     *
     */
    @Parameter(property = "includeTypes", required = false)
    protected String includeTypes;

    /**
     * Comma Separated list of Types to exclude. Empty String indicates don't
     * exclude anything (default).
     *
     */
    @Parameter(property = "excludeTypes", required = false)
    protected String excludeTypes;

    /**
     * Scope to include. An Empty string indicates all scopes (default).
     *
     */
    @Parameter(property = "includeScope", required = false)
    protected String includeScope;

    /**
     * Scope to exclude. An Empty string indicates no scopes (default).
     *
     */
    @Parameter(property = "excludeScope", required = false)
    protected String excludeScope;

    /**
     * Comma Separated list of Classifiers to include. Empty String indicates
     * include everything (default).
     *
     */
    @Parameter(property = "includeClassifiers", required = false)
    protected String includeClassifiers;

    /**
     * Comma Separated list of Classifiers to exclude. Empty String indicates
     * don't exclude anything (default).
     *
     */
    @Parameter(property = "excludeClassifiers", required = false)
    protected String excludeClassifiers;

    /**
     * Specify classifier to look for. Example: sources
     *
     */
    @Parameter(property = "classifier", required = false)
    protected String copyDepClassifier;

    /**
     * Specify type to look for when constructing artifact based on classifier.
     * Example: java-source,jar,war, nar
     *
     */
    @Parameter(property = "type", required = false, defaultValue = "nar")
    protected String type;

    /**
     * Comma separated list of Artifact names too exclude.
     *
     */
    @Parameter(property = "excludeArtifacts", required = false)
    protected String excludeArtifactIds;

    /**
     * Comma separated list of Artifact names to include.
     *
     */
    @Parameter(property = "includeArtifacts", required = false)
    protected String includeArtifactIds;

    /**
     * Comma separated list of GroupId Names to exclude.
     *
     */
    @Parameter(property = "excludeArtifacts", required = false)
    protected String excludeGroupIds;

    /**
     * Comma separated list of GroupIds to include.
     *
     */
    @Parameter(property = "includeGroupIds", required = false)
    protected String includeGroupIds;

    /**
     * Directory to store flag files
     *
     */
    @Parameter(property = "markersDirectory", required = false, defaultValue = "${project.build.directory}/dependency-maven-plugin-markers")
    protected File markersDirectory;

    /**
     * Overwrite release artifacts
     *
     */
    @Parameter(property = "overWriteReleases", required = false)
    protected boolean overWriteReleases;

    /**
     * Overwrite snapshot artifacts
     *
     */
    @Parameter(property = "overWriteSnapshots", required = false)
    protected boolean overWriteSnapshots;

    /**
     * Overwrite artifacts that don't exist or are older than the source.
     *
     */
    @Parameter(property = "overWriteIfNewer", required = false, defaultValue = "true")
    protected boolean overWriteIfNewer;

    @Parameter(property = "projectBuildDirectory", required = false, defaultValue = "${project.build.directory}")
    protected File projectBuildDirectory;

    /**
     * Used to look up Artifacts in the remote repository.
     */
    @Component
    protected ArtifactFactory factory;

    /**
     * Used to look up Artifacts in the remote repository.
     *
     */
    @Component
    protected ArtifactResolver resolver;

    /**
     * Artifact collector, needed to resolve dependencies.
     *
     */
    @Component(role = org.apache.maven.artifact.resolver.ArtifactCollector.class)
    protected ArtifactCollector artifactCollector;

    @Component(role = org.apache.maven.artifact.metadata.ArtifactMetadataSource.class)
    protected ArtifactMetadataSource artifactMetadataSource;

    /**
     * Location of the local repository.
     *
     */
    @Parameter(property = "localRepository", required = true, readonly = true)
    protected ArtifactRepository local;

    /**
     * List of Remote Repositories used by the resolver
     *
     */
    @Parameter(property = "project.remoteArtifactRepositories", required = true, readonly = true)
    protected List remoteRepos;

    /**
     * To look up Archiver/UnArchiver implementations
     *
     */
    @Component
    protected ArchiverManager archiverManager;

    /**
     * Contains the full list of projects in the reactor.
     *
     */
    @Parameter(property = "reactorProjects", required = true, readonly = true)
    protected List reactorProjects;

    /**
     * If the plugin should be silent.
     *
     */
    @Parameter(property = "silent", required = false, defaultValue = "false")
    public boolean silent;

    /**
     * The dependency tree builder to use for verbose output.
     */
    @Component
    private DependencyTreeBuilder dependencyTreeBuilder;

    /**
     * *
     * The {@link ArtifactHandlerManager} into which any extension {@link ArtifactHandler} instances should have been injected when the extensions were loaded.
     */
    @Component
    private ArtifactHandlerManager artifactHandlerManager;


    /**
     * Output absolute filename for resolved artifacts
     *
     */
    @Parameter(property = "outputAbsoluteArtifactFilename", defaultValue = "false", required = false)
    protected boolean outputAbsoluteArtifactFilename;

    /* The values to use for populating the Nar-Group, Nar-Id, and Nar-Version in the MANIFEST file. By default
     * these values will be set to the standard Maven project equivalents, but they may be overridden through properties.
     *
     * For example if the pom.xml for the nifi-test-nar contained the following:
     *
     *    <groupId>org.apache.nifi</groupId>
     *    <artifactId>nifi-test-nar</artifactId>
     *    <version>1.0</version>
     *
     *    <properties>
     *       <narGroup>org.apache.nifi.overridden</narGroup>
     *       <narId>nifi-overridden-test-nar</narId>
     *       <narVersion>2.0</narVersion>
     *   </properties>
     *
     * It would produce a MANIFEST with:
     *
     *   Nar-Id: nifi-overridden-test-nar
     *   Nar-Group: org.apache.nifi.overridden
     *   Nar-Version: 2.0
     *
    */

    @Parameter(property = "narGroup", defaultValue = "${project.groupId}", required = true)
    protected String narGroup;

    @Parameter(property = "narId", defaultValue = "${project.artifactId}", required = true)
    protected String narId;

    @Parameter(property = "narVersion", defaultValue = "${project.version}", required = true)
    protected String narVersion;

    @Parameter(property = "narDependencyGroup", required = false)
    protected String narDependencyGroup = null;

    @Parameter(property = "narDependencyId", required = false)
    protected String narDependencyId = null;

    @Parameter(property = "narDependencyVersion", required = false)
    protected String narDependencyVersion = null;


    /**
     * Build info to be populated in MANIFEST.
     */

    @Parameter(property = "buildTag", defaultValue = "${project.scm.tag}", required = false)
    protected String buildTag;

    @Parameter(property = "buildBranch", defaultValue = "${buildBranch}", required = false)
    protected String buildBranch;

    @Parameter(property = "buildRevision", defaultValue = "${buildRevision}", required = false)
    protected String buildRevision;

    /**
     * Allows a NAR to specify if it's resources should be cloned when a component that depends on this NAR
     * is performing class loader isolation.
     */
    @Parameter(property = "cloneDuringInstanceClassLoading", defaultValue = "false", required = false)
    protected boolean cloneDuringInstanceClassLoading;


    /**
     * The {@link RepositorySystemSession} used for obtaining the local and remote artifact repositories.
     */
    @Parameter(defaultValue = "${repositorySystemSession}", readonly = true)
    private RepositorySystemSession repoSession;


    /**
     * The {@link ProjectBuilder} used to generate the {@link MavenProject} for the nar artifact the dependency tree is being generated for.
     */
    @Component
    private ProjectBuilder projectBuilder;



    @Override
    public void execute() throws MojoExecutionException {
        copyDependencies();

        try {
            generateDocumentation();
        } catch (final Exception e) {
            getLog().warn("Could not generate extensions' documentation", e);
        }

        makeNar();
    }

    private File getExtensionsDocumentationFile() {
        final File directory = new File(projectBuildDirectory, "META-INF");
        return new File(directory, "extension-docs.xml");
    }

    private void generateDocumentation() throws MojoExecutionException {
        // Create the ClassLoader for the NAR
        final ExtensionClassLoaderFactory classLoaderFactory = createClassLoaderFactory();

        final ExtensionClassLoader extensionClassLoader;
        try {
            extensionClassLoader = classLoaderFactory.createExtensionClassLoader();
        } catch (final Exception e) {
            if (getLog().isDebugEnabled()) {
                getLog().debug("Unable to create a ClassLoader for documenting extensions. If this NAR contains any NiFi Extensions, those extensions will not be documented.", e);
            } else {
                getLog().warn("Unable to create a ClassLoader for documenting extensions. If this NAR contains any NiFi Extensions, those extensions will not be documented. " +
                    "Enable mvn DEBUG output for more information (mvn -X).");
            }

            return;
        }


        final File docsFile = getExtensionsDocumentationFile();
        createDirectory(docsFile.getParentFile());

        try (final OutputStream out = new FileOutputStream(docsFile)) {

            final XMLStreamWriter xmlWriter = XMLOutputFactory.newInstance().createXMLStreamWriter(out, "UTF-8");
            try {
                xmlWriter.writeStartElement("extensions");

                final String nifiApiVersion = extensionClassLoader.getNiFiApiVersion();
                xmlWriter.writeStartElement("nifiApiVersion");
                xmlWriter.writeCharacters(nifiApiVersion);
                xmlWriter.writeEndElement();

                final Class<?> docWriterClass;
                try {
                    docWriterClass = Class.forName(DOCUMENTATION_WRITER_CLASS_NAME, false, extensionClassLoader);
                } catch (ClassNotFoundException e) {
                    getLog().warn("Cannot locate class " + DOCUMENTATION_WRITER_CLASS_NAME + ", so no documentation will be generated for the extensions in this NAR");
                    return;
                }

                getLog().debug("Creating Extension Definition Factory for NiFi API version " + nifiApiVersion);

                final ExtensionDefinitionFactory extensionDefinitionFactory = new ExtensionDefinitionFactory(extensionClassLoader);

                final ClassLoader currentContextClassLoader = Thread.currentThread().getContextClassLoader();
                try {
                    Thread.currentThread().setContextClassLoader(extensionClassLoader);

                    final Set<ExtensionDefinition> processorDefinitions = extensionDefinitionFactory.discoverExtensions(ExtensionType.PROCESSOR);
                    writeDocumentation(processorDefinitions, extensionClassLoader, docWriterClass, xmlWriter);

                    final Set<ExtensionDefinition> controllerServiceDefinitions = extensionDefinitionFactory.discoverExtensions(ExtensionType.CONTROLLER_SERVICE);
                    writeDocumentation(controllerServiceDefinitions, extensionClassLoader, docWriterClass, xmlWriter);

                    final Set<ExtensionDefinition> reportingTaskDefinitions = extensionDefinitionFactory.discoverExtensions(ExtensionType.REPORTING_TASK);
                    writeDocumentation(reportingTaskDefinitions, extensionClassLoader, docWriterClass, xmlWriter);
                } finally {
                    if (currentContextClassLoader != null) {
                        Thread.currentThread().setContextClassLoader(currentContextClassLoader);
                    }
                }

                xmlWriter.writeEndElement();
            } finally {
                xmlWriter.close();
            }
        } catch (final Exception ioe) {
            throw new MojoExecutionException("Failed to create Extension Documentation", ioe);
        }
    }

    private void writeDocumentation(final Set<ExtensionDefinition> extensionDefinitions, final ExtensionClassLoader classLoader,
                                    final Class<?> docWriterClass, final XMLStreamWriter xmlWriter)
                                    throws InvocationTargetException, NoSuchMethodException, ClassNotFoundException, InstantiationException, IllegalAccessException {

        for (final ExtensionDefinition definition : extensionDefinitions) {
            writeDocumentation(definition, classLoader, docWriterClass, xmlWriter);
        }
    }

    private void writeDocumentation(final ExtensionDefinition extensionDefinition, final ExtensionClassLoader classLoader,
                                    final Class<?> docWriterClass, final XMLStreamWriter xmlWriter)
                                    throws NoSuchMethodException, IllegalAccessException, InvocationTargetException, InstantiationException, ClassNotFoundException {

        getLog().debug("Generating documentation for " + extensionDefinition.getExtensionName() + " using ClassLoader:\n" + classLoader.toTree());
        final Object docWriter = docWriterClass.getConstructor(XMLStreamWriter.class).newInstance(xmlWriter);
        final Class<?> configurableComponentClass = Class.forName("org.apache.nifi.components.ConfigurableComponent", false, classLoader);

        final Class<?> extensionClass = Class.forName(extensionDefinition.getExtensionName(), false, classLoader);
        final Object extensionInstance = extensionClass.newInstance();

        final Set<ServiceAPIDefinition> serviceDefinitions = extensionDefinition.getProvidedServiceAPIs();

        if (serviceDefinitions == null || serviceDefinitions.isEmpty()) {
            final Method writeMethod = docWriterClass.getMethod("write", configurableComponentClass);
            writeMethod.invoke(docWriter, extensionInstance);
        } else {
            final Class<?> providedServiceApiClass = Class.forName("org.apache.nifi.documentation.StandardProvidedServiceAPI", false, classLoader);
            final Constructor<?> ctr = providedServiceApiClass.getConstructor(String.class, String.class, String.class, String.class);

            final List<Object> providedServices = new ArrayList<>();

            for (final ServiceAPIDefinition definition : serviceDefinitions) {
                final Object serviceApi = ctr.newInstance(definition.getServiceAPIClassName(), definition.getServiceGroupId(), definition.getServiceArtifactId(), definition.getServiceVersion());
                providedServices.add(serviceApi);
            }

            final Method writeMethod = docWriterClass.getMethod("write", configurableComponentClass, Collection.class);
            writeMethod.invoke(docWriter, extensionInstance, providedServices);
        }
    }

    private ExtensionClassLoaderFactory createClassLoaderFactory() {
        return new ExtensionClassLoaderFactory.Builder()
            .artifactResolver(resolver)
            .dependencyTreeBuilder(dependencyTreeBuilder)
            .localRepository(local)
            .log(getLog())
            .project(project)
            .projectBuilder(projectBuilder)
            .remoteRepositories(remoteRepos)
            .repositorySession(repoSession)
            .artifactHandlerManager(artifactHandlerManager)
            .build();
    }


    private void createDirectory(final File file) throws MojoExecutionException {
        if (!file.exists()) {
            try {
                Files.createDirectories(file.toPath());
            } catch (IOException e) {
                throw new MojoExecutionException("Could not create directory " + file, e);
            }
        }
    }


    private void copyDependencies() throws MojoExecutionException {
        DependencyStatusSets dss = getDependencySets(this.failOnMissingClassifierArtifact);
        Set artifacts = dss.getResolvedDependencies();

        for (Object artifactObj : artifacts) {
            copyArtifact((Artifact) artifactObj);
        }

        artifacts = dss.getSkippedDependencies();
        for (Object artifactOjb : artifacts) {
            Artifact artifact = (Artifact) artifactOjb;
            getLog().debug(artifact.getFile().getName() + " already exists in destination.");
        }
    }

    protected void copyArtifact(Artifact artifact) throws MojoExecutionException {
        String destFileName = DependencyUtil.getFormattedFileName(artifact, false);
        final File destDir = DependencyUtil.getFormattedOutputDirectory(false, false, false, false, false, getDependenciesDirectory(), artifact);
        final File destFile = new File(destDir, destFileName);
        copyFile(artifact.getFile(), destFile);
    }

    protected Artifact getResolvedPomArtifact(Artifact artifact) {
        Artifact pomArtifact = this.factory.createArtifact(artifact.getGroupId(), artifact.getArtifactId(), artifact.getVersion(), "", "pom");
        // Resolve the pom artifact using repos
        try {
            this.resolver.resolve(pomArtifact, this.remoteRepos, this.local);
        } catch (ArtifactResolutionException | ArtifactNotFoundException e) {
            getLog().info(e.getMessage());
        }
        return pomArtifact;
    }

    protected ArtifactsFilter getMarkedArtifactFilter() {
        return new DestFileFilter(this.overWriteReleases, this.overWriteSnapshots, this.overWriteIfNewer, false, false, false, false, false, getDependenciesDirectory());
    }


    protected DependencyStatusSets getDependencySets(boolean stopOnFailure) throws MojoExecutionException {
        // add filters in well known order, least specific to most specific
        FilterArtifacts filter = new FilterArtifacts();

        filter.addFilter(new ProjectTransitivityFilter(project.getDependencyArtifacts(), false));
        filter.addFilter(new ScopeFilter(this.includeScope, this.excludeScope));
        filter.addFilter(new TypeFilter(this.includeTypes, this.excludeTypes));
        filter.addFilter(new ClassifierFilter(this.includeClassifiers, this.excludeClassifiers));
        filter.addFilter(new GroupIdFilter(this.includeGroupIds, this.excludeGroupIds));
        filter.addFilter(new ArtifactIdFilter(this.includeArtifactIds, this.excludeArtifactIds));

        // explicitly filter our nar dependencies
        filter.addFilter(new TypeFilter("", "nar"));

        // start with all artifacts.
        Set artifacts = project.getArtifacts();

        // perform filtering
        try {
            artifacts = filter.filter(artifacts);
        } catch (ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // transform artifacts if classifier is set
        final DependencyStatusSets status;
        if (StringUtils.isNotEmpty(copyDepClassifier)) {
            status = getClassifierTranslatedDependencies(artifacts, stopOnFailure);
        } else {
            status = filterMarkedDependencies(artifacts);
        }

        return status;
    }

    protected DependencyStatusSets getClassifierTranslatedDependencies(Set artifacts, boolean stopOnFailure) throws MojoExecutionException {
        Set unResolvedArtifacts = new HashSet();
        Set resolvedArtifacts = artifacts;
        DependencyStatusSets status = new DependencyStatusSets();

        // possibly translate artifacts into a new set of artifacts based on the
        // classifier and type
        // if this did something, we need to resolve the new artifacts
        if (StringUtils.isNotEmpty(copyDepClassifier)) {
            ArtifactTranslator translator = new ClassifierTypeTranslator(this.copyDepClassifier, this.type, this.factory);
            artifacts = translator.translate(artifacts, getLog());

            status = filterMarkedDependencies(artifacts);

            // the unskipped artifacts are in the resolved set.
            artifacts = status.getResolvedDependencies();

            // resolve the rest of the artifacts
            ArtifactsResolver artifactsResolver = new DefaultArtifactsResolver(this.resolver, this.local,
                    this.remoteRepos, stopOnFailure);
            resolvedArtifacts = artifactsResolver.resolve(artifacts, getLog());

            // calculate the artifacts not resolved.
            unResolvedArtifacts.addAll(artifacts);
            unResolvedArtifacts.removeAll(resolvedArtifacts);
        }

        // return a bean of all 3 sets.
        status.setResolvedDependencies(resolvedArtifacts);
        status.setUnResolvedDependencies(unResolvedArtifacts);

        return status;
    }

    protected DependencyStatusSets filterMarkedDependencies(Set artifacts) throws MojoExecutionException {
        // remove files that have markers already
        FilterArtifacts filter = new FilterArtifacts();
        filter.clearFilters();
        filter.addFilter(getMarkedArtifactFilter());

        Set unMarkedArtifacts;
        try {
            unMarkedArtifacts = filter.filter(artifacts);
        } catch (ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // calculate the skipped artifacts
        Set skippedArtifacts = new HashSet();
        skippedArtifacts.addAll(artifacts);
        skippedArtifacts.removeAll(unMarkedArtifacts);

        return new DependencyStatusSets(unMarkedArtifacts, null, skippedArtifacts);
    }

    protected void copyFile(File artifact, File destFile) throws MojoExecutionException {
        try {
            getLog().info("Copying " + (this.outputAbsoluteArtifactFilename ? artifact.getAbsolutePath() : artifact.getName()) + " to " + destFile);
            FileUtils.copyFile(artifact, destFile);
        } catch (Exception e) {
            throw new MojoExecutionException("Error copying artifact from " + artifact + " to " + destFile, e);
        }
    }

    private File getClassesDirectory() {
        final File outputDirectory = projectBuildDirectory;
        return new File(outputDirectory, "classes");
    }

    private File getDependenciesDirectory() {
        return new File(getClassesDirectory(), "META-INF/bundled-dependencies");
    }

    private void makeNar() throws MojoExecutionException {
        File narFile = createArchive();

        if (classifier != null) {
            projectHelper.attachArtifact(project, "nar", classifier, narFile);
        } else {
            project.getArtifact().setFile(narFile);
        }
    }

    public File createArchive() throws MojoExecutionException {
        final File outputDirectory = projectBuildDirectory;
        File narFile = getNarFile(outputDirectory, finalName, classifier);
        MavenArchiver archiver = new MavenArchiver();
        archiver.setArchiver(jarArchiver);
        archiver.setOutputFile(narFile);
        archive.setForced(forceCreation);

        try {
            File contentDirectory = getClassesDirectory();
            if (contentDirectory.exists()) {
                archiver.getArchiver().addDirectory(contentDirectory, getIncludes(), getExcludes());
            } else {
                getLog().warn("NAR will be empty - no content was marked for inclusion!");
            }

            File extensionDocsFile = getExtensionsDocumentationFile();
            if (extensionDocsFile.exists()) {
                archiver.getArchiver().addFile(extensionDocsFile, "META-INF/" + extensionDocsFile.getName());
            } else {
                getLog().warn("NAR will not contain any Extensions' documentation - no META-INF/" + extensionDocsFile.getName() + " file found!");
            }

            File existingManifest = defaultManifestFile;
            if (useDefaultManifestFile && existingManifest.exists() && archive.getManifestFile() == null) {
                getLog().info("Adding existing MANIFEST to archive. Found under: " + existingManifest.getPath());
                archive.setManifestFile(existingManifest);
            }

            // automatically add the artifact id, group id, and version to the manifest
            archive.addManifestEntry("Nar-Id", narId);
            archive.addManifestEntry("Nar-Group", narGroup);
            archive.addManifestEntry("Nar-Version", narVersion);

            // look for a nar dependency
            NarDependency narDependency = getNarDependency();
            if (narDependency != null) {
                final String narDependencyGroup = notEmpty(this.narDependencyGroup) ? this.narDependencyGroup : narDependency.getGroupId();
                final String narDependencyId = notEmpty(this.narDependencyId) ? this.narDependencyId : narDependency.getArtifactId();
                final String narDependencyVersion = notEmpty(this.narDependencyVersion) ? this.narDependencyVersion : narDependency.getVersion();

                archive.addManifestEntry("Nar-Dependency-Group", narDependencyGroup);
                archive.addManifestEntry("Nar-Dependency-Id", narDependencyId);
                archive.addManifestEntry("Nar-Dependency-Version", narDependencyVersion);
            }

            // add build information when available

            if (notEmpty(buildTag)) {
                archive.addManifestEntry("Build-Tag", buildTag);
            }
            if (notEmpty(buildBranch)) {
                archive.addManifestEntry("Build-Branch", buildBranch);
            }
            if (notEmpty(buildRevision)) {
                archive.addManifestEntry("Build-Revision", buildRevision);
            }

            SimpleDateFormat dateFormat = new SimpleDateFormat(BUILD_TIMESTAMP_FORMAT);
            archive.addManifestEntry("Build-Timestamp", dateFormat.format(new Date()));

            archive.addManifestEntry("Clone-During-Instance-Class-Loading", String.valueOf(cloneDuringInstanceClassLoading));

            archiver.createArchive(session, project, archive);
            return narFile;
        } catch (ArchiverException | MojoExecutionException | ManifestException | IOException | DependencyResolutionRequiredException e) {
            throw new MojoExecutionException("Error assembling NAR", e);
        }
    }

    private boolean notEmpty(String value) {
        return value != null && !value.isEmpty();
    }

    private String[] getIncludes() {
        if (includes != null && includes.length > 0) {
            return includes;
        }
        return DEFAULT_INCLUDES;
    }

    private String[] getExcludes() {
        if (excludes != null && excludes.length > 0) {
            return excludes;
        }
        return DEFAULT_EXCLUDES;
    }

    protected File getNarFile(File basedir, String finalName, String classifier) {
        if (classifier == null) {
            classifier = "";
        } else if (classifier.trim().length() > 0 && !classifier.startsWith("-")) {
            classifier = "-" + classifier;
        }

        return new File(basedir, finalName + classifier + ".nar");
    }

    private NarDependency getNarDependency() throws MojoExecutionException {
        NarDependency narDependency = null;

        // get nar dependencies
        FilterArtifacts filter = new FilterArtifacts();
        filter.addFilter(new TypeFilter("nar", ""));

        // start with all artifacts.
        Set artifacts = project.getArtifacts();

        // perform filtering
        try {
            artifacts = filter.filter(artifacts);
        } catch (ArtifactFilterException e) {
            throw new MojoExecutionException(e.getMessage(), e);
        }

        // ensure there is a single nar dependency
        if (artifacts.size() > 1) {
            throw new MojoExecutionException("Each NAR represents a ClassLoader. A NAR dependency allows that NAR's ClassLoader to be "
                    + "used as the parent of this NAR's ClassLoader. As a result, only a single NAR dependency is allowed.");
        } else if (artifacts.size() == 1) {
            final Artifact artifact = (Artifact) artifacts.iterator().next();

            narDependency = new NarDependency(artifact.getGroupId(), artifact.getArtifactId(), artifact.getBaseVersion());
        }

        return narDependency;
    }

    private static class NarDependency {

        final String groupId;
        final String artifactId;
        final String version;

        public NarDependency(String groupId, String artifactId, String version) {
            this.groupId = groupId;
            this.artifactId = artifactId;
            this.version = version;
        }

        public String getGroupId() {
            return groupId;
        }

        public String getArtifactId() {
            return artifactId;
        }

        public String getVersion() {
            return version;
        }
    }

}
