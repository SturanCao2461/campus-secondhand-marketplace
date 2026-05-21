package nz.ac.waikato.campusmarketplace;

import org.junit.jupiter.api.Test;

/**
 * Default Spring Boot smoke test. Verifies the full ApplicationContext loads.
 *
 * Extends AbstractIntegrationTest so it picks up the shared Testcontainers
 * MySQL + Redis setup and the "test" profile — without these, the test would
 * try to connect to localhost:3306 / localhost:6379 and fail in CI.
 */
class BackendApplicationTests extends AbstractIntegrationTest {

    @Test
    void contextLoads() {
    }
}
