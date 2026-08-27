package com.jventas.backend.usuario;

import jakarta.validation.constraints.NotBlank;

public record CargoRequest(@NotBlank String nombre) {}
