package com.jventas.backend.producto;

import java.util.List;

/**
 * Forma pública del catálogo -- sin costo interno, ubicación de almacén, stock
 * mínimo, ni cantidad exacta en stock. "disponible" es solo un sí/no (hay
 * stock en algún almacén activo o no), no el número real.
 */
public record ProductoPublicoResponse(
        Long id,
        String codigo,
        String nombre,
        String categoriaNombre,
        String marcaNombre,
        String monedaNombre,
        String imagenUrl,
        boolean disponible,
        List<PrecioResponse> precios) {}
