package com.jventas.backend.almacen;

import com.jventas.backend.direccion.DireccionRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record AlmacenRequest(@NotBlank String nombre, @NotNull @Valid DireccionRequest direccion) {
}
