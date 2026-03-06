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

import org.apache.maven.plugin.MojoExecutionException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.CodeSigner;
import java.security.KeyStore;
import java.security.cert.X509Certificate;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.jar.JarOutputStream;
import java.util.jar.Manifest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NarMojoTest {

    private static final String TEST_PASSWORD = "testpass";
    private static final String TEST_ALIAS = "test-signer";

    @TempDir
    static Path tempDir;

    private static Path testKeystorePath;

    @BeforeAll
    static void createTestKeystore() throws Exception {
        testKeystorePath = tempDir.resolve("test-keystore.p12");

        final ProcessBuilder pb = new ProcessBuilder(
                "keytool", "-genkeypair",
                "-alias", TEST_ALIAS,
                "-keyalg", "EC",
                "-keysize", "256",
                "-sigalg", "SHA256withECDSA",
                "-validity", "365",
                "-storetype", "PKCS12",
                "-keystore", testKeystorePath.toString(),
                "-storepass", TEST_PASSWORD,
                "-dname", "CN=Test NAR Signer, O=Apache NiFi Test, C=US"
        );
        pb.inheritIO();
        final Process process = pb.start();
        assertEquals(0, process.waitFor(), "keytool must succeed");
        assertTrue(Files.exists(testKeystorePath), "Keystore file must exist after generation");
    }

    @Test
    void testSignNar() throws Exception {
        final File narFile = createTestNar("test-sign");

        final NarMojo mojo = createSigningMojo();
        final KeyStore.PrivateKeyEntry keyEntry = mojo.loadSigningKey();
        mojo.signNar(narFile, keyEntry);

        try (final JarFile jarFile = new JarFile(narFile, true)) {
            final List<JarEntry> signableEntries = readAllEntriesAndCollectSignable(jarFile);
            assertFalse(signableEntries.isEmpty(), "NAR must contain signable entries");

            for (final JarEntry entry : signableEntries) {
                final CodeSigner[] signers = entry.getCodeSigners();
                assertNotNull(signers, "Entry [%s] must have code signers".formatted(entry.getName()));
                assertTrue(signers.length > 0, "Entry [%s] must have at least one code signer".formatted(entry.getName()));
            }
        }
    }

    @Test
    void testLoadSigningKeyReturnsCertificateWithExpectedDn() throws Exception {
        final NarMojo mojo = createSigningMojo();
        final KeyStore.PrivateKeyEntry keyEntry = mojo.loadSigningKey();

        assertNotNull(keyEntry);
        final X509Certificate cert = (X509Certificate) keyEntry.getCertificate();
        final String dn = cert.getSubjectX500Principal().getName();
        assertTrue(dn.contains("CN=Test NAR Signer"), "Signer DN must contain the expected CN, got: " + dn);
    }

    @Test
    void testSignNarDisabledByDefault() throws Exception {
        final File narFile = createTestNar("test-unsigned");

        try (final JarFile jarFile = new JarFile(narFile, true)) {
            final List<JarEntry> signableEntries = readAllEntriesAndCollectSignable(jarFile);
            for (final JarEntry entry : signableEntries) {
                final CodeSigner[] signers = entry.getCodeSigners();
                assertTrue(signers == null || signers.length == 0,
                        "Unsigned NAR entry [%s] must not have code signers".formatted(entry.getName()));
            }
        }
    }

    @Test
    void testSignNarMissingKeystore() {
        final NarMojo mojo = new NarMojo();
        mojo.signKeystore = null;
        mojo.signStorepass = TEST_PASSWORD;
        mojo.signAlias = TEST_ALIAS;
        mojo.signStoretype = "PKCS12";

        final MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::loadSigningKey);
        assertTrue(exception.getMessage().contains("nar.sign.keystore"));
    }

    @Test
    void testSignNarMissingStorepass() {
        final NarMojo mojo = new NarMojo();
        mojo.signKeystore = testKeystorePath.toString();
        mojo.signStorepass = null;
        mojo.signAlias = TEST_ALIAS;
        mojo.signStoretype = "PKCS12";

        final MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::loadSigningKey);
        assertTrue(exception.getMessage().contains("nar.sign.storepass"));
    }

    @Test
    void testSignNarMissingAlias() {
        final NarMojo mojo = new NarMojo();
        mojo.signKeystore = testKeystorePath.toString();
        mojo.signStorepass = TEST_PASSWORD;
        mojo.signAlias = null;
        mojo.signStoretype = "PKCS12";

        final MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::loadSigningKey);
        assertTrue(exception.getMessage().contains("nar.sign.alias"));
    }

    @Test
    void testSignNarInvalidAlias() {
        final NarMojo mojo = createSigningMojo();
        mojo.signAlias = "nonexistent-alias";

        final MojoExecutionException exception = assertThrows(MojoExecutionException.class, mojo::loadSigningKey);
        assertNotNull(exception.getMessage());
    }

    @Test
    void testSignNarPreservesContent() throws Exception {
        final File narFile = createTestNar("test-preserve");

        final Set<String> originalEntries = new HashSet<>();
        try (final JarFile jarFile = new JarFile(narFile)) {
            final Enumeration<JarEntry> entries = jarFile.entries();
            while (entries.hasMoreElements()) {
                originalEntries.add(entries.nextElement().getName());
            }
        }

        final NarMojo mojo = createSigningMojo();
        final KeyStore.PrivateKeyEntry keyEntry = mojo.loadSigningKey();
        mojo.signNar(narFile, keyEntry);

        try (final JarFile jarFile = new JarFile(narFile, true)) {
            final Enumeration<JarEntry> entries = jarFile.entries();
            final Set<String> signedEntries = new HashSet<>();
            while (entries.hasMoreElements()) {
                final JarEntry entry = entries.nextElement();
                signedEntries.add(entry.getName());
                try (final InputStream is = jarFile.getInputStream(entry)) {
                    assertNotNull(is.readAllBytes(), "Entry [%s] must be readable after signing".formatted(entry.getName()));
                }
            }

            for (final String original : originalEntries) {
                assertTrue(signedEntries.contains(original),
                        "Original entry [%s] must still be present after signing".formatted(original));
            }
        }
    }

    private NarMojo createSigningMojo() {
        final NarMojo mojo = new NarMojo();
        mojo.sign = true;
        mojo.signKeystore = testKeystorePath.toString();
        mojo.signStorepass = TEST_PASSWORD;
        mojo.signAlias = TEST_ALIAS;
        mojo.signStoretype = "PKCS12";
        return mojo;
    }

    private File createTestNar(final String name) throws Exception {
        final Path narPath = tempDir.resolve(name + ".nar");
        final Manifest manifest = new Manifest();
        manifest.getMainAttributes().putValue("Manifest-Version", "1.0");
        manifest.getMainAttributes().putValue("Nar-Id", name);
        manifest.getMainAttributes().putValue("Nar-Group", "org.apache.nifi.test");
        manifest.getMainAttributes().putValue("Nar-Version", "1.0.0");

        try (final JarOutputStream jos = new JarOutputStream(new FileOutputStream(narPath.toFile()), manifest)) {
            jos.putNextEntry(new JarEntry("META-INF/bundled-dependencies/"));
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("META-INF/bundled-dependencies/test-lib.jar"));
            jos.write("fake jar content for testing".getBytes());
            jos.closeEntry();

            jos.putNextEntry(new JarEntry("META-INF/docs/extension-manifest.xml"));
            jos.write("<extensionManifest/>".getBytes());
            jos.closeEntry();
        }

        return narPath.toFile();
    }

    private List<JarEntry> readAllEntriesAndCollectSignable(final JarFile jarFile) throws Exception {
        final Enumeration<JarEntry> entries = jarFile.entries();
        final List<JarEntry> signableEntries = new ArrayList<>();

        while (entries.hasMoreElements()) {
            final JarEntry entry = entries.nextElement();
            try (final InputStream is = jarFile.getInputStream(entry)) {
                is.readAllBytes();
            }
            if (!entry.isDirectory() && !isSignatureRelatedEntry(entry.getName())) {
                signableEntries.add(entry);
            }
        }

        return signableEntries;
    }

    private boolean isSignatureRelatedEntry(final String name) {
        if (name.equals("META-INF/MANIFEST.MF")) {
            return true;
        }
        if (!name.startsWith("META-INF/")) {
            return false;
        }
        final String upperName = name.toUpperCase();
        return (upperName.endsWith(".SF") || upperName.endsWith(".RSA") || upperName.endsWith(".EC") || upperName.endsWith(".DSA"))
                && name.indexOf('/', "META-INF/".length()) == -1;
    }
}
