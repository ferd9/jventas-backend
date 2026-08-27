package com.jventas.backend.usuario;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.Set;

public record RolRequest(@NotBlank String nombre, String descripcion, @NotNull Set<Long> permisoIds) {}
