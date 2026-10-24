package com.flowtask.servico;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowtask.dominio.Usuario;
import com.flowtask.dto.AutenticacaoDtos;
import com.flowtask.excecao.ConflitoException;
import com.flowtask.excecao.CredenciaisInvalidasException;
import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.UsuarioRepositorio;
import com.flowtask.seguranca.ServicoJwt;

/**
 * Cadastro, login e perfil. Senhas sempre com hash BCrypt; o token JWT é
 * assinado com segredo de ambiente e tem expiração configurável.
 */
@Service
public class ServicoAutenticacao {

    private final UsuarioRepositorio repositorio;
    private final org.springframework.security.crypto.password.PasswordEncoder codificador;
    private final ServicoJwt servicoJwt;

    public ServicoAutenticacao(UsuarioRepositorio repositorio,
                               org.springframework.security.crypto.password.PasswordEncoder codificador,
                               ServicoJwt servicoJwt) {
        this.repositorio = repositorio;
        this.codificador = codificador;
        this.servicoJwt = servicoJwt;
    }

    @Transactional
    public AutenticacaoDtos.RespostaUsuario criarConta(AutenticacaoDtos.CadastrarConta dto) {
        String email = dto.email().trim().toLowerCase();
        if (repositorio.existsByEmailIgnoreCase(email)) {
            throw new ConflitoException("Já existe uma conta cadastrada com este e-mail.");
        }
        Usuario usuario = new Usuario(dto.nome().trim(), email, codificador.encode(dto.senha()));
        return resposta(repositorio.save(usuario));
    }

    @Transactional(readOnly = true)
    public AutenticacaoDtos.RespostaLogin entrar(AutenticacaoDtos.EntrarConta dto) {
        Usuario usuario = repositorio
                .findByEmailIgnoreCase(dto.email().trim())
                .filter(u -> codificador.matches(dto.senha(), u.getSenhaHash()))
                .orElseThrow(() -> new CredenciaisInvalidasException("E-mail ou senha inválidos."));
        String token = servicoJwt.gerarToken(usuario);
        return new AutenticacaoDtos.RespostaLogin(token, servicoJwt.getExpiracaoSegundos(), resposta(usuario));
    }

    @Transactional(readOnly = true)
    public AutenticacaoDtos.RespostaUsuario perfil(Usuario usuarioAtual) {
        return resposta(buscarAnexado(usuarioAtual));
    }

    @Transactional
    public AutenticacaoDtos.RespostaUsuario atualizarPerfil(Usuario usuarioAtual, String nome) {
        if (nome == null || nome.isBlank()) {
            throw new RegraNegocioException("Informe o nome.");
        }
        Usuario usuario = buscarAnexado(usuarioAtual);
        usuario.setNome(nome.trim());
        usuario.setAtualizadoEm(java.time.LocalDateTime.now());
        return resposta(usuario);
    }

    /** Carrega o usuário gerenciado pela sessão atual (a entidade do principal pode estar detached). */
    private Usuario buscarAnexado(Usuario referencia) {
        return repositorio.findById(referencia.getId())
                .orElseThrow(() -> new RegraNegocioException("Conta não encontrada."));
    }

    private AutenticacaoDtos.RespostaUsuario resposta(Usuario usuario) {
        return new AutenticacaoDtos.RespostaUsuario(usuario.getId(), usuario.getNome(), usuario.getEmail(),
                usuario.getCriadoEm());
    }
}
