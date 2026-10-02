package ar.edu.utn.dds.bot;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Only presents API data: states and quantities are never inferred or changed. */
final class ResponseFormatter {
    private static final ObjectMapper JSON = new ObjectMapper();
    private static final Map<String, String> LABELS = Map.ofEntries(
        Map.entry("id", "Referencia"), Map.entry("productoSolicitadoID", "Producto"),
        Map.entry("cantidadObjetivo", "Cantidad requerida"), Map.entry("cantidadAsignada", "Cantidad asignada"),
        Map.entry("cantidadDonada", "Cantidad recibida"), Map.entry("cantidad", "Cantidad"),
        Map.entry("estado", "Estado"), Map.entry("nivelDeUrgencia", "Urgencia"),
        Map.entry("razonSocial", "Entidad"), Map.entry("productoId", "Producto"),
        Map.entry("productoID", "Producto"), Map.entry("donadorId", "Donador"),
        Map.entry("donadorID", "Donador"), Map.entry("entidadID", "Entidad"),
        Map.entry("depositoId", "Depósito"), Map.entry("necesidadId", "Necesidad"),
        Map.entry("paqueteId", "Paquete"), Map.entry("capacidadMaxima", "Capacidad máxima"),
        Map.entry("stockActual", "Paquetes en depósito"), Map.entry("origenAsignacion", "Origen de asignación"));

    static List<String> pages(String response) {
        String text = response;
        try {
            JsonNode data = JSON.readTree(response);
            if (data != null && (data.isContainerNode() || data.isBoolean() || data.isNumber())) {
                StringBuilder rendered = new StringBuilder("Resultado de la consulta/operación\n");
                if (data.isArray() && data.isEmpty()) rendered.append("No hay resultados.\n");
                else append(data, rendered, 0);
                text = rendered.toString().strip();
            }
        } catch (Exception ignored) { /* Existing command/help text remains text. */ }
        List<String> pages = new ArrayList<>();
        StringBuilder page = new StringBuilder();
        for (String line : text.split("\n", -1)) {
            // Never discard fields, even when an API returns an unusually long description.
            while (line.length() > 2800) {
                if (!page.isEmpty()) { pages.add(page.toString().strip()); page.setLength(0); }
                int end = 2800;
                if (Character.isHighSurrogate(line.charAt(end - 1))) end--;
                pages.add(line.substring(0, end)); line = line.substring(end);
            }
            if (page.length() + line.length() > 2900) { pages.add(page.toString().strip()); page.setLength(0); }
            page.append(line).append('\n');
        }
        if (!page.isEmpty()) pages.add(page.toString().strip());
        return pages.isEmpty() ? List.of("Operación completada.") : List.copyOf(pages);
    }

    private static void append(JsonNode node, StringBuilder text, int depth) {
        String indent = "  ".repeat(Math.min(depth, 6));
        if (node.isObject()) {
            node.fields().forEachRemaining(field -> {
                String label = LABELS.getOrDefault(field.getKey(), label(field.getKey()));
                text.append(indent).append(label).append(':');
                if (field.getValue().isContainerNode()) { text.append('\n'); append(field.getValue(), text, depth + 1); }
                else text.append(' ').append(value(field.getValue())).append('\n');
            });
        } else if (node.isArray()) {
            int index = 1;
            for (JsonNode item : node) {
                text.append(indent).append("• ").append(index++).append('\n');
                append(item, text, depth + 1);
            }
            if (node.isEmpty()) text.append(indent).append("Sin registros\n");
        } else text.append(indent).append(value(node)).append('\n');
    }

    private static String value(JsonNode node) {
        if (node.isNull()) return "Sin informar";
        if (node.isBoolean()) return node.asBoolean() ? "Sí" : "No";
        String value = node.asText();
        // Keep enum spellings to make comparison with Swagger and Claude unambiguous.
        return value;
    }
    private static String label(String key) {
        String spaced = key.replaceAll("([a-z])([A-Z])", "$1 $2").replace('_', ' ');
        return spaced.isEmpty() ? key : Character.toUpperCase(spaced.charAt(0)) + spaced.substring(1);
    }

    static String error(int status, String trace) {
        String message = switch (status) {
            case 400, 422 -> "Revisá los datos y el formato del comando en /ayuda.";
            case 401, 403 -> "No tenés permiso para realizar esta operación.";
            case 404 -> "No se encontró el recurso. Revisá su referencia.";
            case 409 -> "El estado actual no permite la operación. Consultá el recurso antes de continuar.";
            default -> "El componente no pudo responder correctamente. Consultá el estado antes de repetir una operación que modifica datos.";
        };
        return "❌ " + message + "\nCódigo HTTP: " + status + "\nReferencia de soporte: " + trace;
    }
}
