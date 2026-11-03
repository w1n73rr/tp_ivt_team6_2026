package tech.genna.TY3.tests.base;

import io.restassured.RestAssured;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tech.genna.TY3.service.file.FileStorageService;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "spring.config.import=")
@ActiveProfiles("test")
public abstract class BaseTest {

    // Authentication tests use real PostgreSQL, but never the developer's S3 storage.
    @MockitoBean
    protected FileStorageService fileStorageService;

    @LocalServerPort
    protected int port;

    @BeforeEach
    void setUpBase() {
        RestAssured.port = port;
        RestAssured.enableLoggingOfRequestAndResponseIfValidationFails();
    }
}
