package com.jventas.backend.reporte;

import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reportes")
@RequiredArgsConstructor
public class ReporteController {

    private final ReporteService reporteService;

    @GetMapping("/cuentas-por-cobrar")
    @PreAuthorize("hasAuthority('venta:ver')")
    public List<CuentaPorCobrarResponse> cuentasPorCobrar() {
        return reporteService.cuentasPorCobrar();
    }

    @GetMapping("/cuentas-por-pagar")
    @PreAuthorize("hasAuthority('compra:ver')")
    public List<CuentaPorPagarResponse> cuentasPorPagar() {
        return reporteService.cuentasPorPagar();
    }
}
