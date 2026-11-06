package ar.edu.utn.dds.bot;

import static org.junit.jupiter.api.Assertions.*;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.*;
import org.junit.jupiter.api.*;

class GuidedOperationsTest {
  HttpServer server;
  BotCommandHandler bot;
  AtomicInteger writes;
  AtomicReference<String> body;

  @BeforeEach
  void setup() throws Exception {
    writes = new AtomicInteger();
    body = new AtomicReference<>();
    server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
    server.createContext(
        "/",
        ex -> {
          if (!ex.getRequestMethod().equals("GET")) writes.incrementAndGet();
          body.set(new String(ex.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
          byte[] response =
              "{\"id\":\"created\",\"estado\":\"INGRESADA\",\"cantidad\":10}"
                  .getBytes(StandardCharsets.UTF_8);
          ex.sendResponseHeaders(200, response.length);
          ex.getResponseBody().write(response);
          ex.close();
        });
    server.start();
    String base = "http://127.0.0.1:" + server.getAddress().getPort();
    bot =
        new BotCommandHandler(
            new DonadoresApiClient(base),
            new ComponentCommandHandler(
                Map.of(
                    "donaciones",
                    base,
                    "donadores",
                    base,
                    "logistica",
                    base,
                    "incentivos",
                    base,
                    "consultas",
                    base,
                    "gateway",
                    base)));
    bot.handle(1, "/admin");
  }

  @AfterEach
  void close() {
    server.stop(0);
  }

  String form() {
    assertTrue(bot.handle(1, "🤝 Donar").contains("Paso 1"));
    for (String value : List.of("donor", "depot", "product", "Alimentos")) bot.handle(1, value);
    return bot.handle(1, "10");
  }

  @Test
  void formsRequireConfirmationAndDoNotSendTwice() {
    String review = form();
    assertTrue(review.contains("/confirmar"));
    assertTrue(review.contains("Cantidad: 10"));
    assertEquals(0, writes.get());
    String result = bot.handle(1, "/confirmar");
    assertEquals(1, writes.get());
    assertTrue(result.contains("Referencia: created"));
    assertFalse(result.contains("{\""));
    assertTrue(body.get().contains("\"cantidad\":10"));
    bot.handle(1, "/confirmar");
    assertEquals(1, writes.get());
  }

  @Test
  void cancellationAndCorrectionNeverSendPrematureWrites() {
    form();
    bot.handle(1, "/atras");
    bot.handle(1, "12");
    bot.handle(1, "/cancelar");
    bot.handle(1, "/confirmar");
    assertEquals(0, writes.get());
  }

  @Test
  void invalidNumberDoesNotAdvanceAndConversationsAreIsolated() {
    bot.handle(1, "/crear_deposito");
    bot.handle(1, "Central");
    bot.handle(1, "Medrano");
    assertTrue(bot.handle(1, "mucho").contains("entero"));
    assertEquals(0, writes.get());
    bot.handle(2, "/donador");
    assertTrue(bot.handle(2, "/confirmar").contains("/menu"));
    assertTrue(bot.handle(1, "100").contains("/confirmar"));
  }

  @Test
  void donorCanReadReportsButCannotMutateComponents() {
    bot.handle(2, "/donador");
    assertTrue(bot.handle(2, "/realizar_donacion").contains("/admin"));
    assertTrue(bot.handle(2, "/consultas").contains("Ficha integral donador"));
    assertEquals(0, writes.get());
  }

  @Test
  void referenceLookupsKeepTheCurrentForm() {
    bot.handle(1, "/realizar_donacion");
    String lookup = bot.handle(1, "/listar_donadores");
    assertTrue(lookup.contains("Continuá el formulario"));
    assertTrue(bot.handle(1, "donor").contains("Paso 2"));
  }

  @Test
  void optionalFiltersCanBeSkippedAndMenusOfferReadableOperations() {
    assertTrue(bot.handle(1, "📊 Consultas").contains("Necesidades urgentes"));
    bot.handle(1, "/necesidades_urgentes");
    String result = "";
    for (int i = 0; i < 5; i++) result = bot.handle(1, "/omitir");
    assertFalse(result.contains("{\""));
    assertEquals(0, writes.get());
    assertFalse(bot.keyboard(1).isEmpty());
  }
}
