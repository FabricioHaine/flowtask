package com.flowtask.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.flowtask.dominio.Usuario;
import com.flowtask.dto.AutenticacaoDtos;
import com.flowtask.excecao.ConflitoException;
import com.flowtask.excecao.CredenciaisInvalidasException;
import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.UsuarioRepositorio;
import com.flowtask.seguranca.ServicoJwt;
import com.flowtask.suporte.SuporteTeste;

/**
 * Testes unitários de cadastro, login e perfil: senhas nunca em texto puro,
 * e-mail normalizado, duplicidade bloqueada e erro claro com senha errada.
 */
class ServicoAutenticacaoTest {

    private UsuarioRepositorio repositorio;
    private PasswordEncoder codificador;
    private ServicoJwt servicoJwt;
    private ServicoAutenticacao servico;

    @BeforeEach
    void setUp() {
        repositorio = org.mockito.Mockito.mock(UsuarioRepositorio.class);
        codificador = org.mockito.Mockito.mock(PasswordEncoder.class);
        servicoJwt = org.mockito.Mockito.mock(ServicoJwt.class);
        servico = new ServicoAutenticacao(repositorio, codificador, servicoJwt);
    }

    @Test
    void deveCriarContaComEmailNormalizadoESenhaCodificada() {
        when(repositorio.existsByEmailIgnoreCase("ana.souza@flowtask.dev")).thenReturn(false);
        when(codificador.encode("senha1234")).thenReturn("bcrypt-hash");
        when(repositorio.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));

        AutenticacaoDtos.RespostaUsuario resposta = servico.criarConta(new AutenticacaoDtos.CadastrarConta(
                "  Ana Souza ", " Ana.Souza@FlowTask.DEV ", "senha1234"));

        assertEquals("Ana Souza", resposta.nome());
        assertEquals("ana.souza@flowtask.dev", resposta.email());

        ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
        verify(repositorio).save(captor.capture());
        assertEquals("Ana Souza", captor.getValue().getNome());
        assertEquals("ana.souza@flowtask.dev", captor.getValue().getEmail());
        assertEquals("bcrypt-hash", captor.getValue().getSenhaHash());
    }

    @Test
    void deveRecusarCadastroComEmailDuplicado() {
        when(repositorio.existsByEmailIgnoreCase("ana@flowtask.dev")).thenReturn(true);

        assertThrows(ConflitoException.class, () -> servico.criarConta(
                new AutenticacaoDtos.CadastrarConta("Ana", "ana@flowtask.dev", "senha1234")));

        verify(repositorio, never()).save(any());
    }

    @Test
    void deveLogarComCredenciaisValidas() {
        Usuario usuario = SuporteTeste.usuario("Ana", "ana@flowtask.dev", 1L);
        usuario.setSenhaHash("bcrypt-hash");
        when(repositorio.findByEmailIgnoreCase("ana@flowtask.dev")).thenReturn(Optional.of(usuario));
        when(codificador.matches("senha1234", "bcrypt-hash")).thenReturn(true);
        when(servicoJwt.gerarToken(usuario)).thenReturn("jwt-token");
        when(servicoJwt.getExpiracaoSegundos()).thenReturn(600L);

        AutenticacaoDtos.RespostaLogin login =
                servico.entrar(new AutenticacaoDtos.EntrarConta("ana@flowtask.dev", "senha1234"));

        assertEquals("jwt-token", login.token());
        assertEquals(600L, login.expiraEmSegundos());
        assertEquals(1L, login.usuario().id());
        assertEquals("ana@flowtask.dev", login.usuario().email());
    }

    @Test
    void deveRecusarLoginComSenhaErrada() {
        Usuario usuario = SuporteTeste.usuario("Ana", "ana@flowtask.dev", 1L);
        usuario.setSenhaHash("bcrypt-hash");
        when(repositorio.findByEmailIgnoreCase("ana@flowtask.dev")).thenReturn(Optional.of(usuario));
        when(codificador.matches("errada999", "bcrypt-hash")).thenReturn(false);

        assertThrows(CredenciaisInvalidasException.class,
                () -> servico.entrar(new AutenticacaoDtos.EntrarConta("ana@flowtask.dev", "errada999")));
        verify(servicoJwt, never()).gerarToken(any());
    }

    @Test
    void deveRecusarLoginComEmailDesconhecido() {
        when(repositorio.findByEmailIgnoreCase("fantasma@flowtask.dev")).thenReturn(Optional.empty());

        assertThrows(CredenciaisInvalidasException.class,
                () -> servico.entrar(new AutenticacaoDtos.EntrarConta("fantasma@flowtask.dev", "senha1234")));
    }

    @Test
    void deveAtualizarNomeDoPerfil() {
        Usuario usuario = SuporteTeste.usuario("Nome Antigo", "ana@flowtask.dev", 7L);
        when(repositorio.findById(7L)).thenReturn(Optional.of(usuario));

        AutenticacaoDtos.RespostaUsuario resposta = servico.atualizarPerfil(usuario, "  Nome Novo ");

        assertEquals("Nome Novo", resposta.nome());
        assertEquals("Nome Novo", usuario.getNome());
    }

    @Test
    void deveRecusarNomeVazioAoAtualizarPerfil() {
        Usuario usuario = SuporteTeste.usuario("Nome Antigo", "ana@flowtask.dev", 7L);

        assertThrows(RegraNegocioException.class, () -> servico.atualizarPerfil(usuario, "   "));
        verify(repositorio, never()).save(any());
    }
}
