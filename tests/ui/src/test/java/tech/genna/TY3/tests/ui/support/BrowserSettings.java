package tech.genna.TY3.tests.ui.support;

import com.codeborne.selenide.Configuration;
import org.openqa.selenium.chrome.ChromeOptions;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

public final class BrowserSettings {
    private BrowserSettings() {}

    public static void configure() throws IOException {
        Properties local = new Properties();
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path file = dir.resolve("ui.local.properties");
            if (!Files.isRegularFile(file)) file = dir.resolve("tests/ui/ui.local.properties");
            if (Files.isRegularFile(file)) {
                try (var input = Files.newInputStream(file)) { local.load(input); }
                break;
            }
        }
        Configuration.baseUrl = setting(local, "ui.baseUrl", "http://localhost:3000");
        Configuration.browser = "chrome";
        Configuration.headless = Boolean.parseBoolean(setting(local, "ui.headless", "false"));
        Configuration.browserSize = "1366x900";
        Configuration.timeout = 15_000;
        Path driverLog = Path.of("build", "reports", "chromedriver.log").toAbsolutePath();
        Files.createDirectories(driverLog.getParent());
        System.setProperty("webdriver.chrome.logfile", driverLog.toString());
        System.setProperty("webdriver.chrome.verboseLogging", "true");
        String driver = setting(local, "ui.chromeDriver", "");
        if (!driver.isBlank()) {
            if (!Files.isRegularFile(Path.of(driver))) {
                throw new IllegalArgumentException("ChromeDriver does not exist: " + driver);
            }
            System.setProperty("webdriver.chrome.driver", driver);
        }
        ChromeOptions options = new ChromeOptions();
        String binary = setting(local, "ui.chromeBinary", "");
        if (!binary.isBlank()) {
            if (!Files.isRegularFile(Path.of(binary))) {
                throw new IllegalArgumentException("Chrome executable does not exist: " + binary);
            }
            options.setBinary(binary);
        }
        options.addArguments("--disable-gpu", "--no-first-run");
        if (Boolean.parseBoolean(setting(local, "ui.disableSandbox", "false"))) {
            options.addArguments("--no-sandbox", "--disable-gpu-sandbox");
        }
        Configuration.browserCapabilities = options;
        System.out.println("ChromeDriver log: " + driverLog);
    }

    private static String setting(Properties local, String key, String fallback) {
        return System.getProperty(key, local.getProperty(key, fallback));
    }
}
