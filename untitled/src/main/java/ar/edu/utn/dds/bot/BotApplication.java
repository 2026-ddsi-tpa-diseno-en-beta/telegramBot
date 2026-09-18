package ar.edu.utn.dds.bot;

import org.telegram.telegrambots.meta.TelegramBotsApi;
import org.telegram.telegrambots.updatesreceivers.DefaultBotSession;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class BotApplication {

    public static void main(String[] args) throws Exception {

        Properties properties = cargarProperties();

        String username = requiredProperty(
                properties,
                "telegram.bot.username"
        );

        String token = requiredProperty(
                properties,
                "telegram.bot.token"
        );

        String donadoresUrl = requiredProperty(
                properties,
                "integrations.donadores-url"
        );

        String logisticaUrl = requiredProperty(properties, "integrations.logistica-url");
        String incentivosUrl = requiredProperty(properties, "integrations.incentivos-url");

        DonadoresApiClient api =
                new DonadoresApiClient(donadoresUrl);

        LogisticaApiClient apiLogistica = new LogisticaApiClient(logisticaUrl);
        IncentivosApiClient apiIncentivos = new IncentivosApiClient(incentivosUrl);

        BotCommandHandler handler =
                new BotCommandHandler(api, apiLogistica, apiIncentivos);

        DonaTrackBot bot =
                new DonaTrackBot(
                        username,
                        token,
                        handler
                );

        TelegramBotsApi telegramBotsApi =
                new TelegramBotsApi(
                        DefaultBotSession.class
                );

        telegramBotsApi.registerBot(bot);
        System.out.println("DonaTrack Telegram Bot iniciado.");
    }

    private static Properties cargarProperties() {
        Properties properties = new Properties();

        try (InputStream input =
                     BotApplication.class
                             .getClassLoader()
                             .getResourceAsStream("application.properties")) {
            if (input == null)
                throw new IllegalStateException(
                        "No se encontró application.properties"
                );

            properties.load(input);
        } catch (IOException e) {
            throw new IllegalStateException(
                    "No se pudo cargar application.properties",
                    e
            );
        }

        return properties;
    }

    private static String requiredProperty(
            Properties properties,
            String name
    ) {
        String value = properties.getProperty(name);
        if (value == null || value.isBlank())
            throw new IllegalStateException(
                    "Falta propiedad: " + name
            );

        return value;
    }
}
