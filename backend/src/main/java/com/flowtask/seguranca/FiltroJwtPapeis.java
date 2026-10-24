package com.flowtask.seguranca;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Utilitário para colocar/recuperar o usuário autenticado SecurityContext.
 */
public final class FiltroJwtPapeis {

    private FiltroJwtPapeis() {
    }

    /** Define a autenticação no contexto da requisição atual. */
    public static void autenticar(UsuarioSeguranca principal) {
        SecurityContextHolder.getContext().setAuthentication(new TokenAutenticacao(principal));
    }

    /**
     * Usuário autenticado da requisição atual, ou null (endpoints públicos).
     */
    public static UsuarioSeguranca usuarioAtual() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a != null && a.getPrincipal() instanceof UsuarioSeguranca us) {
            return us;
        }
        return null;
    }

    /**
     * Usuário de domínio da requisição atual; lança erro claro se ausente
     * (chamado apenas por endpoints protegidos).
     */
    public static com.flowtask.dominio.Usuario usuarioCorrente() {
        UsuarioSeguranca usuario = usuarioAtual();
        if (usuario == null) {
            throw new IllegalStateException("Nenhum usuário autenticado no contexto da requisição.");
        }
        return usuario.getUsuario();
    }

    /** Autenticação simples sem credenciais sensíveis no objeto. */
    static final class TokenAutenticacao extends AbstractAuthenticationToken {

        private final UsuarioSeguranca principal;

        TokenAutenticacao(UsuarioSeguranca principal) {
            super(principal.getAuthorities());
            this.principal = principal;
            setAuthenticated(true);
        }

        @Override
        public Object getPrincipal() {
            return principal;
        }

        @Override
        public Object getCredentials() {
            return "N/A";
        }

        @Override
        public String getName() {
            return principal.getEmail();
        }
    }
}
