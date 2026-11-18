package com.factori.platform;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

@SpringBootTest(properties = {
    "spring.datasource.url=jdbc:h2:mem:factori;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
    "spring.datasource.username=sa",
    "spring.datasource.password=",
    "spring.jpa.hibernate.ddl-auto=create-drop",
    "spring.flyway.enabled=false",
    "spring.security.oauth2.resourceserver.jwt.issuer-uri=https://issuer.example.test",
    "factori.build-worker-threads=1",
    "factori.build-queue-capacity=2",
    "factori.artifact-root=${java.io.tmpdir}/factori-test-artifacts"
})
class FactoriPlatformContextTest {
    @Autowired
    private FactoriPlatformApplication application;

    @MockitoBean
    private JwtDecoder jwtDecoder;

    @Test
    void applicationContextStartsWithConfiguredAuthenticationBoundary() {
        assertNotNull(application);
        assertNotNull(jwtDecoder);
    }
}
