package com.flowtask.seguranca;

import java.time.Instant;
import java.util.Base64;
import java.util.Date;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Component;

import com.flowtask.config.PropriedadesApp;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;

/**
 * Assinatura e validação de tokens JWT (HMAC-SHA256).
 * O segredo vem de variável de ambiente; o valor padrão do arquivo serve
 * apenas para desenvolvimento local.
 */
@Component
public class ServicoJwt {

    private static final String CLAIM_USUARIO_ID = "uid";

    private final SecretKey chave;
    private final long expiracaoSegundos;

    public ServicoJwt(PropriedadesApp propriedades) {
        String segredoBase64 = propriedades.getSeguranca().getJwt().getSegredo();
        byte[] bytesSegredo;
        try {
            bytesSegredo = Base64.getDecoder().decode(segredoBase64);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("JWT_SECRET precisa estar em base64 válido.", e);
        }
        if (bytesSegredo.length < 32) {
            throw new IllegalStateException(
                    "O segredo JWT precisa ter ao menos 32 bytes aleatórios (configurado em JWT_SECRET).");
        }
        this.chave = Keys.hmacShaKeyFor(bytesSegredo);
        this.expiracaoSegundos = propriedades.getSeguranca().getJwt().getExpiracaoSegundos();
    }

    public long getExpiracaoSegundos() {
        return expiracaoSegundos;
    }

    public String gerarToken(com.flowtask.dominio.Usuario usuario) {
        Instant agora = Instant.now();
        Instant expiracao = agora.plusSeconds(expiracaoSegundos);
        return Jwts.builder()
                .subject(usuario.getEmail())
                .claim(CLAIM_USUARIO_ID, usuario.getId())
                .claim("nome", usuario.getNome())
                .issuedAt(Date.from(agora))
                .expiration(Date.from(expiracao))
                .signWith(chave)
                .compact();
    }

    /** Valida assinatura e expiração; devolve o id do usuário informado no token. */
    public Long validarCapturarUsuarioId(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.get(CLAIM_USUARIO_ID, Long.class);
    }

    /** Valida assinatura e expiração; devolve o e-mail (subject) informado no token. */
    public String validarCapturarEmail(String token) {
        Claims claims = Jwts.parser()
                .verifyWith(chave)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        return claims.getSubject();
    }
}
