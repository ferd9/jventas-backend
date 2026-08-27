package com.jventas.backend.usuario;

/** passwordNueva nula = se genera una aleatoria y se devuelve una sola vez en la respuesta. */
public record ResetearPasswordRequest(String passwordNueva) {
}
