package ar.edu.utn.dds.bot;

import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Per-chat, expiring forms. Nothing is sent until a write is explicitly confirmed. */
public class GuidedOperations {
  private final ComponentCommandHandler api;
  private final ObjectMapper mapper = new ObjectMapper();
  private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

  private record Menu(long created, List<JsonNode> options) {}

  private final Map<Long, Menu> menus = new ConcurrentHashMap<>();

  private record Field(String parent, String name, JsonNode schema, boolean required) {}

  private static class Session {
    String command;
    long chat;
    List<Field> fields;
    int index;
    ObjectNode input;
    long touched = System.currentTimeMillis();
    boolean confirming;
    boolean running;
    List<String> choices = List.of();
  }

  public GuidedOperations(ComponentCommandHandler api) {
    this.api = api;
  }

  public String handle(long chat, String text, boolean admin) {
    sessions.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().touched > 600_000);
    menus.entrySet().removeIf(e -> System.currentTimeMillis() - e.getValue().created() > 600_000);
    text = alias(text.trim());
    if (text.equals("/cancelar")
        || text.equals("/menu")
        || text.equals("/start")
        || text.equals("/admin")
        || text.equals("/donador")) {
      sessions.remove(chat);
      menus.remove(chat);
      return text.equals("/cancelar")
          ? "Operación cancelada. No se enviaron cambios. Volvé a /menu."
          : null;
    }
    try {
      if (text.startsWith("/operaciones") || text.equals("/consultas")) {
        String group =
            text.equals("/consultas")
                ? "consultas"
                : text.substring("/operaciones".length()).trim();
        if (group.isBlank()) {
          menus.put(chat, new Menu(System.currentTimeMillis(), List.of()));
          sessions.remove(chat);
          return "¿Qué parte de DonaTrack querés usar? Elegí un componente con los botones.\n\n"
                     + "También podés abrir /consultas para los reportes integrados.";
        }
        var options =
            api.operations(group).stream()
                .filter(op -> admin || op.path("method").asText().equals("GET"))
                .toList();
        if (menus.size() >= 1000 && !menus.containsKey(chat))
          return "Hay muchas conversaciones abiertas. Volvé a intentar en unos minutos.";
        menus.put(chat, new Menu(System.currentTimeMillis(), options));
        sessions.remove(chat);
        StringBuilder out =
            new StringBuilder("Elegí una operación por número o escribí su comando.\n\n");
        int i = 1;
        for (JsonNode op : options)
          out.append(i++).append(". ").append(title(op.path("name").asText())).append("\n");
        return out.append(
                "\n"
                    + "/operaciones donaciones · /operaciones donadores\n"
                    + "/operaciones logistica · /operaciones incentivos\n"
                    + "/consultas · /cancelar")
            .toString();
      }
      if (text.matches("[0-9]+") && menus.containsKey(chat) && !sessions.containsKey(chat)) {
        int index = Integer.parseInt(text) - 1;
        var options = menus.get(chat).options();
        if (index < 0 || index >= options.size()) return "Elegí un número de la lista.";
        text = "/" + options.get(index).path("name").asText();
        menus.remove(chat);
      }
      Session s = sessions.get(chat);
      if (s != null && !text.startsWith("/")
          || s != null && Set.of("/confirmar", "/omitir", "/atras").contains(text)) {
        s.touched = System.currentTimeMillis();
        if (!admin && api.requiresAdmin(s.command)) {
          sessions.remove(chat);
          return "Seleccioná /admin para modificar datos.";
        }
        if (text.equals("/atras")) {
          s.confirming = false;
          s.index = Math.max(0, s.index - 1);
          return prompt(s);
        }
        if (s.confirming) {
          if (!text.equals("/confirmar"))
            return "Revisá el resumen y elegí /confirmar, /atras o /cancelar.";
          if (s.running) return "La operación ya se está enviando. Consultá su estado.";
          s.running = true;
          sessions.remove(chat);
          return run(s);
        }
        Field f = s.fields.get(s.index);
        if (text.equals("/omitir")) {
          if (f.required) return "Este dato es obligatorio.\n" + prompt(s);
          target(s, f).remove(f.name);
        } else {
          String value = text.contains(" · ") ? text.substring(text.lastIndexOf(" · ") + 3) : text;
          JsonNode parsed = parse(f, value);
          target(s, f).set(f.name, parsed);
        }
        s.index++;
        if (s.index < s.fields.size()) return prompt(s);
        return finish(chat, s);
      }
      String command = text.split("\\s+", 2)[0].split("@", 2)[0];
      JsonNode op = api.operation(command);
      if (op == null) return null;
      if (!admin && api.requiresAdmin(command))
        return "Seleccioná /admin para modificar datos. Podés explorar /consultas.";
      // Existing parameterized commands remain available; the menu always enters guided forms.
      if (text.split("\\s+", 2).length > 1) return null;
      if (s != null
          && op.path("method").asText().equals("GET")
          && op.path("schema").path("properties").isEmpty()) {
        Session lookup = new Session();
        lookup.command = command;
        lookup.input = mapper.createObjectNode();
        return run(lookup) + "\n\nContinuá el formulario:\n" + prompt(s);
      }
      s = new Session();
      s.chat = chat;
      s.command = command;
      s.fields = new ArrayList<>();
      s.input = mapper.createObjectNode();
      JsonNode schema = op.path("schema");
      var fields = schema.path("properties").fields();
      while (fields.hasNext()) {
        var field = fields.next();
        if (field.getValue().path("type").asText().equals("object")) {
          var children = field.getValue().path("properties").fields();
          while (children.hasNext()) {
            var child = children.next();
            s.fields.add(
                new Field(
                    field.getKey(),
                    child.getKey(),
                    child.getValue(),
                    required(field.getValue(), child.getKey())));
          }
        } else
          s.fields.add(
              new Field("", field.getKey(), field.getValue(), required(schema, field.getKey())));
      }
      if (sessions.size() >= 1000)
        return "Hay muchas conversaciones abiertas. Intentá nuevamente en unos minutos.";
      sessions.put(chat, s);
      return s.fields.isEmpty() ? finish(chat, s) : prompt(s);
    } catch (IllegalArgumentException ex) {
      return "Revisá el dato: " + ex.getMessage() + "\nPodés usar /atras o /cancelar.";
    } catch (Exception ex) {
      sessions.remove(chat);
      return "No se pudo completar. Consultá el estado antes de repetir una escritura. /menu";
    }
  }

  private String finish(long chat, Session s) throws Exception {
    if (api.operation(s.command).path("method").asText().equals("GET")) {
      sessions.remove(chat);
      return run(s);
    }
    s.confirming = true;
    return ResultPresenter.present("Confirmar: " + title(s.command.substring(1)), s.input)
        + "\n\n/confirmar para enviar · /atras para corregir · /cancelar";
  }

  private String run(Session s) throws Exception {
    try {
      JsonNode result = mapper.readTree(api.execute(s.command, s.input)).path("resultado");
      boolean read = api.operation(s.command).path("method").asText().equals("GET");
      return ResultPresenter.present((read ? "📋 " : "✅ ") + title(s.command.substring(1)), result)
          + (read
              ? "\n\n/consultas · /menu"
              : "\n\n"
                    + "Cambio enviado. Las consultas se actualizan de forma eventual.\n"
                    + "/consultas · /menu");
    } catch (ApiTransport.ApiFailure ex) {
      JsonNode error = mapper.readTree(ex.getMessage());
      String message =
          ResponseFormatter.error(error.path("status").asInt(), error.path("traceId").asText());
      if (Set.of(400, 404, 409, 422).contains(error.path("status").asInt())
          && s.fields != null
          && !s.fields.isEmpty()
          && !api.operation(s.command).path("method").asText().equals("GET")) {
        s.index = 0;
        s.running = false;
        s.confirming = false;
        sessions.put(s.chat, s);
        return message + "\n\nConservé los datos para corregirlos.\n" + prompt(s);
      }
      return message;
    }
  }

  private String prompt(Session s) {
    Field f = s.fields.get(s.index);
    s.choices = new ArrayList<>();
    f.schema.path("enum").forEach(v -> s.choices.add(v.asText()));
    if (s.choices.isEmpty()) s.choices.addAll(referenceChoices(s, f));
    String hint =
        switch (f.schema.path("type").asText()) {
          case "integer" -> "Escribí un número entero.";
          case "array" -> "Escribí las referencias separadas por coma.";
          default -> "Escribí el dato solicitado.";
        };
    if (f.name.endsWith("ID") || f.name.equals("id"))
      hint =
          "Usá una referencia existente. Podés consultar /listar_productos, /listar_donadores,"
              + " /listar_depositos o /listar_necesidades sin perder este paso.";
    return title(s.command.substring(1))
        + " · Paso "
        + (s.index + 1)
        + " de "
        + s.fields.size()
        + "\n\n"
        + title(f.name)
        + (f.required ? " (obligatorio)" : " (opcional)")
        + "\n"
        + hint
        + (s.choices.isEmpty() ? "" : "\nOpciones: " + String.join(" · ", s.choices))
        + (f.required ? "" : "\n/omitir para continuar sin este filtro/dato")
        + "\n/atras · /cancelar";
  }

  private ObjectNode target(Session s, Field f) {
    return f.parent.isBlank() ? s.input : s.input.with(f.parent);
  }

  private List<String> referenceChoices(Session session, Field field) {
    String resource =
        switch (field.name) {
          case "donadorID" -> "donadores";
          case "productoID", "productoSolicitadoID" -> "productos";
          case "depositoID" -> "depositos";
          case "entidadID" -> "entidades";
          case "categoriaID", "subcategoriaID", "categoriaPadreID" -> "categorias";
          case "insigniaID" -> "insignias";
          case "identificadorID" -> "identificadores";
          default -> "";
        };
    if (resource.isBlank()) return List.of();
    try {
      JsonNode records =
          mapper
              .readTree(api.execute("/listar_" + resource, mapper.createObjectNode()))
              .path("resultado");
      if (!records.isArray()) return List.of();
      List<String> choices = new ArrayList<>();
      for (JsonNode record : records) {
        if (choices.size() == 8) break;
        if (!record.hasNonNull("id")) continue;
        String name = record.path("nombre").asText(record.path("razonSocial").asText("Referencia"));
        if (record.hasNonNull("apellido")) name += " " + record.path("apellido").asText();
        choices.add(
            name.replace('\n', ' ').substring(0, Math.min(name.length(), 40))
                + " · "
                + record.path("id").asText());
      }
      return choices;
    } catch (Exception ex) {
      return List.of();
    }
  }

  private JsonNode parse(Field f, String value) {
    JsonNode parsed;
    try {
      parsed =
          switch (f.schema.path("type").asText()) {
            case "integer" -> mapper.valueToTree(Long.parseLong(value));
            case "array" -> mapper.valueToTree(
                Arrays.stream(value.split(","))
                    .map(String::trim)
                    .filter(v -> !v.isEmpty())
                    .toList());
            default -> mapper.valueToTree(value);
          };
    } catch (NumberFormatException ex) {
      throw new IllegalArgumentException("Se esperaba un número entero");
    }
    ApiTransport.validate(f.schema, parsed, f.name);
    return parsed;
  }

  private static boolean required(JsonNode schema, String name) {
    for (JsonNode n : schema.path("required")) if (n.asText().equals(name)) return true;
    return false;
  }

  static String title(String name) {
    String s = name.replace('_', ' ').replaceAll("([a-z])([A-Z])", "$1 $2");
    return Character.toUpperCase(s.charAt(0)) + s.substring(1);
  }

  public List<List<String>> keyboard(long chat) {
    Session s = sessions.get(chat);
    if (s != null) {
      if (s.confirming) return List.of(List.of("/confirmar", "/atras"), List.of("/cancelar"));
      List<List<String>> rows = new ArrayList<>();
      for (String option : s.choices) rows.add(List.of(option));
      if (!s.fields.get(s.index).required) rows.add(List.of("/omitir"));
      rows.add(List.of("/atras", "/cancelar"));
      return rows;
    }
    Menu menu = menus.get(chat);
    if (menu != null) {
      if (menu.options().isEmpty())
        return List.of(
            List.of("Donaciones · Operaciones", "Donadores y entidades · Operaciones"),
            List.of("Logística · Operaciones", "Incentivos · Operaciones"),
            List.of("📊 Consultas", "/menu"));
      List<List<String>> rows = new ArrayList<>();
      for (JsonNode op : menu.options())
        rows.add(List.of(title(op.path("name").asText()) + " · /" + op.path("name").asText()));
      rows.add(List.of("/operaciones", "/menu"));
      return rows;
    }
    return List.of(
        List.of("📊 Consultas", "🤝 Donar"),
        List.of("📦 Depósitos", "🧾 Donaciones"),
        List.of("🏢 Entidades", "🎯 Incentivos"),
        List.of("/menu", "/operaciones"));
  }

  public boolean hasContext(long chat) {
    return sessions.containsKey(chat) || menus.containsKey(chat);
  }

  public static String alias(String text) {
    if (text.contains(" · /")) return text.substring(text.lastIndexOf(" · /") + 3);
    return switch (text) {
      case "Donaciones · Operaciones" -> "/operaciones donaciones";
      case "Donadores y entidades · Operaciones" -> "/operaciones donadores";
      case "Logística · Operaciones" -> "/operaciones logistica";
      case "Incentivos · Operaciones" -> "/operaciones incentivos";
      case "/registrarse" -> "/crear_donador";
      case "📊 Consultas" -> "/consultas";
      case "🤝 Donar" -> "/realizar_donacion";
      case "📦 Depósitos" -> "/listar_depositos";
      case "🧾 Donaciones" -> "/listar_donaciones";
      case "🏢 Entidades" -> "/operaciones donadores";
      case "🎯 Incentivos" -> "/operaciones incentivos";
      default -> text;
    };
  }
}
