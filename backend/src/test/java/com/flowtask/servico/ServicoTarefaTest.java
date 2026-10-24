package com.flowtask.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
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
import org.mockito.ArgumentCaptor;

import com.flowtask.dominio.ColunaKanban;
import com.flowtask.dominio.EstadoProjeto;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.PrioridadeTarefa;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.Tarefa;
import com.flowtask.dominio.TipoAtividade;
import com.flowtask.dominio.TipoNotificacao;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.QuadroDtos;
import com.flowtask.dto.TarefaDtos;
import com.flowtask.excecao.AcessoNegadoException;
import com.flowtask.excecao.ConflitoException;
import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.ColunaRepositorio;
import com.flowtask.repositorio.ComentarioRepositorio;
import com.flowtask.repositorio.EtiquetaRepositorio;
import com.flowtask.repositorio.MembroProjetoRepositorio;
import com.flowtask.repositorio.ProjetoRepositorio;
import com.flowtask.repositorio.TarefaRepositorio;
import com.flowtask.repositorio.UsuarioRepositorio;
import com.flowtask.suporte.SuporteTeste;

/**
 * Testes das regras de tarefas: criação com validação de responsável,
 * movimentação (reordenação e conclusão), permissões por papel e exclusão
 * protegida por comentários.
 */
class ServicoTarefaTest {

    private TarefaRepositorio tarefaRepositorio;
    private ColunaRepositorio colunaRepositorio;
    private ComentarioRepositorio comentarioRepositorio;
    private EtiquetaRepositorio etiquetaRepositorio;
    private MembroProjetoRepositorio membroRepositorio;
    private UsuarioRepositorio usuarioRepositorio;
    private ProjetoRepositorio projetoRepositorio;
    private VerificadorAcesso verificador;
    private ServicoAtividade servicoAtividade;
    private ServicoNotificacao servicoNotificacao;
    private ServicoTarefa servico;

    private Usuario ana;
    private Usuario bruno;
    private Projeto projeto;
    private ColunaKanban colunaA;
    private ColunaKanban colunaB;
    private ColunaKanban colunaConcluida;
    private ColunaKanban colunaInativa;
    private Tarefa tarefa;

    @BeforeEach
    void setUp() {
        tarefaRepositorio = mock(TarefaRepositorio.class);
        colunaRepositorio = mock(ColunaRepositorio.class);
        comentarioRepositorio = mock(ComentarioRepositorio.class);
        etiquetaRepositorio = mock(EtiquetaRepositorio.class);
        membroRepositorio = mock(MembroProjetoRepositorio.class);
        usuarioRepositorio = mock(UsuarioRepositorio.class);
        projetoRepositorio = mock(ProjetoRepositorio.class);
        verificador = mock(VerificadorAcesso.class);
        servicoAtividade = mock(ServicoAtividade.class);
        servicoNotificacao = mock(ServicoNotificacao.class);
        servico = new ServicoTarefa(tarefaRepositorio, colunaRepositorio, comentarioRepositorio,
                etiquetaRepositorio, membroRepositorio, usuarioRepositorio, projetoRepositorio,
                verificador, servicoAtividade, servicoNotificacao);

        ana = SuporteTeste.usuario("Ana Souza", "ana@flowtask.dev", 1L);
        bruno = SuporteTeste.usuario("Bruno Lima", "bruno@flowtask.dev", 2L);
        projeto = SuporteTeste.projeto("Portal do cliente", ana, 10L);
        colunaA = SuporteTeste.coluna(projeto, "A FAZER", 0, false, 20L);
        colunaB = SuporteTeste.coluna(projeto, "EM ANDAMENTO", 1, false, 21L);
        colunaConcluida = SuporteTeste.coluna(projeto, "CONCLUÍDO", 2, true, 22L);
        colunaInativa = SuporteTeste.coluna(projeto, "ARQUIVADA", 3, false, 23L);
        colunaInativa.setAtiva(false);
        tarefa = SuporteTeste.tarefa(projeto, colunaA, "Configurar CI", ana, 30L);
    }

    // ------------------------------------------------------------------ criação

    @Test
    void deveCriarTarefaComResponsavelENotificar() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.MANAGER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(colunaRepositorio.findByIdAndProjetoId(20L, 10L)).thenReturn(Optional.of(colunaA));
        when(usuarioRepositorio.findById(2L)).thenReturn(Optional.of(bruno));
        when(membroRepositorio.existsByProjetoIdAndUsuarioId(10L, 2L)).thenReturn(true);
        when(tarefaRepositorio.countByColunaId(20L)).thenReturn(0L);
        when(tarefaRepositorio.save(any(Tarefa.class))).thenAnswer(inv -> inv.getArgument(0));

        TarefaDtos.RespostaTarefa resposta = servico.criar(10L, new TarefaDtos.CriarTarefa(
                " Corrigir login ", " Passos para reproduzir o erro ", 20L, null, 2L, null, null), ana);

        assertEquals("Corrigir login", resposta.titulo());
        assertEquals(PrioridadeTarefa.MEDIA, resposta.prioridade());
        assertEquals("Bruno Lima", resposta.responsavel().nome());

        ArgumentCaptor<Tarefa> captor = ArgumentCaptor.forClass(Tarefa.class);
        verify(tarefaRepositorio).save(captor.capture());
        assertEquals(0, captor.getValue().getOrdem());
        assertEquals(bruno, captor.getValue().getResponsavel());

        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.TAREFA_CRIADA),
                eq(ServicoAtividade.ENT_TAREFA), isNull(), anyString());
        verify(servicoNotificacao).criarSeDiferenteDoAtor(same(ana), same(bruno),
                eq(TipoNotificacao.TAREFA_ATRIBUIDA), anyString(), same(projeto), any(Tarefa.class));
    }

    @Test
    void deveRecusarResponsavelForaDoProjeto() {
        Usuario fantasma = SuporteTeste.usuario("Fantasma", "fantasma@flowtask.dev", 9L);
        when(verificador.exigirPapel(10L, ana, PapelProjeto.MANAGER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(colunaRepositorio.findByIdAndProjetoId(20L, 10L)).thenReturn(Optional.of(colunaA));
        when(usuarioRepositorio.findById(9L)).thenReturn(Optional.of(fantasma));
        when(membroRepositorio.existsByProjetoIdAndUsuarioId(10L, 9L)).thenReturn(false);

        assertThrows(RegraNegocioException.class, () -> servico.criar(10L,
                new TarefaDtos.CriarTarefa("Corrigir login", null, 20L, null, 9L, null, null), ana));
        verify(tarefaRepositorio, never()).save(any());
    }

    @Test
    void deveRecusarCriacaoEmColunaArquivada() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.MANAGER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(colunaRepositorio.findByIdAndProjetoId(23L, 10L)).thenReturn(Optional.of(colunaInativa));

        assertThrows(RegraNegocioException.class, () -> servico.criar(10L,
                new TarefaDtos.CriarTarefa("Corrigir login", null, 23L, null, null, null, null), ana));
    }

    @Test
    void deveExigirManagerOuOwnerParaCriarTarefa() {
        when(verificador.exigirPapel(10L, bruno, PapelProjeto.MANAGER))
                .thenThrow(new AcessoNegadoException("sem permissão"));

        assertThrows(AcessoNegadoException.class, () -> servico.criar(10L,
                new TarefaDtos.CriarTarefa("Tarefa do bruno", null, 20L, null, null, null, null), bruno));
        verify(tarefaRepositorio, never()).save(any());
    }

    // ------------------------------------------------------------ movimentação

    @Test
    void deveMoverEReordenarColunaDeDestino() {
        Tarefa x0 = SuporteTeste.tarefa(projeto, colunaB, "X0", ana, 40L);
        x0.setOrdem(0);
        Tarefa x1 = SuporteTeste.tarefa(projeto, colunaB, "X1", ana, 41L);
        x1.setOrdem(1);

        when(tarefaRepositorio.buscarCompleta(30L)).thenReturn(Optional.of(tarefa));
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(projetoRepositorio.buscarComBloqueio(10L)).thenReturn(Optional.of(projeto));
        when(colunaRepositorio.findByIdAndProjetoId(21L, 10L)).thenReturn(Optional.of(colunaB));
        when(tarefaRepositorio.findByColunaIdOrderByOrdemAsc(21L)).thenReturn(List.of(x0, x1));
        when(comentarioRepositorio.countByTarefaId(30L)).thenReturn(0L);

        TarefaDtos.RespostaTarefa resposta =
                servico.mover(30L, new QuadroDtos.MoverTarefa(21L, 0), ana);

        assertEquals("EM ANDAMENTO", resposta.colunaNome());
        assertEquals(0, resposta.ordem());
        assertFalse(resposta.concluida());
        assertEquals(0, tarefa.getOrdem());
        assertEquals(1, x0.getOrdem());
        assertEquals(2, x1.getOrdem());
        assertNull(tarefa.getDataConclusao());

        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.TAREFA_MOVIDA),
                eq(ServicoAtividade.ENT_TAREFA), eq(30L), contains("EM ANDAMENTO"));
        verify(servicoAtividade, never()).registrar(any(), any(), eq(TipoAtividade.TAREFA_CONCLUIDA),
                any(), any(), anyString());
    }

    @Test
    void deveGravarConclusaoAoMoverParaColunaDeConclusao() {
        tarefa.setResponsavel(bruno);

        when(tarefaRepositorio.buscarCompleta(30L)).thenReturn(Optional.of(tarefa));
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(projetoRepositorio.buscarComBloqueio(10L)).thenReturn(Optional.of(projeto));
        when(colunaRepositorio.findByIdAndProjetoId(22L, 10L)).thenReturn(Optional.of(colunaConcluida));
        when(tarefaRepositorio.findByColunaIdOrderByOrdemAsc(22L)).thenReturn(List.of());
        when(comentarioRepositorio.countByTarefaId(30L)).thenReturn(0L);

        TarefaDtos.RespostaTarefa resposta =
                servico.mover(30L, new QuadroDtos.MoverTarefa(22L, 0), ana);

        assertTrue(resposta.concluida());
        assertNotNull(resposta.dataConclusao());
        assertNotNull(tarefa.getDataConclusao());
        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.TAREFA_CONCLUIDA),
                eq(ServicoAtividade.ENT_TAREFA), eq(30L), contains("Concluiu"));
        verify(servicoNotificacao).criarSeDiferenteDoAtor(same(ana), same(bruno),
                eq(TipoNotificacao.TAREFA_CONCLUIDA), anyString(), same(projeto), same(tarefa));
    }

    @Test
    void deveReabrirTarefaAoMoverDeVolta() {
        tarefa.setColuna(colunaConcluida);
        tarefa.setResponsavel(bruno);
        tarefa.setDataConclusao(LocalDateTime.now().minusDays(1));

        when(tarefaRepositorio.buscarCompleta(30L)).thenReturn(Optional.of(tarefa));
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(projetoRepositorio.buscarComBloqueio(10L)).thenReturn(Optional.of(projeto));
        when(colunaRepositorio.findByIdAndProjetoId(20L, 10L)).thenReturn(Optional.of(colunaA));
        when(tarefaRepositorio.findByColunaIdOrderByOrdemAsc(20L)).thenReturn(List.of());
        when(comentarioRepositorio.countByTarefaId(30L)).thenReturn(0L);

        TarefaDtos.RespostaTarefa resposta =
                servico.mover(30L, new QuadroDtos.MoverTarefa(20L, 0), ana);

        assertFalse(resposta.concluida());
        assertNull(tarefa.getDataConclusao());
        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.TAREFA_REABERTA),
                eq(ServicoAtividade.ENT_TAREFA), eq(30L), contains("Reabriu"));
        verify(servicoNotificacao).criarSeDiferenteDoAtor(same(ana), same(bruno),
                eq(TipoNotificacao.TAREFA_REABERTA), anyString(), same(projeto), same(tarefa));
    }

    @Test
    void deveBloquearMovimentoEmProjetoArquivado() {
        projeto.setEstado(EstadoProjeto.ARQUIVADO);
        when(tarefaRepositorio.buscarCompleta(30L)).thenReturn(Optional.of(tarefa));
        when(verificador.projetoPorId(10L)).thenReturn(projeto);

        assertThrows(RegraNegocioException.class,
                () -> servico.mover(30L, new QuadroDtos.MoverTarefa(21L, 0), ana));
        verify(projetoRepositorio, never()).buscarComBloqueio(any());
    }

    @Test
    void deveBloquearMovimentoDeTarefaArquivada() {
        tarefa.setArquivada(true);
        when(tarefaRepositorio.buscarCompleta(30L)).thenReturn(Optional.of(tarefa));
        when(verificador.projetoPorId(10L)).thenReturn(projeto);

        assertThrows(RegraNegocioException.class,
                () -> servico.mover(30L, new QuadroDtos.MoverTarefa(21L, 0), ana));
    }

    @Test
    void deveBloquearMovimentoParaColunaInativa() {
        when(tarefaRepositorio.buscarCompleta(30L)).thenReturn(Optional.of(tarefa));
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(projetoRepositorio.buscarComBloqueio(10L)).thenReturn(Optional.of(projeto));
        when(colunaRepositorio.findByIdAndProjetoId(23L, 10L)).thenReturn(Optional.of(colunaInativa));

        assertThrows(RegraNegocioException.class,
                () -> servico.mover(30L, new QuadroDtos.MoverTarefa(23L, 0), ana));
    }

    // ------------------------------------------------------------------ edição

    @Test
    void devePermitirEditarTextosApenasDoResponsavel() {
        tarefa.setResponsavel(bruno);
        when(verificador.exigirTarefaAcessivel(30L, bruno)).thenReturn(tarefa);
        when(verificador.papelNoProjeto(10L, 2L)).thenReturn(PapelProjeto.MEMBER);
        when(comentarioRepositorio.countByTarefaId(30L)).thenReturn(0L);

        TarefaDtos.RespostaTarefa resposta = servico.editarTextos(30L,
                new TarefaDtos.EditarTarefaTexto(" Pipeline corrigido ", " Ajuste final "), bruno);

        assertEquals("Pipeline corrigido", resposta.titulo());
        assertEquals("Ajuste final", tarefa.getDescricao());
        verify(servicoAtividade).registrar(same(projeto), same(bruno),
                eq(TipoAtividade.TAREFA_ATUALIZADA), eq(ServicoAtividade.ENT_TAREFA), eq(30L),
                anyString(), any(), any());
    }

    @Test
    void deveNegarEdicaoDeMembroQueNaoEhResponsavel() {
        // A tarefa pertence a Ana; Bruno é apenas MEMBER sem vínculo com ela.
        when(verificador.exigirTarefaAcessivel(30L, bruno)).thenReturn(tarefa);
        when(verificador.papelNoProjeto(10L, 2L)).thenReturn(PapelProjeto.MEMBER);

        assertThrows(AcessoNegadoException.class, () -> servico.editarTextos(30L,
                new TarefaDtos.EditarTarefaTexto(" Tentativa indevida ", null), bruno));
        assertEquals("Configurar CI", tarefa.getTitulo());
    }

    @Test
    void deveMananagerEditarQualquerTarefa() {
        Usuario maria = SuporteTeste.usuario("Maria Gomes", "maria@flowtask.dev", 3L);
        when(verificador.exigirTarefaAcessivel(30L, maria)).thenReturn(tarefa);
        when(verificador.papelNoProjeto(10L, 3L)).thenReturn(PapelProjeto.MANAGER);
        when(comentarioRepositorio.countByTarefaId(30L)).thenReturn(0L);

        TarefaDtos.RespostaTarefa resposta = servico.editarTextos(30L,
                new TarefaDtos.EditarTarefaTexto(" Editado pelo gestor ", null), maria);

        assertEquals("Editado pelo gestor", resposta.titulo());
    }

    // ------------------------------------------------------------------ exclusão

    @Test
    void deveExigirOwnerParaExcluirTarefa() {
        when(verificador.exigirTarefaAcessivel(30L, bruno)).thenReturn(tarefa);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(verificador.exigirPapel(10L, bruno, PapelProjeto.OWNER))
                .thenThrow(new AcessoNegadoException("sem permissão"));

        assertThrows(AcessoNegadoException.class, () -> servico.excluir(30L, bruno));
        verify(tarefaRepositorio, never()).delete(any());
    }

    @Test
    void deveBloquearExclusaoDeTarefaComComentarios() {
        when(verificador.exigirTarefaAcessivel(30L, ana)).thenReturn(tarefa);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(comentarioRepositorio.countByTarefaId(30L)).thenReturn(2L);

        assertThrows(ConflitoException.class, () -> servico.excluir(30L, ana));
        verify(tarefaRepositorio, never()).delete(any());
    }

    @Test
    void deveExcluirTarefaSemComentarios() {
        when(verificador.exigirTarefaAcessivel(30L, ana)).thenReturn(tarefa);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(comentarioRepositorio.countByTarefaId(30L)).thenReturn(0L);

        servico.excluir(30L, ana);

        verify(tarefaRepositorio).delete(tarefa);
        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.TAREFA_REMOVIDA),
                eq(ServicoAtividade.ENT_TAREFA), eq(30L), contains("Excluiu a tarefa 'Configurar CI'."));
    }
}
