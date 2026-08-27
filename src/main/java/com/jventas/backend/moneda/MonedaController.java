package com.jventas.backend.moneda;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Slice de referencia para el resto de módulos: entidad -> repositorio ->
 * servicio -> controller, DTO de salida separado de la entidad JPA.
 */
@RestController
@RequestMapping("/api/monedas")
@RequiredArgsConstructor
public class MonedaController {

    private final MonedaService monedaService;

    @GetMapping
    public List<MonedaResponse> listar() {
        return monedaService.listarActivas();
    }
}
