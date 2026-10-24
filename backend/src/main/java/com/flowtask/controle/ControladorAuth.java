package com.flowtask.controle;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.flowtask.dominio.Usuario;
import com.flowtask.dto.AutenticacaoDtos;
import com.flowtask.excecao.CredenciaisInvalidasException;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.seguranca.UsuarioSeguranca;
import com.flowtask.servico.ServicoAutenticacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/auth")
@Tag(name = "Autenticação")
public class ControladorAuth {

    private final ServicoAutenticacao servicoAutenticacao;

    public ControladorAuth(ServicoAutenticacao servicoAutenticacao) {
        this.servicoAutenticacao = servicoAutenticacao;
    }

    @Operation(summary = "Cria uma conta com nome, e-mail e senha")
    @PostMapping("/register")
    public ResponseEntity<AutenticacaoDtos.RespostaUsuario> registrar(
            @Valid @RequestBody AutenticacaoDtos.CadastrarConta dados) {
        AutenticacaoDtos.RespostaUsuario usuario = servicoAutenticacao.criarConta(dados);
        return ResponseEntity.status(HttpStatus.CREATED).body(usuario);
    }

    @Operation(summary = "Autentica o usuário e devolve um token JWT")
    @PostMapping("/login")
    public AutenticacaoDtos.RespostaLogin entrar(@Valid @RequestBody AutenticacaoDtos.EntrarConta dados) {
        return servicoAutenticacao.entrar(dados);
    }

    @Operation(summary = "Dados da conta autenticada")
    @GetMapping("/me")
    public AutenticacaoDtos.RespostaUsuario perfil() {
        return servicoAutenticacao.perfil(usuarioAutenticado());
    }

    @Operation(summary = "Atualiza o nome do usuário autenticado")
    @PutMapping("/me")
    public AutenticacaoDtos.RespostaUsuario atualizarPerfil(@Valid @RequestBody AutenticacaoDtos.AtualizarPerfil dados) {
        return servicoAutenticacao.atualizarPerfil(usuarioAutenticado(), dados.nome());
    }

    /**
     * "/api/auth/**" é público (para register/login); os endpoints /me exigem
     * token, então um principal ausente é 401 — nunca 500.
     */
    private Usuario usuarioAutenticado() {
        UsuarioSeguranca atual = FiltroJwtPapeis.usuarioAtual();
        if (atual == null) {
            throw new CredenciaisInvalidasException("É necessário estar autenticado para acessar este recurso.");
        }
        return atual.getUsuario();
    }
}
