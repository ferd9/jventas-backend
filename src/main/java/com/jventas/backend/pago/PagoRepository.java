package com.jventas.backend.pago;

import java.math.BigDecimal;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

public interface PagoRepository extends JpaRepository<Pago, Long> {

    List<Pago> findByCompraIdAndActivoTrue(Long compraId);

    List<Pago> findByVentaIdAndActivoTrue(Long ventaId);

    @Query("select coalesce(sum(p.monto), 0) from Pago p where p.compra.id = :compraId and p.activo = true")
    BigDecimal sumarPagadoDeCompra(Long compraId);

    @Query("select coalesce(sum(p.monto), 0) from Pago p where p.venta.id = :ventaId and p.activo = true")
    BigDecimal sumarPagadoDeVenta(Long ventaId);

    @Query("select p.compra.id as documentoId, sum(p.monto) as pagado from Pago p"
            + " where p.compra.id in :compraIds and p.activo = true group by p.compra.id")
    List<SaldoAgrupado> sumarPagadoPorCompras(List<Long> compraIds);

    @Query("select p.venta.id as documentoId, sum(p.monto) as pagado from Pago p"
            + " where p.venta.id in :ventaIds and p.activo = true group by p.venta.id")
    List<SaldoAgrupado> sumarPagadoPorVentas(List<Long> ventaIds);
}
