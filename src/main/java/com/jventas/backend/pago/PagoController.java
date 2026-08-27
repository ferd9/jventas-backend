package com.jventas.backend.pago;

import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/pagos")
@RequiredArgsConstructor
public class PagoController {

    private final PagoService pagoService;

    @GetMapping
    @PreAuthorize("hasAuthority('pago:ver')")
    public List<PagoResponse> listar(@RequestParam(required = false) Long compraId, @RequestParam(required = false) Long ventaId) {
        if (compraId != null) {
            return pagoService.listarPorCompra(compraId);
        }
        if (ventaId != null) {
            return pagoService.listarPorVenta(ventaId);
        }
        throw new IllegalArgumentException("Debe indicar compraId o ventaId");
    }

    @GetMapping("/saldo")
    @PreAuthorize("hasAuthority('pago:ver')")
    public SaldoResponse saldo(@RequestParam(required = false) Long compraId, @RequestParam(required = false) Long ventaId) {
        if (compraId != null) {
            return pagoService.saldoDeCompra(compraId);
        }
        if (ventaId != null) {
            return pagoService.saldoDeVenta(ventaId);
        }
        throw new IllegalArgumentException("Debe indicar compraId o ventaId");
    }

    @PostMapping
    @PreAuthorize("hasAuthority('pago:registrar')")
    public ResponseEntity<PagoResponse> registrar(@Valid @RequestBody PagoRequest request, Authentication authentication) {
        PagoResponse creado = pagoService.registrar(request, authentication.getName());
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }
}
