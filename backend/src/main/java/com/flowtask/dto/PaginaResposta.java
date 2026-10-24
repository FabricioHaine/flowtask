package com.flowtask.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.util.List;

/** Wrapper de resposta paginada reutilizado por listagens da API. */
public record PaginaResposta<T>(List<T> conteudo, int pagina, int tamanho, long total) {

    public static <T> PaginaResposta<T> de(List<T> conteudo, int pagina, int tamanho, long total) {
        return new PaginaResposta<>(conteudo, pagina, tamanho, total);
    }

    public static <T> PaginaResposta<T> vazia(int pagina, int tamanho) {
        return new PaginaResposta<>(List.of(), pagina, tamanho, 0);
    }
}
