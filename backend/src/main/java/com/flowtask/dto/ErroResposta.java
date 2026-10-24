package com.flowtask.dto;

import java.util.Map;

/**
 * Corpo padrão de erro da API.
 */
public record ErroResposta(int status, String mensagem, Map<String, String> erros) {

    public static ErroResposta simples(int status, String mensagem) {
        return new ErroResposta(status, mensagem, null);
    }
}
