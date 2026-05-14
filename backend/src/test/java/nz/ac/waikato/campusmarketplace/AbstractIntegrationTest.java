package nz.ac.waikato.campusmarketplace;

import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.MySQLContainer;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ActiveProfiles("test")
public abstract class AbstractIntegrationTest {

    @ServiceConnection
    static final MySQLContainer<?> mysql = new MySQLContainer<>("mysql:8.0")
            .withDatabaseName("campus_marketplace_test");

    static final GenericContainer<?> redis = new GenericContainer<>("redis:7.2-alpine")
            .withExposedPorts(6379);

    static {
        mysql.start();
        redis.start();
    }

    @DynamicPropertySource
    static void redisProps(DynamicPropertyRegistry r) {
        r.add("spring.data.redis.host", redis::getHost);
        r.add("spring.data.redis.port", () -> redis.getMappedPort(6379));
        r.add("app.jwt.secret",
                () -> "VGhpc0lzQVRlc3RTZWNyZXRBdExlYXN0VGhpcnR5VHdvQnl0ZXM=");
        r.add("app.cookie.secure", () -> "false");
        r.add("app.email.mode", () -> "console");
    }
}
