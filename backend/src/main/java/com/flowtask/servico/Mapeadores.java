package com.flowtask.servico;

import com.flowtask.dominio.Usuario;
import com.flowtask.dto.UsuarioResumo;

/** Conversores comuns de entidades em DTOs de resposta. */
public final class Mapeadores {

    private Mapeadores() {
    }

    public static UsuarioResumo resumo(Usuario usuario) {
        if (usuario == null) {
            return null;
        }
        return new UsuarioResumo(usuario.getId(), usuario.getNome(), usuario.getEmail());
    }
}
