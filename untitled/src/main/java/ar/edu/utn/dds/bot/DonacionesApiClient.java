package ar.edu.utn.dds.bot;

import ar.edu.utn.dds.bot.dto.DonacionRequest;
import ar.edu.utn.dds.bot.dto.QuejaRequest;

import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

public class DonacionesApiClient {

    private final HttpClient httpClient;
    private final ObjectMapper objectMapper;
    private final String baseUrl;

    public DonacionesApiClient(String baseUrl) {
        if (baseUrl == null || baseUrl.isBlank())
            throw new IllegalArgumentException(
                    "La URL de Donaciones es obligatoria"
            );

        this.httpClient = HttpClient.newHttpClient();
        this.objectMapper = new ObjectMapper();
        this.baseUrl = removeTrailingSlash(baseUrl);
    }

    public String registrarDonacion(DonacionRequest request) throws IOException, InterruptedException {
        return post("/donaciones", objectMapper.writeValueAsString(request));
    }

    public String buscarDonacion(String id) throws IOException, InterruptedException {
        return get("/donaciones/" + encode(id));
    }

    public String buscarPorDonadorYFecha(String donadorID, String fecha) throws IOException, InterruptedException {
        return get(
                "/donaciones/search?donadorID="
                        + encode(donadorID)
                        + "&fecha="
                        + encode(fecha)
        );
    }

    public String registrarQueja(String donacionID, QuejaRequest request) throws IOException, InterruptedException {
        return post(
                "/donaciones/" + encode(donacionID) + "/quejas",
                objectMapper.writeValueAsString(request)
        );
    }

    public String cambiarEstado(String donacionID, String estado) throws IOException, InterruptedException {
        return patch("/donaciones/" + encode(donacionID) + "/estado?estado=" + encode(estado));
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
                        .header("Content-Type", "application/json")
                        .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                        .build();

        return send(request);
    }

    private String patch(String path) throws IOException, InterruptedException {
        HttpRequest request =
                HttpRequest.newBuilder()
                        .uri(URI.create(baseUrl + path))
                        .method("PATCH", HttpRequest.BodyPublishers.noBody())
                        .build();

        return send(request);
    }

    private String send(HttpRequest request) throws IOException, InterruptedException {
        HttpResponse<String> response =
                httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        if (response.statusCode() < 200 || response.statusCode() >= 300)
            throw new IllegalStateException(
                    "API respondió HTTP "
                            + response.statusCode()
                            + ": "
                            + response.body()
            );

        return response.body();
    }

    private String encode(String value) {
        if (value == null || value.isBlank())
            throw new IllegalArgumentException("El identificador es obligatorio");

        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    private String removeTrailingSlash(String value) {
        if (value.endsWith("/"))
            return value.substring(0, value.length() - 1);

        return value;
    }
}
