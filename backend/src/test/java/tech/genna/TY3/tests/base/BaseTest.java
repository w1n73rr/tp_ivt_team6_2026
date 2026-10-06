package tech.genna.TY3.tests.base;

import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import tech.genna.TY3.config.TestConfig;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public abstract class BaseTest {

    @LocalServerPort
    protected int port;

    @BeforeEach
    void setUpBase() {
        TestConfig.setupRequestSpecification(port);
    }
}