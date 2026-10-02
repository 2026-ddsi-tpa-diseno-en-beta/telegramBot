package ar.edu.utn.dds.bot;

import ar.edu.utn.dds.bot.dto.DonadorRequest;
import ar.edu.utn.dds.bot.dto.EntidadRequest;
import ar.edu.utn.dds.bot.dto.NecesidadRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class DonadoresApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public DonadoresApiClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank())
            throw new IllegalArgumentException(
                    "La URL de Donadores y Entidades es obligatoria"
            );

        this.httpClient = HttpClient.newBuilder().connectTimeout(java.time.Duration.ofSeconds(15)).build();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = removeTrailingSlash(baseUrl);
    }

    // =========================
    // DONADORES
    // =========================
    public String registrarDonador(DonadorRequest request) throws IOException, InterruptedException {
        return post(
                "/donadores",
                objectMapper.writeValueAsString(request)
        );
    }

    public String buscarDonador(String id) throws IOException, InterruptedException {
        return get("/donadores/" + encode(id));
    }

    public String listarDonadores() throws IOException, InterruptedException {
        return get("/donadores");
    }

    public String estadisticasDonador(String id) throws IOException, InterruptedException {
        return get(
                "/donadores/"
                        + encode(id)
                        + "/estadisticas"
        );
    }

    // =========================
    // ENTIDADES
    // =========================
    public String crearEntidad(EntidadRequest request) throws IOException, InterruptedException {
        return post(
                "/entidades",
                objectMapper.writeValueAsString(request)
        );
    }

    public String buscarEntidad(String id) throws IOException, InterruptedException {
        return get("/entidades/" + encode(id));
    }

    public String listarEntidades()throws IOException, InterruptedException {
        return get("/entidades");
    }

    public String modificarEntidad(String id, EntidadRequest request) throws IOException, InterruptedException {
        return put(
                "/entidades/" + encode(id),
                objectMapper.writeValueAsString(request)
        );
    }

    // =========================
    // NECESIDADES
    // =========================
    public String crearNecesidad(NecesidadRequest request) throws IOException, InterruptedException {
        return post(
                "/necesidades",
                objectMapper.writeValueAsString(request)
        );
    }

    public String necesidadesPorProducto(String productoId) throws IOException, InterruptedException {
        return get(
                "/necesidades?productoSolicitadoID="
                        + encode(productoId)
        );
    }

    public String buscarNecesidad(String id) throws IOException, InterruptedException {
        return get("/necesidades/" + encode(id));
    }

    public String modificarNecesidad(String id, NecesidadRequest request) throws IOException, InterruptedException {
        return put(
                "/necesidades/" + encode(id),
                objectMapper.writeValueAsString(request)
        );
    }

    public String borrarNecesidad(String id) throws IOException, InterruptedException {
        return delete("/necesidades/" + encode(id));
    }

    // =========================
    // HTTP
    // =========================
    private String get(String path) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .GET()
                        .build();

        return send(request);
    }

    private String post(String path, String body) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .POST(
                                HttpRequest.BodyPublishers.ofString(
                                        body,
                                        StandardCharsets.UTF_8
                                )
                        )
                        .build();

        return send(request);
    }

    private String put(String path, String body) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .header(
                                "Content-Type",
                                "application/json"
                        )
                        .PUT(
                                HttpRequest.BodyPublishers.ofString(
                                        body,
                                        StandardCharsets.UTF_8
                                )
                        )
                        .build();

        return send(request);
    }

    private String delete(String path) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .DELETE()
                        .build();

        return send(request);
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        String trace = java.util.UUID.randomUUID().toString();
        request = HttpRequest.newBuilder(request, (name, value) -> true)
                .timeout(java.time.Duration.ofSeconds(180))
                .header("X-Trace-Id", trace).build();
        long start = System.nanoTime();
        HttpResponse<String> response;
        try { response = httpClient.send(request, HttpResponse.BodyHandlers.ofString()); }
        catch (IOException ex) {
            ClientEvents.completed("donadores", 0, trace, start);
            throw new IOException("No se pudo contactar Donadores. Referencia: " + trace + ". Consultá el estado antes de repetir una operación.");
        }
        ClientEvents.completed("donadores", response.statusCode(), trace, start);

        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException(
                    ResponseFormatter.error(response.statusCode(), trace)
            );

        return response.body();
    }

    private String encode(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException(
                    "El identificador es obligatorio"
            );

        return URLEncoder.encode(
                value,
                StandardCharsets.UTF_8
        );
    }

    private String removeTrailingSlash(String value) {
        if (value.endsWith("/"))
            return value.substring(
                    0,
                    value.length() - 1
            );

        return value;
    }
}
