package com.jventas.backend.catalogo;

import java.util.NoSuchElementException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListaPrecioService {

    private final ListaPrecioRepository listaPrecioRepository;

    @Transactional
    public ListaPrecioResponse crear(ListaPrecioRequest request) {
        ListaPrecio listaPrecio = new ListaPrecio();
        listaPrecio.setNombre(request.nombre());
        return ListaPrecioResponse.from(listaPrecioRepository.save(listaPrecio));
    }

    @Transactional
    public ListaPrecioResponse actualizar(Long id, ListaPrecioRequest request) {
        ListaPrecio listaPrecio = listaPrecioRepository
                .findById(id)
                .orElseThrow(() -> new NoSuchElementException("Lista de precio no encontrada: " + id));
        listaPrecio.setNombre(request.nombre());
        return ListaPrecioResponse.from(listaPrecioRepository.save(listaPrecio));
    }
}
