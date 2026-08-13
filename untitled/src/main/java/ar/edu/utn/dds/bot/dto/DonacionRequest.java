package ar.edu.utn.dds.bot.dto;

public record DonacionRequest(
        String donadorID,
        String depositoID,
        String descripcion,
        String productoID,
        Integer cantidad
) {}
