package ar.edu.utn.dds.bot;

import com.sun.net.httpserver.HttpServer;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

class ComponentCommandHandlerTest {
  HttpServer server; ComponentCommandHandler components; BotCommandHandler handler;
  AtomicReference<String> call;
  @BeforeEach void setup() throws Exception {
    call=new AtomicReference<>(); server=HttpServer.create(new InetSocketAddress("127.0.0.1",0),0);
    server.createContext("/",exchange->{
      call.set(exchange.getRequestMethod()+" "+exchange.getRequestURI());
      byte[] data="{\"id\":\"nuevo\"}".getBytes(StandardCharsets.UTF_8);
      exchange.sendResponseHeaders(200,data.length); exchange.getResponseBody().write(data);exchange.close();
    }); server.start(); String url="http://127.0.0.1:"+server.getAddress().getPort();
    components=new ComponentCommandHandler(Map.of("donadores",url,"donaciones",url,"logistica",url,"incentivos",url));
    handler=new BotCommandHandler(new DonadoresApiClient(url),components);
  }
  @AfterEach void close() {server.stop(0);}
  @Test void selectionOfAdminIsRequired() {
    assertTrue(handler.handle(1,"/listar_productos").contains("/admin"));assertNull(call.get());
  }
  @Test void dispatchesConsultationsToAllFourComponents() {
    handler.handle(1,"/admin");
    for(var command:Map.of("/listar_productos","/productos","/listar_donadores","/donadores","/listar_depositos","/depositos","/listar_misiones","/misiones").entrySet()) {
      handler.handle(1,command.getKey());assertEquals("GET "+command.getValue(),call.get());
    }
  }
  @Test void supportsCommandsMentioningTheBotUsername() {
    handler.handle(1,"/admin");handler.handle(1,"/listar_productos@DonaTrackBot");assertEquals("GET /productos",call.get());
  }
  @Test void preservesLegacyPipeCommands() {
    handler.handle(1,"/admin");handler.handle(1,"/crear_entidad Escuela|Medrano|123|e@example.com");
    assertEquals("POST /entidades",call.get());
  }
  @Test void checksRequiredJsonFieldsBeforeCallingApi() {
    handler.handle(1,"/admin");assertTrue(handler.handle(1,"/realizar_donacion {\"cantidad\":1}").contains("Falta el campo"));assertNull(call.get());
  }
  @Test void helpExplainsOperationsForEveryComponent() {
    for(String component:List.of("donadores","donaciones","logistica","incentivos")) assertTrue(components.help(component).contains("/crear_"));
  }
  @Test void preservesCommandsAddedByTheGroup() {
    handler.handle(1,"/admin");
    handler.handle(1,"/depositos");assertEquals("GET /depositos",call.get());
    handler.handle(1,"/stock p");assertEquals("GET /stock/p",call.get());
    handler.handle(1,"/insignias");assertEquals("GET /insignias",call.get());
    handler.handle(1,"/procesar_donador d");assertEquals("POST /procesamiento/d",call.get());
  }
}
