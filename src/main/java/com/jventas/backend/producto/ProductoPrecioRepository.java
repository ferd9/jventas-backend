package com.jventas.backend.producto;

import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProductoPrecioRepository extends JpaRepository<ProductoPrecio, ProductoPrecioId> {

    List<ProductoPrecio> findByProductoId(Long productoId);

    void deleteByProductoId(Long productoId);
}
