package ar.edu.utn.dds.bot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class BotApplication {

  public static void main(String[] args) throws Exception {
    Properties properties = cargarProperties();

    String username =
        requiredProperty(properties, "telegram.bot.username", "TELEGRAM_BOT_USERNAME");
    String token = requiredProperty(properties, "telegram.bot.token", "TELEGRAM_BOT_TOKEN");
    String gateway =
        optionalProperty(properties, "integrations.gateway-url", "GATEWAY_URL", "http://localhost:8080")
            .replaceAll("/+$", "");
    Properties serviceProperties = System.getenv().getOrDefault("GATEWAY_URL", "").isBlank()
        && properties.getProperty("integrations.gateway-url", "").isBlank() ? properties : new Properties();
    String donadoresUrl =
        optionalProperty(serviceProperties, "integrations.donadores-url", "DONADORES_API_URL", gateway + "/api/donadores-entidades");
    DonadoresApiClient donadoresApiClient = new DonadoresApiClient(donadoresUrl);
    ComponentCommandHandler components =
        new ComponentCommandHandler(
            java.util.Map.of(
                "donadores",
                donadoresUrl,
                "donaciones",
                optionalProperty(serviceProperties, "integrations.donaciones-url", "DONACIONES_API_URL", gateway + "/api/donaciones"),
                "logistica",
                optionalProperty(serviceProperties, "integrations.logistica-url", "LOGISTICA_API_URL", gateway + "/api/logistica"),
                "incentivos",
                optionalProperty(serviceProperties, "integrations.incentivos-url", "INCENTIVOS_API_URL", gateway + "/api/incentivos"),
                "consultas",
                optionalProperty(serviceProperties, "integrations.consultas-url", "CONSULTAS_API_URL", gateway + "/api/consultas"),
                "gateway",
                gateway));
    java.util.Set<Long> adminChats = new java.util.HashSet<>();
    String configuredAdmins = System.getenv().getOrDefault("TELEGRAM_ADMIN_CHAT_IDS", "");
    for (String id : configuredAdmins.split(","))
      if (!id.isBlank()) adminChats.add(Long.parseLong(id.trim()));
    BotCommandHandler commandHandler =
        new BotCommandHandler(donadoresApiClient, components, adminChats);
    DonaTrackBot bot = new DonaTrackBot(username, token, commandHandler);

    TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
    botsApi.registerBot(bot);

    System.out.println("DonaTrack Telegram Bot iniciado.");
  }

  private static Properties cargarProperties() {
    return cargarProperties(System.getenv("TELEGRAM_CONFIG"));
  }

  static Properties cargarProperties(String external) {
    Properties properties = new Properties();
    try (InputStream inputStream =
        external == null || external.isBlank()
            ? BotApplication.class.getClassLoader().getResourceAsStream("application.properties")
            : java.nio.file.Files.newInputStream(java.nio.file.Path.of(external))) {

      if (inputStream == null) {
        return properties;
      }

      properties.load(inputStream);
      return properties;
    } catch (IOException exception) {
      throw new IllegalStateException("No se pudo cargar application.properties", exception);
    }
  }

  private static String requiredProperty(
      Properties properties, String key, String environmentVariable) {
    String environmentValue = System.getenv(environmentVariable);
    if (environmentValue != null && !environmentValue.isBlank()) {
      return environmentValue;
    }

    String propertyValue = properties.getProperty(key);
    if (propertyValue == null || propertyValue.isBlank()) {
      throw new IllegalStateException(key + " o " + environmentVariable + " es obligatorio");
    }

    return propertyValue;
  }

  private static String optionalProperty(
      Properties properties, String key, String environmentVariable, String fallback) {
    String value = System.getenv(environmentVariable);
    return value != null && !value.isBlank() ? value : properties.getProperty(key, fallback);
  }
}
