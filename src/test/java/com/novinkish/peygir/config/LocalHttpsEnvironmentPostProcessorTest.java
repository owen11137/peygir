package com.novinkish.peygir.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.core.env.MapPropertySource;
import org.springframework.core.env.StandardEnvironment;

import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class LocalHttpsEnvironmentPostProcessorTest {

    @TempDir
    Path temporaryDirectory;

    private final LocalHttpsEnvironmentPostProcessor processor = new LocalHttpsEnvironmentPostProcessor();

    @Test
    void generatesLocalServerCertificateAndReusesItsKeystoreAndPassword() throws Exception {
        Path directory = temporaryDirectory.resolve("installation");
        StandardEnvironment firstStart = environment(directory, Map.of());
        processor.postProcessEnvironment(firstStart, null);

        Path storePath = directory.resolve("peygir.p12");
        Path passwordPath = directory.resolve("keystore-password");
        assertEquals(storePath, Path.of(URI.create(firstStart.getProperty("server.ssl.key-store"))));
        assertEquals("PKCS12", firstStart.getProperty("server.ssl.key-store-type"));
        X509Certificate certificate = certificate(directory);
        certificate.checkValidity();
        certificate.verify(certificate.getPublicKey());
        assertEquals(-1, certificate.getBasicConstraints());
        assertEquals(List.of("1.3.6.1.5.5.7.3.1"), certificate.getExtendedKeyUsage());
        var subjectAlternativeNames = certificate.getSubjectAlternativeNames();
        assertTrue(subjectAlternativeNames.stream().anyMatch(name -> name.equals(List.of(2, "localhost"))));
        assertTrue(subjectAlternativeNames.stream().anyMatch(name -> name.equals(List.of(7, "127.0.0.1"))));
        try (InputStream input = Files.newInputStream(directory.resolve("localhost.crt"))) {
            var exportedCertificate = CertificateFactory.getInstance("X.509").generateCertificate(input);
            assertEquals(certificate, exportedCertificate);
        }

        byte[] originalStore = Files.readAllBytes(storePath);
        byte[] originalPassword = Files.readAllBytes(passwordPath);
        StandardEnvironment secondStart = environment(directory, Map.of());
        processor.postProcessEnvironment(secondStart, null);
        assertTrue(Arrays.equals(originalStore, Files.readAllBytes(storePath)), "Restart must preserve the keystore");
        assertTrue(Arrays.equals(originalPassword, Files.readAllBytes(passwordPath)), "Restart must preserve its password");
        assertTrue(firstStart.getProperty("server.ssl.key-store-password")
                .equals(secondStart.getProperty("server.ssl.key-store-password")), "Restart must reuse the password");
        assertEquals(certificate, certificate(directory));
        Arrays.fill(originalPassword, (byte) 0);
    }

    @Test
    void createsIndependentMaterialAndRejectsAMismatchedCertificateExport() throws Exception {
        Path first = temporaryDirectory.resolve("first");
        Path second = temporaryDirectory.resolve("second");
        processor.postProcessEnvironment(environment(first, Map.of()), null);
        processor.postProcessEnvironment(environment(second, Map.of()), null);

        assertNotEquals(certificate(first), certificate(second));
        byte[] firstPassword = Files.readAllBytes(first.resolve("keystore-password"));
        byte[] secondPassword = Files.readAllBytes(second.resolve("keystore-password"));
        try {
            assertFalse(Arrays.equals(firstPassword, secondPassword), "Each installation needs an independent password");
        } finally {
            Arrays.fill(firstPassword, (byte) 0);
            Arrays.fill(secondPassword, (byte) 0);
        }

        Path certificatePath = first.resolve("localhost.crt");
        byte[] unrelatedCertificate = Files.readAllBytes(second.resolve("localhost.crt"));
        byte[] originalStore = Files.readAllBytes(first.resolve("peygir.p12"));
        Files.write(certificatePath, unrelatedCertificate);
        assertThrows(IllegalStateException.class,
                () -> processor.postProcessEnvironment(environment(first, Map.of()), null));
        assertTrue(Arrays.equals(unrelatedCertificate, Files.readAllBytes(certificatePath)),
                "An existing certificate must not be overwritten");
        assertTrue(Arrays.equals(originalStore, Files.readAllBytes(first.resolve("peygir.p12"))),
                "A certificate mismatch must preserve the keystore");
    }

    @ParameterizedTest
    @ValueSource(strings = {"server.ssl.key-store", "server.ssl.bundle"})
    void explicitTlsConfigurationDoesNotGenerateOrOverrideLocalFiles(String property) {
        Path directory = temporaryDirectory.resolve("unused");
        StandardEnvironment environment = environment(directory, Map.of(property, "external-configuration"));
        processor.postProcessEnvironment(environment, null);

        assertFalse(Files.exists(directory));
        assertEquals("external-configuration", environment.getProperty(property));
        assertNull(environment.getProperty("server.ssl.key-store-password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"server.ssl.enabled", "peygir.https.auto-generate"})
    void disabledTlsOrGenerationDoesNotCreateFiles(String property) {
        Path directory = temporaryDirectory.resolve("unused");
        StandardEnvironment environment = environment(directory, Map.of(property, false));
        processor.postProcessEnvironment(environment, null);

        assertFalse(Files.exists(directory));
        assertNull(environment.getProperty("server.ssl.key-store"));
        assertNull(environment.getProperty("server.ssl.key-store-password"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"peygir.p12", "keystore-password"})
    void incompletePairFailsWithoutOverwritingTheRemainingFile(String filename) throws Exception {
        Path directory = temporaryDirectory.resolve("incomplete");
        Files.createDirectories(directory);
        Path existingFile = directory.resolve(filename);
        Files.writeString(existingFile, "existing-local-data", StandardCharsets.UTF_8);

        assertThrows(IllegalStateException.class,
                () -> processor.postProcessEnvironment(environment(directory, Map.of()), null));
        assertTrue("existing-local-data".equals(Files.readString(existingFile)), "Existing local data must be preserved");
        try (var files = Files.list(directory)) {
            assertEquals(1, files.count());
        }
    }

    @Test
    void orphanCertificateFailsWithoutCreatingANewIdentity() throws Exception {
        Path directory = temporaryDirectory.resolve("orphan");
        Files.createDirectories(directory);
        Path certificatePath = directory.resolve("localhost.crt");
        Files.writeString(certificatePath, "existing-public-certificate", StandardCharsets.US_ASCII);

        assertThrows(IllegalStateException.class,
                () -> processor.postProcessEnvironment(environment(directory, Map.of()), null));
        assertEquals("existing-public-certificate", Files.readString(certificatePath));
        assertFalse(Files.exists(directory.resolve("peygir.p12")));
        assertFalse(Files.exists(directory.resolve("keystore-password")));
    }

    private static StandardEnvironment environment(Path directory, Map<String, ?> overrides) {
        StandardEnvironment environment = new StandardEnvironment();
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_ENVIRONMENT_PROPERTY_SOURCE_NAME);
        environment.getPropertySources().remove(StandardEnvironment.SYSTEM_PROPERTIES_PROPERTY_SOURCE_NAME);
        Map<String, Object> properties = new LinkedHashMap<>();
        properties.put("server.ssl.enabled", true);
        properties.put("peygir.https.directory", directory.toString());
        properties.putAll(overrides);
        environment.getPropertySources().addFirst(new MapPropertySource("test", properties));
        return environment;
    }

    private static X509Certificate certificate(Path directory) throws Exception {
        char[] password = Files.readString(directory.resolve("keystore-password")).strip().toCharArray();
        try (InputStream input = Files.newInputStream(directory.resolve("peygir.p12"))) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(input, password);
            assertTrue(store.getKey("peygir", password) instanceof PrivateKey, "A server private key is required");
            return (X509Certificate) store.getCertificate("peygir");
        } finally {
            Arrays.fill(password, '\0');
        }
    }
}
