package ar.edu.utn.dds.bot;

import org.telegram.telegrambots.bots.TelegramLongPollingBot;
import org.telegram.telegrambots.meta.api.methods.send.SendMessage;
import org.telegram.telegrambots.meta.api.objects.Update;
import org.telegram.telegrambots.meta.exceptions.TelegramApiException;

public class DonaTrackBot extends TelegramLongPollingBot {
  private final String username;
  private final String token;
  private final BotCommandHandler commandHandler;
  private final java.util.concurrent.ExecutorService workers =
      java.util.concurrent.Executors.newFixedThreadPool(
          4,
          r -> {
            Thread thread = new Thread(r, "donatrack-bot");
            thread.setDaemon(true);
            return thread;
          });
  private final java.util.concurrent.ConcurrentHashMap<
          Long, java.util.concurrent.CompletableFuture<Void>>
      pending = new java.util.concurrent.ConcurrentHashMap<>();
  private final java.util.concurrent.atomic.AtomicInteger queued =
      new java.util.concurrent.atomic.AtomicInteger();

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
    if (queued.incrementAndGet() > 100) {
      queued.decrementAndGet();
      sendMessage(chatId, "Hay varias operaciones en curso. Volvé a intentar en unos instantes.");
      return;
    }
    var future =
        pending.compute(
            chatId,
            (id, previous) -> {
              var ready =
                  previous == null
                      ? java.util.concurrent.CompletableFuture.<Void>completedFuture(null)
                      : previous.exceptionally(ex -> null);
              return ready.thenRunAsync(() -> process(chatId, text), workers);
            });
    future.whenComplete(
        (done, error) -> {
          queued.decrementAndGet();
          pending.remove(chatId, future);
        });
  }

  private void process(long chatId, String text) {
    try {
      var typing = new org.telegram.telegrambots.meta.api.methods.send.SendChatAction();
      typing.setChatId(String.valueOf(chatId));
      typing.setAction(org.telegram.telegrambots.meta.api.methods.ActionType.TYPING);
      execute(typing);
    } catch (TelegramApiException ignored) {
      /* A typing failure must not prevent the operation. */
    }

    org.slf4j.MDC.put("component", "telegram");
    org.slf4j.MDC.put("instanceId", System.getenv().getOrDefault("INSTANCE_ID", "local"));
    org.slf4j.LoggerFactory.getLogger(DonaTrackBot.class)
        .info("telegram.comando comando={}", text.split("\\s+", 2)[0]);

    try {
      String response = commandHandler.handle(chatId, text);
      sendMessage(chatId, response);
    } catch (Exception exception) {
      System.err.printf("Error procesando mensaje chatId=%d: %s%n", chatId, exception.getMessage());
      sendMessage(
          chatId,
          "No se pudo completar el comando. Revisá /menu y el estado del recurso antes de"
              + " repetirlo.");
    } finally {
      org.slf4j.MDC.clear();
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
    var keyboard =
        new org.telegram.telegrambots.meta.api.objects.replykeyboard.ReplyKeyboardMarkup();
    keyboard.setResizeKeyboard(true);
    var rows =
        new java.util.ArrayList<
            org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow>();
    for (var labels : commandHandler.keyboard(chatId)) {
      var row = new org.telegram.telegrambots.meta.api.objects.replykeyboard.buttons.KeyboardRow();
      row.addAll(labels);
      rows.add(row);
    }
    keyboard.setKeyboard(rows);
    message.setReplyMarkup(keyboard);

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
