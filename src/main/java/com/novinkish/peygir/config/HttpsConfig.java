package com.novinkish.peygir.config;

import org.apache.catalina.connector.Connector;
import org.apache.tomcat.util.descriptor.web.SecurityCollection;
import org.apache.tomcat.util.descriptor.web.SecurityConstraint;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** HTTP فقط به HTTPS هدایت می‌شود؛ صفحات و فرم‌ها از اتصال امن ارائه می‌شوند. */
@Configuration
@ConditionalOnProperty(name = "server.ssl.enabled", havingValue = "true")
public class HttpsConfig {
    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> httpsRedirect(
            @Value("${server.port}") int httpsPort,
            @Value("${peygir.http-port:8080}") int httpPort,
            @Value("${server.address:127.0.0.1}") String address) {
        if (httpsPort < 1 || httpsPort > 65535)
            throw new IllegalArgumentException("HTTPS port must be between 1 and 65535");
        if (httpPort == httpsPort || httpPort > 65535 || httpPort == 0 || httpPort < -1)
            throw new IllegalArgumentException("HTTP port must differ from HTTPS; use -1 to disable HTTP");
        return factory -> {
            if (httpPort > 0) {
                Connector http = new Connector("org.apache.coyote.http11.Http11NioProtocol");
                http.setPort(httpPort);
                http.setScheme("http");
                http.setSecure(false);
                http.setRedirectPort(httpsPort);
                http.setProperty("address", address);
                factory.addAdditionalTomcatConnectors(http);
            }
            SecurityCollection paths = new SecurityCollection();
            paths.addPattern("/*");
            SecurityConstraint secure = new SecurityConstraint();
            secure.setUserConstraint("CONFIDENTIAL");
            secure.addCollection(paths);
            factory.addContextCustomizers(context -> context.addConstraint(secure));
        };
    }
}
