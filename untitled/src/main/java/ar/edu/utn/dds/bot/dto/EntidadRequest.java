package ar.edu.utn.dds.bot.dto;

public record EntidadRequest(
        String razonSocial,
        String domicilio,
        String telefono,
        String correo
) {}
