package com.novinkish.peygir.config;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.env.EnvironmentPostProcessor;
import org.springframework.core.Ordered;
import org.springframework.core.env.ConfigurableEnvironment;
import org.springframework.core.env.MapPropertySource;
import org.springframework.util.StringUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFileAttributeView;
import java.nio.file.attribute.PosixFilePermissions;
import java.security.KeyStore;
import java.security.PrivateKey;
import java.security.SecureRandom;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.Arrays;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Collections;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/** Creates installation-specific localhost and LAN TLS material before the web server starts. */
public class LocalHttpsEnvironmentPostProcessor implements EnvironmentPostProcessor, Ordered {

    private static final String PASSWORD_ENV = "PEYGIR_LOCAL_TLS_PASSWORD";

    @Override
    public int getOrder() {
        // Run after ConfigData, so application.properties and user overrides are available.
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public void postProcessEnvironment(ConfigurableEnvironment environment, SpringApplication application) {
        if (!environment.getProperty("server.ssl.enabled", Boolean.class, false)
                || !environment.getProperty("peygir.https.auto-generate", Boolean.class, true)
                || StringUtils.hasText(environment.getProperty("server.ssl.key-store"))
                || StringUtils.hasText(environment.getProperty("server.ssl.bundle"))) {
            return;
        }

        Path directory = Path.of(environment.getProperty("peygir.https.directory", "./data/tls"))
                .toAbsolutePath().normalize();
        String alias = environment.getProperty("server.ssl.key-alias", "peygir");
        try {
            Files.createDirectories(directory);
            restrictPermissions(directory, "rwx------");
            Path storePath = directory.resolve("peygir.p12");
            Path passwordPath = directory.resolve("keystore-password");
            Path certificatePath = directory.resolve("localhost.crt");
            boolean storeExists = Files.exists(storePath);
            boolean passwordExists = Files.exists(passwordPath);
            if (storeExists != passwordExists || (!storeExists && Files.exists(certificatePath))) {
                throw new IllegalStateException("Incomplete local HTTPS files in " + directory
                        + ". Restore the matching peygir.p12 and keystore-password from a local backup,"
                        + " or move this directory aside and restart to generate a new localhost certificate."
                        + " Existing files will not be replaced automatically.");
            }
            if (!storeExists) {
                generate(directory, storePath, passwordPath, alias);
            }

            restrictPermissions(storePath, "rw-------");
            restrictPermissions(passwordPath, "rw-------");
            String password = Files.readString(passwordPath, StandardCharsets.UTF_8).strip();
            if (!StringUtils.hasText(password)) {
                throw new IllegalStateException("The local HTTPS password file is empty: " + passwordPath);
            }
            X509Certificate certificate = loadCertificate(storePath, password, alias);
            certificate.checkValidity();
            exportCertificateIfMissing(certificatePath, certificate);

            Map<String, Object> properties = new LinkedHashMap<>();
            properties.put("server.ssl.key-store", storePath.toUri().toString());
            properties.put("server.ssl.key-store-password", password);
            properties.put("server.ssl.key-store-type", "PKCS12");
            properties.put("server.ssl.key-alias", alias);
            // Explicit configuration always takes priority over these generated defaults.
            environment.getPropertySources().addLast(new MapPropertySource("peygirLocalHttps", properties));
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Local HTTPS certificate generation was interrupted.", exception);
        } catch (IllegalStateException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalStateException("Cannot prepare local HTTPS files in " + directory
                    + ". Check directory permissions and the matching keystore/password pair."
                    + " For an expired or damaged local certificate, move this directory aside and restart;"
                    + " existing files are never replaced automatically.", exception);
        }
    }

    private static void generate(Path directory, Path storePath, Path passwordPath, String alias)
            throws IOException, InterruptedException {
        String executable = System.getProperty("os.name").startsWith("Windows") ? "keytool.exe" : "keytool";
        Path keytool = Path.of(System.getProperty("java.home"), "bin", executable);
        if (!Files.isRegularFile(keytool)) {
            throw new IllegalStateException("Local HTTPS requires keytool. Run Peygir with a full JDK 17 or newer"
                    + ", or configure server.ssl.key-store with your own certificate.");
        }

        byte[] random = new byte[32];
        new SecureRandom().nextBytes(random);
        String password = Base64.getEncoder().encodeToString(random);
        Arrays.fill(random, (byte) 0);
        Path temporaryDirectory = Files.createTempDirectory(directory, ".https-");
        Path temporaryStore = temporaryDirectory.resolve("peygir.p12");
        Path temporaryPassword = temporaryDirectory.resolve("keystore-password");
        try {
            restrictPermissions(temporaryDirectory, "rwx------");
            Files.writeString(temporaryPassword, password, StandardCharsets.UTF_8);
            restrictPermissions(temporaryPassword, "rw-------");
            ProcessBuilder builder = new ProcessBuilder(
                    keytool.toString(), "-genkeypair", "-noprompt", "-alias", alias,
                    "-keyalg", "RSA", "-keysize", "3072", "-sigalg", "SHA256withRSA",
                    "-validity", "365", "-dname", "CN=localhost, OU=Peygir Local, O=Peygir",
                    "-ext", "SAN=" + subjectAlternativeNames(), "-ext", "EKU=serverAuth",
                    "-ext", "BC=ca:false", "-storetype", "PKCS12", "-keystore", temporaryStore.toString(),
                    "-storepass:env", PASSWORD_ENV, "-keypass:env", PASSWORD_ENV);
            builder.environment().put(PASSWORD_ENV, password);
            // Do not place the password on the command line or include tool output in startup errors.
            builder.redirectOutput(ProcessBuilder.Redirect.DISCARD);
            builder.redirectError(ProcessBuilder.Redirect.DISCARD);
            Process process = builder.start();
            try {
                if (!process.waitFor(30, TimeUnit.SECONDS)) {
                    throw new IllegalStateException("Local HTTPS keytool timed out after 30 seconds."
                            + " Check the JDK and write access to " + directory + ".");
                }
                if (process.exitValue() != 0) {
                    throw new IllegalStateException("Local HTTPS keytool failed with exit code "
                            + process.exitValue() + ". Check the JDK and write access to " + directory + ".");
                }
            } finally {
                if (process.isAlive()) {
                    process.destroyForcibly();
                    process.waitFor(5, TimeUnit.SECONDS);
                }
                builder.environment().remove(PASSWORD_ENV);
            }
            restrictPermissions(temporaryStore, "rw-------");
            // Moving without REPLACE_EXISTING preserves any certificate another process created.
            Files.move(temporaryStore, storePath);
            Files.move(temporaryPassword, passwordPath);
        } finally {
            Files.deleteIfExists(temporaryStore);
            Files.deleteIfExists(temporaryPassword);
            Files.deleteIfExists(temporaryDirectory);
        }
    }

    /** Include IPs of active interfaces when creating a new certificate; never rotate existing keys. */
    static String subjectAlternativeNames() throws SocketException {
        var names = new LinkedHashSet<String>();
        names.add("dns:localhost");
        names.add("ip:127.0.0.1");
        names.add("ip:::1");
        var interfaces = NetworkInterface.getNetworkInterfaces();
        if (interfaces != null) {
            for (var network : Collections.list(interfaces)) {
                if (!network.isUp() || network.isLoopback()) continue;
                for (var address : Collections.list(network.getInetAddresses())) {
                    if (address.isLoopbackAddress() || address.isAnyLocalAddress()
                            || address.isMulticastAddress() || address.isLinkLocalAddress()) continue;
                    // Certificate IP entries do not carry IPv6 scope identifiers.
                    names.add("ip:" + address.getHostAddress().split("%", 2)[0]);
                }
            }
        }
        return String.join(",", names);
    }

    private static X509Certificate loadCertificate(Path storePath, String password, String alias) throws Exception {
        char[] passwordChars = password.toCharArray();
        try (InputStream input = Files.newInputStream(storePath)) {
            KeyStore store = KeyStore.getInstance("PKCS12");
            store.load(input, passwordChars);
            if (!(store.getKey(alias, passwordChars) instanceof PrivateKey)
                    || !(store.getCertificate(alias) instanceof X509Certificate certificate)) {
                throw new IllegalStateException("Local HTTPS keystore does not contain a private key and certificate"
                        + " for alias '" + alias + "': " + storePath);
            }
            return certificate;
        } finally {
            Arrays.fill(passwordChars, '\0');
        }
    }

    private static void exportCertificateIfMissing(Path path, X509Certificate certificate) throws Exception {
        if (Files.exists(path)) {
            try (InputStream input = Files.newInputStream(path)) {
                byte[] existing = CertificateFactory.getInstance("X.509").generateCertificate(input).getEncoded();
                if (!Arrays.equals(existing, certificate.getEncoded())) {
                    throw new IllegalStateException("The public local HTTPS certificate does not match the keystore: "
                            + path + ". Restore the matching certificate from a local backup, or move the entire"
                            + " TLS directory aside and restart. Existing files will not be replaced automatically.");
                }
            }
            return;
        }
        String encoded = Base64.getMimeEncoder(64, new byte[]{'\n'}).encodeToString(certificate.getEncoded());
        Files.writeString(path, "-----BEGIN CERTIFICATE-----\n" + encoded + "\n-----END CERTIFICATE-----\n",
                StandardCharsets.US_ASCII, java.nio.file.StandardOpenOption.CREATE_NEW);
    }

    private static void restrictPermissions(Path path, String permissions) throws IOException {
        if (Files.getFileAttributeView(path, PosixFileAttributeView.class) != null) {
            Files.setPosixFilePermissions(path, PosixFilePermissions.fromString(permissions));
        }
    }
}
