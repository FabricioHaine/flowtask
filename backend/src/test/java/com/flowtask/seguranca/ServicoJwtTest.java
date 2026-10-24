package com.flowtask.seguranca;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.flowtask.config.PropriedadesApp;
import com.flowtask.dominio.Usuario;
import com.flowtask.suporte.SuporteTeste;

import io.jsonwebtoken.JwtException;

/**
 * Testes do serviço JWT: gerar token com identidade, validação de assinatura
 * e política do segredo (base64 e no mínimo 32 bytes).
 */
class ServicoJwtTest {

    private static final String SEGREDO_1 = Base64.getEncoder()
            .encodeToString("segredo-de-teste-01-0123456789abcdef-0123456789abcdef".getBytes(StandardCharsets.UTF_8));
    private static final String SEGREDO_2 = Base64.getEncoder()
            .encodeToString("segredo-de-teste-02-9876543210fedcba-fedcba9876543210".getBytes(StandardCharsets.UTF_8));

    private PropriedadesApp propriedades;
    private Usuario usuario;

    @BeforeEach
    void setUp() {
        propriedades = new PropriedadesApp();
        propriedades.getSeguranca().getJwt().setSegredo(SEGREDO_1);
        propriedades.getSeguranca().getJwt().setExpiracaoSegundos(900);
        usuario = SuporteTeste.usuario("Ana Souza", "ana@flowtask.dev", 42L);
    }

    @Test
    void deveGerarTokenComIdentidadeDoUsuario() {
        ServicoJwt servico = new ServicoJwt(propriedades);

        String token = servico.gerarToken(usuario);

        assertEquals(42L, servico.validarCapturarUsuarioId(token));
        assertEquals("ana@flowtask.dev", servico.validarCapturarEmail(token));
        assertEquals(900L, servico.getExpiracaoSegundos());
    }

    @Test
    void deveRecusarTokenAssinadoComOutraChave() {
        String token = new ServicoJwt(propriedades).gerarToken(usuario);

        propriedades.getSeguranca().getJwt().setSegredo(SEGREDO_2);
        ServicoJwt outroServico = new ServicoJwt(propriedades);

        assertThrows(JwtException.class, () -> outroServico.validarCapturarUsuarioId(token));
        assertThrows(JwtException.class, () -> outroServico.validarCapturarEmail(token));
    }

    @Test
    void deveRecusarSegredoComMenosDe32Bytes() {
        propriedades.getSeguranca().getJwt().setSegredo(
                Base64.getEncoder().encodeToString(new byte[16]));

        assertThrows(IllegalStateException.class, () -> new ServicoJwt(propriedades));
    }

    @Test
    void deveRecusarSegredoForaDeBase64() {
        propriedades.getSeguranca().getJwt().setSegredo("nao-base64!!!");

        assertThrows(IllegalStateException.class, () -> new ServicoJwt(propriedades));
    }
}
