package com.flowtask.excecao;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.flowtask.dto.ErroResposta;

/**
 * Erros simplesmente "de rota" não podem virar 500: rota inexistente é 404 e
 * método HTTP errado é 405, sem poluir o log como erro interno.
 */
class TratadorExcecoesTest {

    private final TratadorExcecoes tratador = new TratadorExcecoes();

    @Test
    void rotaInexistenteRetorna404() {
        ResponseEntity<ErroResposta> resposta =
                tratador.rotaInexistente(new NoResourceFoundException(HttpMethod.GET, "/api/rota/que-nao-existe"));
        assertEquals(404, resposta.getStatusCode().value());
        assertEquals(404, resposta.getBody().status());
        assertEquals("Recurso não encontrado.", resposta.getBody().mensagem());
    }

    @Test
    void metodoNaoSuportadoRetorna405() {
        ResponseEntity<ErroResposta> resposta =
                tratador.metodoNaoSuportado(new HttpRequestMethodNotSupportedException("DELETE"));
        assertEquals(405, resposta.getStatusCode().value());
        assertEquals(405, resposta.getBody().status());
    }
}
