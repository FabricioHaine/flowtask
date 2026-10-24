package com.flowtask.seguranca;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowtask.dto.ErroResposta;
import com.flowtask.excecao.RegraNegocioException;

import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Extrai o token "Authorization: Bearer ..." e autentica a requisição.
 * Token ausente (rotas públicas) → segue sem autenticação; endpoints protegidos
 * respondem 401 pelo SecurityFilterChain.
 * Token inválido e expirado → responde 401 imediatamente, com mensagem clara.
 */
@Component
public class FiltroJwt extends OncePerRequestFilter {

    public static final String CABECALHO = "Authorization";
    public static final String PREFIXO = "Bearer ";

    private final ServicoJwt servicoJwt;
    private final ServicoDetalhesUsuario servicoDetalhesUsuario;
    private final ObjectMapper objectMapper;

    public FiltroJwt(ServicoJwt servicoJwt, ServicoDetalhesUsuario servicoDetalhesUsuario, ObjectMapper objectMapper) {
        this.servicoJwt = servicoJwt;
        this.servicoDetalhesUsuario = servicoDetalhesUsuario;
        this.objectMapper = objectMapper;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {

        String cabecalho = request.getHeader(CABECALHO);
        if (cabecalho == null || !cabecalho.startsWith(PREFIXO)) {
            chain.doFilter(request, response);
            return;
        }

        String token = cabecalho.substring(PREFIXO.length()).trim();
        try {
            Long usuarioId = servicoJwt.validarCapturarUsuarioId(token);
            UsuarioSeguranca principal = servicoDetalhesUsuario.carregarPorId(usuarioId);
            FiltroJwtPapeis.autenticar(principal);
            chain.doFilter(request, response);
        } catch (JwtException e) {
            escrever401(response,
                    "Sessão inválida ou expirada. Faça login novamente.");
        } catch (RegraNegocioException e) {
            // O token estava válido, mas a conta não existe mais (ex.: removida por suporte).
            escrever401(response, e.getMessage());
        }
    }

    private void escrever401(HttpServletResponse response, String mensagem) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), ErroResposta.simples(401, mensagem));
    }
}
