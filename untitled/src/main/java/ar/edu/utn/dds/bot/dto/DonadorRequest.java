package ar.edu.utn.dds.bot.dto;

public record DonadorRequest(
        String nombre,
        String apellido,
        int edad,
        String email,
        String nroDocumento,
        String domicilio
) {}
