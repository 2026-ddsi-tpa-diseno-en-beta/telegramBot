package ar.edu.utn.dds.bot;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

public class BotApplication {

    public static void main(String[] args) throws Exception {
        Properties properties = cargarProperties();

        String username = requiredProperty(properties, "telegram.bot.username", "TELEGRAM_BOT_USERNAME");
        String token = requiredProperty(properties, "telegram.bot.token", "TELEGRAM_BOT_TOKEN");
        String donadoresUrl = requiredProperty(properties, "integrations.donadores-url", "DONADORES_API_URL");

        DonadoresApiClient donadoresApiClient = new DonadoresApiClient(donadoresUrl);
        ComponentCommandHandler components = new ComponentCommandHandler(java.util.Map.of(
            "donadores", donadoresUrl,
            "donaciones", optionalProperty(properties, "integrations.donaciones-url", "DONACIONES_API_URL", "http://localhost:8081"),
            "logistica", optionalProperty(properties, "integrations.logistica-url", "LOGISTICA_API_URL", "http://localhost:8083"),
            "incentivos", optionalProperty(properties, "integrations.incentivos-url", "INCENTIVOS_API_URL", "http://localhost:8084")));
        java.util.Set<Long> adminChats = new java.util.HashSet<>();
        String configuredAdmins = System.getenv().getOrDefault("TELEGRAM_ADMIN_CHAT_IDS", "");
        for (String id : configuredAdmins.split(",")) if (!id.isBlank()) adminChats.add(Long.parseLong(id.trim()));
        BotCommandHandler commandHandler = new BotCommandHandler(donadoresApiClient, components, adminChats);
        DonaTrackBot bot = new DonaTrackBot(username, token, commandHandler);

        TelegramBotsApi botsApi = new TelegramBotsApi(DefaultBotSession.class);
        botsApi.registerBot(bot);

        System.out.println("DonaTrack Telegram Bot iniciado.");
    }

    private static Properties cargarProperties() {
        Properties properties = new Properties();

        try (InputStream inputStream = BotApplication.class
            .getClassLoader()
            .getResourceAsStream("application.properties")) {

            if (inputStream == null) {
                throw new IllegalStateException("No se encontro application.properties");
            }

            properties.load(inputStream);
            return properties;
        } catch (IOException exception) {
            throw new IllegalStateException("No se pudo cargar application.properties", exception);
        }
    }

    private static String requiredProperty(Properties properties, String key, String environmentVariable) {
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

    private static String optionalProperty(Properties properties, String key, String environmentVariable, String fallback) {
        String value = System.getenv(environmentVariable);
        return value != null && !value.isBlank() ? value : properties.getProperty(key, fallback);
    }
}
