package ar.edu.utn.dds.bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class DonaTrackBot extends TelegramLongPollingBot {
    private final String username;
    private final String token;
    private final BotCommandHandler commandHandler;

    public DonaTrackBot(String username, String token, BotCommandHandler commandHandler) {
        this.username = username;
        this.token = token;
        this.commandHandler = commandHandler;
    }

    @Override
    public void onUpdateReceived(Update update) {
        if (!update.hasMessage() || !update.getMessage().hasText()) {
            return;
        }

        long chatId = update.getMessage().getChatId();
        String text = update.getMessage().getText();

        System.out.printf("Comando recibido: %s%n", text.split("\\s+", 2)[0]);

        try {
            String response = commandHandler.handle(chatId, text);
            sendMessage(chatId, response);
        } catch (Exception exception) {
            System.err.printf("Error procesando mensaje chatId=%d: %s%n", chatId, exception.getMessage());
            sendMessage(chatId, "Error: " + exception.getMessage());
        }
    }

    private void sendMessage(long chatId, String text) {
        // Telegram accepts up to 4096 characters per message.
        for (int offset = 0; offset < text.length(); ) {
            int end = Math.min(offset + 4000, text.length());
            if (end < text.length() && Character.isHighSurrogate(text.charAt(end - 1))) end--;
            sendChunk(chatId, text.substring(offset, end));
            offset = end;
        }
    }

    private void sendChunk(long chatId, String text) {
        SendMessage message = new SendMessage();
        message.setChatId(String.valueOf(chatId));
        message.setText(text);

        try {
            execute(message);
            System.out.printf("Respuesta enviada chatId=%d%n", chatId);
        } catch (TelegramApiException exception) {
            System.err.println("No se pudo enviar el mensaje: " + exception.getMessage());
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
