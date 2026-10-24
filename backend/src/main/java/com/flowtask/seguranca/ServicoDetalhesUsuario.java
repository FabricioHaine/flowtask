package com.flowtask.seguranca;

import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.UsuarioRepositorio;

/**
 * Carrega o usuário por e-mail para a autenticação.
 * Lança RegraNegocioException com mensagem genérica para não revelar
 * se o e-mail existe ou não.
 */
@Service
public class ServicoDetalhesUsuario implements UserDetailsService {

    private final UsuarioRepositorio repositorio;

    public ServicoDetalhesUsuario(UsuarioRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        return repositorio.findByEmailIgnoreCase(email)
                .map(UsuarioSeguranca::new)
                .orElseThrow(() -> new UsernameNotFoundException("usuario nao encontrado"));
    }

    /**
     * Carrega a conta do usuário autenticado (id informado pelo filtro JWT).
     */
    public UsuarioSeguranca carregarPorId(Long id) {
        return repositorio.findById(id)
                .map(UsuarioSeguranca::new)
                .orElseThrow(() -> new RegraNegocioException("Conta não encontrada. Faça login novamente."));
    }
}
