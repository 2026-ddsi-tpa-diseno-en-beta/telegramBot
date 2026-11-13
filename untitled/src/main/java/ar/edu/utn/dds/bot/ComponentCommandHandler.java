package ar.edu.utn.dds.bot;

import com.fasterxml.jackson.databind.*;
import java.util.*;

public class ComponentCommandHandler {
  private final Map<String, JsonNode> commands = new LinkedHashMap<>();
  private final ObjectMapper mapper = new ObjectMapper();
  private final ApiTransport transport;

  public ComponentCommandHandler(Map<String, String> urls) {
    transport = new ApiTransport(urls);
    try (var input = getClass().getResourceAsStream("/tools.json")) {
      for (JsonNode item : mapper.readTree(Objects.requireNonNull(input, "Falta catálogo")))
        commands.put("/" + item.path("name").asText(), item);
    } catch (Exception ex) {
      throw new IllegalStateException("No se pudo cargar el catálogo", ex);
    }
  }

  public boolean supports(String command) {
    return command.equals("/ayuda") || commands.containsKey(command);
  }

  public JsonNode operation(String name) {
    return commands.get(name);
  }

  public boolean requiresAdmin(String command) {
    JsonNode op = operation(command);
    // Self-registration remains available to the selected donor role.
    return op != null
        && !op.path("method").asText().equals("GET")
        && !command.equals("/crear_donador");
  }

  public List<JsonNode> operations(String component) {
    return commands.values().stream()
        .filter(op -> component.isBlank() || component.equals(op.path("component").asText()))
        .toList();
  }

  public String help(String component) {
    StringBuilder out =
        new StringBuilder(
            "Operaciones de DonaTrack\n"
                + "Elegí /operaciones para completar los datos paso a paso, sin escribir JSON.\n");
    for (JsonNode op : operations(component))
      out.append("\n/")
          .append(op.path("name").asText())
          .append(" — ")
          .append(op.path("description").asText().split("\\.", 2)[0]);
    return out.toString();
  }

  public String execute(String command, JsonNode arguments) throws Exception {
    return transport.execute(
        Objects.requireNonNull(operation(command)), mapper.convertValue(arguments, Map.class));
  }

  public String handle(String command, String args) throws Exception {
    if (command.equals("/ayuda")) return help(args.trim());
    JsonNode op = operation(command);
    if (op == null) return "Elegí una operación en /operaciones.";
    var input = mapper.createObjectNode();
    String body = args.trim();
    if (op.path("schema").path("properties").has("id")) {
      var parts = body.split("\\s+", 2);
      input.put("id", parts[0]);
      body = parts.length > 1 ? parts[1] : "";
    }
    String payload =
        op.path("schema").path("properties").has("datos")
            ? "datos"
            : op.path("schema").path("properties").has("filtros") ? "filtros" : null;
    if (payload != null && !body.isBlank()) input.set(payload, mapper.readTree(body));
    if ("datos".equals(payload)) {
      for (JsonNode required : op.path("schema").path("properties").path("datos").path("required"))
        if (!input.path("datos").hasNonNull(required.asText()))
          return "Falta el campo "
              + required.asText()
              + ". Escribí "
              + command
              + " sin datos para abrir el asistente.";
    }
    if (payload == null && !body.isBlank()) return "Este comando no recibe datos adicionales.";
    try {
      return mapper.readTree(execute(command, input)).path("resultado").toString();
    } catch (ApiTransport.ApiFailure ex) {
      JsonNode error = mapper.readTree(ex.getMessage());
      return ResponseFormatter.error(error.path("status").asInt(), error.path("traceId").asText());
    }
  }
}
