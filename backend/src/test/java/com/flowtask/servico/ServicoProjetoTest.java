package com.flowtask.servico;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.Usuario;
import com.flowtask.excecao.AcessoNegadoException;
import com.flowtask.excecao.RecursoNaoEncontradoException;
import com.flowtask.repositorio.AtividadeRepositorio;
import com.flowtask.repositorio.ColunaRepositorio;
import com.flowtask.repositorio.ConviteRepositorio;
import com.flowtask.repositorio.MembroProjetoRepositorio;
import com.flowtask.repositorio.NotificacaoRepositorio;
import com.flowtask.repositorio.ProjetoRepositorio;
import com.flowtask.repositorio.TarefaRepositorio;
import com.flowtask.suporte.SuporteTeste;

/**
 * Exclusão de projeto: pode excluir somente o Proprietário (403 para papéis
 * inferiores) e a remoção passa por todas as tabelas sem CASCADE.
 */
class ServicoProjetoTest {

    private VerificadorAcesso verificador;
    private ProjetoRepositorio projetoRepositorio;
    private MembroProjetoRepositorio membroRepositorio;
    private TarefaRepositorio tarefaRepositorio;
    private NotificacaoRepositorio notificacaoRepositorio;
    private ConviteRepositorio conviteRepositorio;
    private AtividadeRepositorio atividadeRepositorio;
    private ServicoProjeto servico;

    private Usuario ana;
    private Usuario bruno;
    private Projeto projeto;

    @BeforeEach
    void setUp() {
        verificador = mock(VerificadorAcesso.class);
        projetoRepositorio = mock(ProjetoRepositorio.class);
        membroRepositorio = mock(MembroProjetoRepositorio.class);
        tarefaRepositorio = mock(TarefaRepositorio.class);
        notificacaoRepositorio = mock(NotificacaoRepositorio.class);
        conviteRepositorio = mock(ConviteRepositorio.class);
        atividadeRepositorio = mock(AtividadeRepositorio.class);
        servico = new ServicoProjeto(projetoRepositorio, membroRepositorio,
                mock(ColunaRepositorio.class), tarefaRepositorio, notificacaoRepositorio,
                conviteRepositorio, atividadeRepositorio, verificador, mock(ServicoAtividade.class));

        ana = SuporteTeste.usuario("Ana Souza", "ana@flowtask.dev", 1L);
        bruno = SuporteTeste.usuario("Bruno Lima", "bruno@flowtask.dev", 2L);
        projeto = SuporteTeste.projeto("Portal do cliente", ana, 10L);
    }

    @Test
    void ownerExcluiEPassaPorTodasAsTabelas() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);

        servico.excluir(10L, ana);

        verify(tarefaRepositorio).excluirPorProjeto(10L);
        verify(notificacaoRepositorio).excluirPorProjeto(10L);
        verify(conviteRepositorio).excluirPorProjeto(10L);
        verify(membroRepositorio).excluirPorProjeto(10L);
        verify(atividadeRepositorio).excluirPorProjeto(10L);
        verify(projetoRepositorio).delete(projeto);
    }

    @Test
    void managerRecebe403() {
        when(verificador.exigirPapel(10L, bruno, PapelProjeto.OWNER))
                .thenThrow(new AcessoNegadoException("Você não possui permissão para esta operação neste projeto."));

        org.junit.jupiter.api.Assertions.assertThrows(AcessoNegadoException.class,
                () -> servico.excluir(10L, bruno));

        verify(projetoRepositorio, never()).delete(org.mockito.ArgumentMatchers.any(Projeto.class));
        verify(tarefaRepositorio, never()).excluirPorProjeto(10L);
    }

    @Test
    void naoIntegranteRecebe404() {
        when(verificador.exigirPapel(10L, bruno, PapelProjeto.OWNER))
                .thenThrow(new RecursoNaoEncontradoException("Projeto não encontrado."));

        org.junit.jupiter.api.Assertions.assertThrows(RecursoNaoEncontradoException.class,
                () -> servico.excluir(10L, bruno));

        verify(projetoRepositorio, never()).delete(org.mockito.ArgumentMatchers.any(Projeto.class));
    }
}
