package com.jventas.backend.catalogo;

public record CategoriaResponse(Long id, String nombre, Long categoriaPadreId, boolean requiereSerie) {

    public static CategoriaResponse from(Categoria categoria) {
        Long padreId = categoria.getCategoriaPadre() != null ? categoria.getCategoriaPadre().getId() : null;
        return new CategoriaResponse(categoria.getId(), categoria.getNombre(), padreId, categoria.isRequiereSerie());
    }
}
