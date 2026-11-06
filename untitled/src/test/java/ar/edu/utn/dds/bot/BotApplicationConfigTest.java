package ar.edu.utn.dds.bot;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BotApplicationConfigTest {
  @Test
  void keepsTheExistingClasspathConfigurationWithoutPackagingACredential() {
    var properties = BotApplication.cargarProperties(null);
    assertFalse(properties.getProperty("telegram.bot.username", "").isBlank());
    assertTrue(properties.getProperty("integrations.donadores-url", "").startsWith("http"));
    assertTrue(properties.getProperty("telegram.bot.token", "").isBlank());
  }

  @Test
  void readsAnExternalConfigurationWhenRequested(@TempDir Path directory) throws Exception {
    var file = directory.resolve("bot.properties");
    Files.writeString(file, "telegram.bot.username=test-bot\ntelegram.bot.token=test-token\n");
    var properties = BotApplication.cargarProperties(file.toString());
    assertEquals("test-bot", properties.getProperty("telegram.bot.username"));
    assertEquals("test-token", properties.getProperty("telegram.bot.token"));
    assertThrows(IllegalStateException.class,
        () -> BotApplication.cargarProperties(directory.resolve("missing.properties").toString()));
  }
}
