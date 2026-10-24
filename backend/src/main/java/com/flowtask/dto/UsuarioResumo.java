package com.flowtask.dto;

/** Resumo anônimo/leve de um usuário para exibição (comentários, responsáveis etc.). */
public record UsuarioResumo(Long id, String nome, String email) {
}
