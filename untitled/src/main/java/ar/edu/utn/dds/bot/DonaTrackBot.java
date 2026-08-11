package ar.edu.utn.dds.bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class DonaTrackBot extends TelegramLongPollingBot {

    private final String username;
    private final String token;
    private final BotCommandHandler commandHandler;

    public DonaTrackBot(
            String username,
            String token,
            BotCommandHandler commandHandler
    ) {
        this.username = username;
        this.token = token;
        this.commandHandler = commandHandler;
    }

    @Override
    public void onUpdateReceived(Update update) {

        if (!update.hasMessage() || !update.getMessage().hasText())
            return;

        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();
        String response = commandHandler.handle(chatId, text);

        sendMessage(chatId, response);
    }

    private void sendMessage(
            long chatId,
            String text
    ) {
        SendMessage message = new SendMessage();

        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
        } catch (TelegramApiException e) {
            System.err.println(
                    "Error enviando mensaje Telegram: "
                            + e.getMessage()
            );
        }
    }

    @Override
    public String getBotUsername() {
        return username;
    }

    @Override
    public String getBotToken() {
        return token;
    }
}
