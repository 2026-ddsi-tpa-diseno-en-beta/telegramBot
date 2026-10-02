package ar.edu.utn.dds.bot;

import com.fasterxml.jackson.databind.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;

/** Additional commands for all four components; domain validation stays in their APIs. */
public class ComponentCommandHandler {
  private final Map<String, String> urls;
  private final Map<String, JsonNode> commands = new LinkedHashMap<>();
  private final ObjectMapper mapper = new ObjectMapper();
  private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(15)).build();
  public ComponentCommandHandler(Map<String, String> urls) {
    this.urls = Map.copyOf(urls);
    try (var input = getClass().getResourceAsStream("/tools.json")) {
      for (JsonNode item : mapper.readTree(Objects.requireNonNull(input, "Falta tools.json"))) commands.put("/" + item.path("name").asText(), item);
    } catch (Exception ex) { throw new IllegalStateException("No se pudieron cargar los comandos", ex); }
  }
  public boolean supports(String command) { return command.equals("/ayuda") || commands.containsKey(command); }
  public String help(String component) {
    StringBuilder result = new StringBuilder("Comandos de DonaTrack");
    if (!component.isBlank()) result.append(" · ").append(component);
    result.append("\nUsá /ayuda donaciones, donadores, logistica o incentivos.\n");
    commands.forEach((name, operation) -> {
      if (component.isBlank() || component.equals(operation.path("component").asText())) {
        result.append("\n").append(name);
        if (operation.path("path").asText().contains("{id}")) result.append(" ID");
        if (operation.path("schema").path("properties").has("datos")) {
          result.append(" JSON { ");
          operation.path("schema").path("properties").path("datos").path("properties").fieldNames().forEachRemaining(f -> result.append(f).append(" "));
          result.append("}");
        }
      }
    });
    return result.toString();
  }
  public String handle(String command, String args) throws Exception {
    if (command.equals("/ayuda")) return help(args.trim());
    JsonNode operation = commands.get(command);
    if (operation == null) return "Comando desconocido. Usá /ayuda.";
    String component = operation.path("component").asText();
    String url = urls.get(component);
    if (url == null || url.isBlank()) return "Todavía no se configuró el componente " + component + ".";
    String path = operation.path("path").asText();
    String body = args;
    if (path.contains("{id}")) {
      String[] pieces = args.trim().split("\\s+", 2);
      if (pieces[0].isBlank() || pieces[0].contains("/") || pieces[0].contains("\\") || pieces[0].equals(".") || pieces[0].equals("..")) return "Indicá un ID válido.";
      path = path.replace("{id}", java.net.URLEncoder.encode(pieces[0], java.nio.charset.StandardCharsets.UTF_8).replace("+", "%20"));
      body = pieces.length == 2 ? pieces[1] : "";
    }
    String trace = UUID.randomUUID().toString();
    var builder = HttpRequest.newBuilder(URI.create(url.replaceAll("/+$", "") + path))
        .timeout(Duration.ofSeconds(180)).header("Accept", "application/json")
        .header("X-Trace-Id", trace);
    String method = operation.path("method").asText();
    if (operation.path("schema").path("properties").has("datos")) {
      JsonNode data = mapper.readTree(body);
      if (data == null || !data.isObject()) return "Indicá los datos en un objeto JSON. Consultá /ayuda " + component + ".";
      for (JsonNode field : operation.path("schema").path("properties").path("datos").path("required"))
        if (!data.hasNonNull(field.asText())) return "Falta el campo " + field.asText() + ".";
      builder.header("Content-Type", "application/json").method(method, HttpRequest.BodyPublishers.ofString(mapper.writeValueAsString(data)));
    } else {
      if (!body.isBlank()) return "Este comando no recibe datos adicionales.";
      builder.method(method, HttpRequest.BodyPublishers.noBody());
    }
    HttpResponse<String> response;
    long start = System.nanoTime();
    try { response = client.send(builder.build(), HttpResponse.BodyHandlers.ofString());
      ClientEvents.completed(component, response.statusCode(), trace, start);
    } catch (java.io.IOException ex) {
      ClientEvents.completed(component, 0, trace, start);
      return "No se pudo contactar el componente. Referencia: " + trace + ". Consultá el recurso antes de repetir una operación.";
    }
    catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw ex; }
    if (response.statusCode() < 200 || response.statusCode() >= 300)
      return ResponseFormatter.error(response.statusCode(), trace);
    if (response.body().isBlank()) return method.equals("DELETE") ? "✅ Recurso eliminado." : "✅ Operación completada.";
    return response.body();
  }
}
