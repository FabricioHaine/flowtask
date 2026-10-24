package com.flowtask.seguranca;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import com.flowtask.dominio.Usuario;

/**
 * Estrutura de segurança que representa o usuário autenticado. Cada requisição
 * revindica este principal no SecurityContext.
 */
public class UsuarioSeguranca implements UserDetails {

    private final transient Usuario usuario;

    public UsuarioSeguranca(Usuario usuario) {
        this.usuario = usuario;
    }

    public Long getId() {
        return usuario.getId();
    }

    public String getEmail() {
        return usuario.getEmail();
    }

    public Usuario getUsuario() {
        return usuario;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_USUARIO"));
    }

    @Override
    public String getPassword() {
        return usuario.getSenhaHash();
    }

    @Override
    public String getUsername() {
        return usuario.getEmail();
    }
}
