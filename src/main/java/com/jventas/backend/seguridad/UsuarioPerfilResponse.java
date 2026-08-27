package com.jventas.backend.seguridad;

import java.util.List;

public record UsuarioPerfilResponse(String login, String nombreCompleto, List<String> authorities) {
}
