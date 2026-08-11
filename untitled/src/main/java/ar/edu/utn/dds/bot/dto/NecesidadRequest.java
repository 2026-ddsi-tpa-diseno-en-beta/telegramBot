package ar.edu.utn.dds.bot.dto;

public record NecesidadRequest(
        String entidadID,
        String productoSolicitadoID,
        String descripcion,
        int cantidadObjetivo,
        int nivelDeUrgencia,
        String tipo
) {}
