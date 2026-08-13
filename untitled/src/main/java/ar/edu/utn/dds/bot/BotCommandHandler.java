package ar.edu.utn.dds.bot;

import ar.edu.utn.dds.bot.dto.DonacionRequest;
import ar.edu.utn.dds.bot.dto.DonadorRequest;
import ar.edu.utn.dds.bot.dto.EntidadRequest;
import ar.edu.utn.dds.bot.dto.NecesidadRequest;
import ar.edu.utn.dds.bot.dto.QuejaRequest;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class BotCommandHandler {

    private final DonadoresApiClient api;
    private final DonacionesApiClient donacionesApi;
    private final Map<Long, BotRole> roles = new ConcurrentHashMap<>();

    public BotCommandHandler(
            DonadoresApiClient api,
            DonacionesApiClient donacionesApi
    ) {
        this.api = api;
        this.donacionesApi = donacionesApi;
    }

    public String handle(long chatId, String text) {
        if (text == null || text.isBlank())
            return "No entendí el comando.";

        String[] parts = text.trim().split("\\s+", 2);
        String command = parts[0].toLowerCase();

        String args =
                parts.length > 1
                        ? parts[1].trim()
                        : "";

        try {
            return switch (command) {
                case "/start" ->
                        start();
                case "/donador" ->
                        selectRole(
                                chatId,
                                BotRole.DONADOR
                        );
                case "/admin" ->
                        selectRole(
                                chatId,
                                BotRole.ADMIN
                        );
                case "/menu" ->
                        menu(chatId);

                // DONADORES
                case "/registrarse" ->
                        requireRole(
                                chatId,
                                BotRole.DONADOR,
                                () -> registrarDonador(args)
                        );
                case "/estadisticas" ->
                        requireRole(
                                chatId,
                                BotRole.DONADOR,
                                () -> estadisticas(args)
                        );
                case "/donador_id" ->
                        requireRole(
                                chatId,
                                BotRole.DONADOR,
                                () -> buscarDonador(args)
                        );
                case "/donadores" ->
                        requireRole(
                                chatId,
                                BotRole.DONADOR,
                                api::listarDonadores
                        );

                // ADMIN
                case "/crear_entidad" ->
                        requireRole(
                                chatId,
                                BotRole.ADMIN,
                                () -> crearEntidad(args)
                        );
                case "/entidades" ->
                        requireRole(
                                chatId,
                                BotRole.ADMIN,
                                api::listarEntidades
                        );
                case "/entidad" ->
                        requireRole(
                                chatId,
                                BotRole.ADMIN,
                                () -> buscarEntidad(args)
                        );
                case "/alta_necesidad" ->
                        requireRole(
                                chatId,
                                BotRole.ADMIN,
                                () -> crearNecesidad(args)
                        );
                case "/necesidades_producto" ->
                        requireRole(
                                chatId,
                                BotRole.ADMIN,
                                () -> necesidadesPorProducto(args)
                        );
                case "/necesidad" ->
                        requireRole(
                                chatId,
                                BotRole.ADMIN,
                                () -> buscarNecesidad(args)
                        );
                case "/editar_entidad" ->
                        requireRole(
                                chatId,
                                BotRole.ADMIN,
                                () -> editarEntidad(args)
                        );
                case "/borrar_necesidad" ->
                        requireRole(
                                chatId,
                                BotRole.ADMIN,
                                () -> borrarNecesidad(args)
                        );
                case "/modificar_necesidad" ->
                        requireRole(
                                chatId,
                                BotRole.ADMIN,
                                () -> modificarNecesidad(args)
                        );
                case "/cambiar_estado" ->
                        requireRole(
                                chatId,
                                BotRole.ADMIN,
                                () -> cambiarEstado(args)
                        );

                // DONACIONES (DONADOR)
                case "/registrar_donacion" ->
                        requireRole(
                                chatId,
                                BotRole.DONADOR,
                                () -> registrarDonacion(args)
                        );
                case "/mis_donaciones" ->
                        requireRole(
                                chatId,
                                BotRole.DONADOR,
                                () -> misDonaciones(args)
                        );
                case "/donacion" ->
                        requireRole(
                                chatId,
                                BotRole.DONADOR,
                                () -> buscarDonacion(args)
                        );
                case "/queja" ->
                        requireRole(
                                chatId,
                                BotRole.DONADOR,
                                () -> registrarQueja(args)
                        );

                default ->
                        "Comando desconocido.\n\n"
                                + help();
            };
        } catch (Exception e) {
            return "❌ No se pudo realizar la operación.\n"
                    + e.getMessage();
        }
    }

    // =========================
    // MENÚ
    // =========================
    private String start() {
        return """
                Bienvenido a DonaTrack 🤝

                Seleccioná tu tipo de usuario:

                /donador
                /admin
                """;
    }

    private String selectRole(long chatId, BotRole role) {
        roles.put(chatId, role);

        return switch (role) {
            case DONADOR -> """
                    Rol seleccionado: DONADOR.

                    Comandos:

                    /registrarse nombre|apellido|edad|email|documento|domicilio
                    /estadisticas ID
                    /donador_id ID
                    /donadores

                    /registrar_donacion donadorID|depositoID|descripcion|productoID|cantidad
                    /mis_donaciones donadorID|fecha (ej: 2025-01-01)
                    /donacion ID
                    /queja donacionID|descripcion

                    /menu
                    """;
            case ADMIN -> """
                    Rol seleccionado: ADMIN.

                    Comandos:

                    /crear_entidad razonSocial|domicilio|telefono|correo
                    /editar_entidad ID|razonSocial|domicilio|telefono|correo
                    /entidad ID
                    /entidades

                    /alta_necesidad entidadID|productoID|descripcion|cantidad|urgencia|tipo
                    /modificar_necesidad ID|entidadID|productoID|descripcion|cantidad|urgencia|tipo
                    /borrar_necesidad ID
                    /necesidad ID
                    /necesidades_producto productoID

                    /cambiar_estado donacionID|estado (INGRESADA|ACEPTADA|CONQUEJA)

                    /menu
                    """;
        };
    }

    private String menu(long chatId) {
        BotRole role = roles.get(chatId);
        if (role == null) return start();

        return selectRole(
                chatId,
                role
        );
    }

    // =========================
    // DONADORES
    // =========================
    private String registrarDonador(String args) throws Exception {
        String[] values = split(args, 6);

        return api.registrarDonador(
                new DonadorRequest(
                        values[0],
                        values[1],
                        Integer.parseInt(values[2]),
                        values[3],
                        values[4],
                        values[5]
                )
        );
    }

    private String estadisticas(String args) throws Exception {
        requireArgument(args);
        return api.estadisticasDonador(args);
    }

    private String buscarDonador(String args) throws Exception {
        requireArgument(args);
        return api.buscarDonador(args);
    }

    // =========================
    // ENTIDADES
    // =========================
    private String crearEntidad(String args) throws Exception {
        String[] values = split(args, 4);

        return api.crearEntidad(
                new EntidadRequest(
                        values[0],
                        values[1],
                        values[2],
                        values[3]
                )
        );
    }

    private String buscarEntidad(String args) throws Exception {
        requireArgument(args);
        return api.buscarEntidad(args);
    }

    private String editarEntidad(String args) throws Exception {
        String[] values = split(args, 5);

        return api.modificarEntidad(
                values[0],
                new EntidadRequest(
                        values[1],
                        values[2],
                        values[3],
                        values[4]
                )
        );
    }

    // =========================
    // NECESIDADES
    // =========================
    private String crearNecesidad(String args) throws Exception {
        String[] values = split(args, 6);

        return api.crearNecesidad(
                new NecesidadRequest(
                        values[0],
                        values[1],
                        values[2],
                        Integer.parseInt(values[3]),
                        Integer.parseInt(values[4]),
                        values[5]
                )
        );
    }

    private String necesidadesPorProducto(String args) throws Exception {
        requireArgument(args);
        return api.necesidadesPorProducto(args);
    }

    private String buscarNecesidad(String args) throws Exception {
        requireArgument(args);
        return api.buscarNecesidad(args);
    }

    private String borrarNecesidad(String args) throws Exception {
        requireArgument(args);
        return api.borrarNecesidad(args);
    }

    private String modificarNecesidad(String args) throws Exception {
        String[] values = split(args, 7);

        return api.modificarNecesidad(
                values[0],
                new NecesidadRequest(
                        values[1],
                        values[2],
                        values[3],
                        Integer.parseInt(values[4]),
                        Integer.parseInt(values[5]),
                        values[6]
                )
        );
    }

    // =========================
    // DONACIONES
    // =========================
    private String registrarDonacion(String args) throws Exception {
        requireDonaciones();
        String[] values = split(args, 5);

        return donacionesApi.registrarDonacion(
                new DonacionRequest(
                        values[0],
                        values[1],
                        values[2],
                        values[3],
                        Integer.parseInt(values[4])
                )
        );
    }

    private String misDonaciones(String args) throws Exception {
        requireDonaciones();
        String[] values = split(args, 2);
        return donacionesApi.buscarPorDonadorYFecha(values[0], values[1]);
    }

    private String buscarDonacion(String args) throws Exception {
        requireDonaciones();
        requireArgument(args);
        return donacionesApi.buscarDonacion(args);
    }

    private String registrarQueja(String args) throws Exception {
        requireDonaciones();
        String[] values = split(args, 2);
        return donacionesApi.registrarQueja(values[0], new QuejaRequest(values[1]));
    }

    private String cambiarEstado(String args) throws Exception {
        requireDonaciones();
        String[] values = split(args, 2);
        return donacionesApi.cambiarEstado(values[0], values[1]);
    }

    private void requireDonaciones() {
        if (donacionesApi == null)
            throw new IllegalStateException(
                    "El servicio de Donaciones no está configurado.\n"
                            + "Agregá integrations.donaciones-url en application.properties."
            );
    }

    // =========================
    // SEGURIDAD DE ROL
    // =========================
    private String requireRole(
            long chatId,
            BotRole expected,
            CommandAction action
    ) throws Exception {
        BotRole current = roles.get(chatId);

        if (current != expected)
            return "Primero seleccioná el rol correcto con:\n\n"
                    + (
                    expected == BotRole.DONADOR
                            ? "/donador"
                            : "/admin"
            );

        return action.execute();
    }

    // =========================
    // VALIDACIONES
    // =========================
    private String[] split(String args, int expected) {
        if (args == null || args.isBlank()) 
            throw new IllegalArgumentException(
                    "Faltan parámetros."
            );

        String[] values = args.split("\\|", -1);

        if (values.length != expected)
            throw new IllegalArgumentException(
                    "Se esperaban "
                            + expected
                            + " parámetros separados por '|'."
            );

        for (String value : values) {
            if (value.isBlank())
                throw new IllegalArgumentException(
                        "No puede haber parámetros vacíos."
                );
        }

        return values;
    }

    private void requireArgument(String args) {
        if (args == null || args.isBlank()) 
            throw new IllegalArgumentException(
                    "Debe indicar un ID."
            );
    }

    private String help() {
        return """
                Comandos principales:

                /start
                /donador
                /admin
                /menu
                """;
    }

    @FunctionalInterface
    private interface CommandAction {
        String execute() throws Exception;
    }
}