package com.jventas.backend.producto;

import com.jventas.backend.catalogo.CategoriaRepository;
import com.jventas.backend.catalogo.ImpuestoRepository;
import com.jventas.backend.catalogo.ListaPrecio;
import com.jventas.backend.catalogo.ListaPrecioRepository;
import com.jventas.backend.catalogo.MarcaRepository;
import com.jventas.backend.catalogo.ModeloRepository;
import com.jventas.backend.catalogo.UnidadMedidaRepository;
import com.jventas.backend.moneda.MonedaRepository;
import java.util.List;
import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class ProductoService {

    private final ProductoRepository productoRepository;
    private final ProductoPrecioRepository productoPrecioRepository;
    private final MonedaRepository monedaRepository;
    private final CategoriaRepository categoriaRepository;
    private final MarcaRepository marcaRepository;
    private final ModeloRepository modeloRepository;
    private final UnidadMedidaRepository unidadMedidaRepository;
    private final ImpuestoRepository impuestoRepository;
    private final ListaPrecioRepository listaPrecioRepository;

    @Transactional(readOnly = true)
    public Page<ProductoResumenResponse> listar(String q, Pageable pageable) {
        Page<Producto> pagina = StringUtils.hasText(q)
                ? productoRepository.buscarPorNombre(q, pageable)
                : productoRepository.findByActivoTrue(pageable);
        return pagina.map(ProductoResumenResponse::from);
    }

    @Transactional(readOnly = true)
    public ProductoDetalleResponse obtener(Long id) {
        Producto producto = obtenerEntidad(id);
        return ProductoDetalleResponse.from(producto, productoPrecioRepository.findByProductoId(id));
    }

    @Transactional
    public ProductoDetalleResponse crear(ProductoRequest request) {
        if (productoRepository.existsByCodigoBarras(request.codigoBarras())) {
            throw new IllegalArgumentException("Ya existe un producto con el código de barras " + request.codigoBarras());
        }
        if (productoRepository.existsByCodigo(request.codigo())) {
            throw new IllegalArgumentException("Ya existe un producto con el código " + request.codigo());
        }

        Producto producto = new Producto();
        aplicarCambios(producto, request);
        producto = productoRepository.save(producto);
        reemplazarPrecios(producto, request.precios());

        return obtener(producto.getId());
    }

    @Transactional
    public ProductoDetalleResponse actualizar(Long id, ProductoRequest request) {
        Producto producto = obtenerEntidad(id);

        if (!producto.getCodigoBarras().equals(request.codigoBarras())
                && productoRepository.existsByCodigoBarras(request.codigoBarras())) {
            throw new IllegalArgumentException("Ya existe un producto con el código de barras " + request.codigoBarras());
        }
        if (!producto.getCodigo().equals(request.codigo()) && productoRepository.existsByCodigo(request.codigo())) {
            throw new IllegalArgumentException("Ya existe un producto con el código " + request.codigo());
        }

        aplicarCambios(producto, request);
        productoRepository.save(producto);
        reemplazarPrecios(producto, request.precios());

        return obtener(id);
    }

    @Transactional
    public void eliminar(Long id) {
        Producto producto = obtenerEntidad(id);
        producto.setActivo(false);
        productoRepository.save(producto);
    }

    private Producto obtenerEntidad(Long id) {
        return productoRepository.findById(id).orElseThrow(() -> new NoSuchElementException("Producto no encontrado: " + id));
    }

    private void aplicarCambios(Producto producto, ProductoRequest request) {
        producto.setCodigoBarras(request.codigoBarras());
        producto.setCodigo(request.codigo());
        producto.setCodigoFabricante(request.codigoFabricante());
        producto.setNombre(request.nombre());
        producto.setCosto(request.costo());
        producto.setStockMinimo(request.stockMinimo());
        producto.setTipo(request.tipo());
        producto.setImagenUrl(request.imagenUrl());
        producto.setUbicacion(request.ubicacion());
        producto.setPeso(request.peso());

        producto.setMoneda(referencia(monedaRepository, request.monedaId(), "Moneda"));
        producto.setImpuesto(referenciaOpcional(impuestoRepository, request.impuestoId(), "Impuesto"));
        producto.setCategoria(referenciaOpcional(categoriaRepository, request.categoriaId(), "Categoría"));
        producto.setMarca(referenciaOpcional(marcaRepository, request.marcaId(), "Marca"));
        producto.setModelo(referenciaOpcional(modeloRepository, request.modeloId(), "Modelo"));
        producto.setUnidadMedida(referenciaOpcional(unidadMedidaRepository, request.unidadMedidaId(), "Unidad de medida"));
    }

    private void reemplazarPrecios(Producto producto, List<PrecioRequest> precios) {
        productoPrecioRepository.deleteByProductoId(producto.getId());
        for (PrecioRequest pr : precios) {
            ListaPrecio listaPrecio = referencia(listaPrecioRepository, pr.listaPrecioId(), "Lista de precio");
            productoPrecioRepository.save(new ProductoPrecio(producto, listaPrecio, pr.precio()));
        }
    }

    private <T, ID> T referencia(org.springframework.data.jpa.repository.JpaRepository<T, ID> repo, ID id, String nombreEntidad) {
        return repo.findById(id).orElseThrow(() -> new NoSuchElementException(nombreEntidad + " no encontrada: " + id));
    }

    private <T, ID> T referenciaOpcional(org.springframework.data.jpa.repository.JpaRepository<T, ID> repo, ID id, String nombreEntidad) {
        if (id == null) {
            return null;
        }
        return referencia(repo, id, nombreEntidad);
    }
}
