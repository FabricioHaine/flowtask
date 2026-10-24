package com.flowtask.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.flowtask.dominio.ColunaKanban;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.TipoAtividade;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.QuadroDtos;
import com.flowtask.excecao.AcessoNegadoException;
import com.flowtask.excecao.ConflitoException;
import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.ColunaRepositorio;
import com.flowtask.repositorio.ComentarioRepositorio;
import com.flowtask.repositorio.TarefaRepositorio;
import com.flowtask.suporte.SuporteTeste;

/**
 * Testes das regras do quadro: colunas só pelo OWNER, nome único, um único
 * sinalizador de conclusão por projeto e remoção bloqueada com tarefas.
 */
class ServicoQuadroTest {

    private VerificadorAcesso verificador;
    private ColunaRepositorio colunaRepositorio;
    private TarefaRepositorio tarefaRepositorio;
    private ComentarioRepositorio comentarioRepositorio;
    private ServicoAtividade servicoAtividade;
    private ServicoQuadro servico;

    private Usuario ana;
    private Projeto projeto;
    private ColunaKanban colunaA;
    private ColunaKanban colunaB;
    private ColunaKanban colunaC;

    @BeforeEach
    void setUp() {
        verificador = mock(VerificadorAcesso.class);
        colunaRepositorio = mock(ColunaRepositorio.class);
        tarefaRepositorio = mock(TarefaRepositorio.class);
        comentarioRepositorio = mock(ComentarioRepositorio.class);
        servicoAtividade = mock(ServicoAtividade.class);
        servico = new ServicoQuadro(verificador, colunaRepositorio, tarefaRepositorio,
                comentarioRepositorio, servicoAtividade);

        ana = SuporteTeste.usuario("Ana Souza", "ana@flowtask.dev", 1L);
        projeto = SuporteTeste.projeto("Portal do cliente", ana, 10L);
        colunaA = SuporteTeste.coluna(projeto, "A FAZER", 0, false, 20L);
        colunaB = SuporteTeste.coluna(projeto, "EM ANDAMENTO", 1, false, 21L);
        colunaC = SuporteTeste.coluna(projeto, "CONCLUÍDO", 2, true, 22L);
        projeto.addColuna(colunaA);
        projeto.addColuna(colunaB);
        projeto.addColuna(colunaC);
    }

    @Test
    void deveCriarColunaComOrdemNoFimDoQuadro() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(colunaRepositorio.existsByProjetoIdAndNomeIgnoreCase(10L, "BACKLOG")).thenReturn(false);
        when(colunaRepositorio.countByProjetoId(10L)).thenReturn(3L);
        when(colunaRepositorio.save(any(ColunaKanban.class))).thenAnswer(inv -> inv.getArgument(0));

        QuadroDtos.RespostaColunaQuadro resposta = servico.criarColuna(10L, "  BACKLOG  ", ana);

        assertEquals("BACKLOG", resposta.nome());
        assertEquals(3, resposta.ordem());
        assertFalse(resposta.colunaConclusao());
        assertEquals(4, projeto.getColunas().size());
        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.COLUNA_CRIADA),
                eq(ServicoAtividade.ENT_COLUNA), isNull(), eq("Criou a coluna 'BACKLOG'."));
    }

    @Test
    void deveRecusarColunaComNomeDuplicado() {
        when(colunaRepositorio.existsByProjetoIdAndNomeIgnoreCase(10L, "EM ANDAMENTO")).thenReturn(true);

        assertThrows(ConflitoException.class, () -> servico.criarColuna(10L, "EM ANDAMENTO", ana));
        verify(colunaRepositorio, never()).save(any());
    }

    @Test
    void deveExigirPapelOwnerParaCriarColuna() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER))
                .thenThrow(new AcessoNegadoException("sem permissão"));

        assertThrows(AcessoNegadoException.class, () -> servico.criarColuna(10L, "BACKLOG", ana));
        verify(colunaRepositorio, never()).save(any());
    }

    @Test
    void deveManterUmUnicoSinalizadorDeConclusaoAoAtivarColunaB() {
        QuadroDtos.AtualizarColuna dto = new QuadroDtos.AtualizarColuna("EM ANDAMENTO", true);
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(colunaRepositorio.findByIdAndProjetoId(21L, 10L)).thenReturn(Optional.of(colunaB));
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(colunaRepositorio.findByProjetoIdAndColunaConclusaoTrue(10L)).thenReturn(Optional.of(colunaC));

        QuadroDtos.RespostaColunaQuadro resposta = servico.atualizarColuna(10L, 21L, dto, ana);

        assertTrue(colunaB.isColunaConclusao());
        assertFalse(colunaC.isColunaConclusao());
        assertTrue(resposta.colunaConclusao());
        verify(tarefaRepositorio).marcarConclusaoDasTarefas(eq(21L), any(LocalDateTime.class));
        verify(tarefaRepositorio, never()).limparConclusaoDasTarefas(any(), any());
        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.COLUNA_RENOMEADA),
                eq(ServicoAtividade.ENT_COLUNA), eq(21L), anyString(),
                any(), any());
    }

    @Test
    void deveLimparConclusaoAoDesativarSinalizador() {
        QuadroDtos.AtualizarColuna dto = new QuadroDtos.AtualizarColuna("CONCLUÍDO", false);
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(colunaRepositorio.findByIdAndProjetoId(22L, 10L)).thenReturn(Optional.of(colunaC));
        when(verificador.projetoPorId(10L)).thenReturn(projeto);

        servico.atualizarColuna(10L, 22L, dto, ana);

        assertFalse(colunaC.isColunaConclusao());
        verify(tarefaRepositorio).limparConclusaoDasTarefas(eq(22L), any(LocalDateTime.class));
        verify(tarefaRepositorio, never()).marcarConclusaoDasTarefas(any(), any());
    }

    @Test
    void deveReordenarColunasSomenteComListaCompleta() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(colunaRepositorio.findByProjetoIdOrderByOrdem(10L))
                .thenReturn(List.of(colunaA, colunaB, colunaC));

        servico.reordenar(10L, List.of(21L, 22L, 20L), ana);

        assertEquals(0, colunaB.getOrdem());
        assertEquals(1, colunaC.getOrdem());
        assertEquals(2, colunaA.getOrdem());
        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.COLUNA_REORDENADA),
                eq(ServicoAtividade.ENT_COLUNA), eq(10L), contains("Reordenou"));

        // Lista incompleta: todas as colunas devem estar presentes, exatamente uma vez.
        assertThrows(RegraNegocioException.class, () -> servico.reordenar(10L, List.of(20L, 21L), ana));
    }

    @Test
    void deveBloquearRemocaoDeColunaComTarefas() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(colunaRepositorio.findByIdAndProjetoId(21L, 10L)).thenReturn(Optional.of(colunaB));
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(tarefaRepositorio.countByColunaId(21L)).thenReturn(2L);

        assertThrows(ConflitoException.class, () -> servico.remover(10L, 21L, ana));
        verify(colunaRepositorio, never()).delete(any());
        assertEquals(3, projeto.getColunas().size());
    }

    @Test
    void deveRemoverColunaVazia() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(colunaRepositorio.findByIdAndProjetoId(22L, 10L)).thenReturn(Optional.of(colunaC));
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(tarefaRepositorio.countByColunaId(22L)).thenReturn(0L);

        servico.remover(10L, 22L, ana);

        verify(colunaRepositorio).delete(colunaC);
        assertFalse(projeto.getColunas().contains(colunaC));
        assertEquals(2, projeto.getColunas().size());
        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.COLUNA_REMOVIDA),
                eq(ServicoAtividade.ENT_COLUNA), eq(22L), contains("Removeu a coluna 'CONCLUÍDO'."));
    }

    @Test
    void deveExigirOwnerParaArquivarColuna() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER))
                .thenThrow(new AcessoNegadoException("sem permissão"));

        assertThrows(AcessoNegadoException.class,
                () -> servico.arquivar(10L, 21L, false, ana));
        assertTrue(colunaB.isAtiva());
    }
}
