package com.flowtask.servico;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.ArgumentMatchers.same;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.flowtask.dominio.MembroProjeto;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.TipoAtividade;
import com.flowtask.dominio.TipoNotificacao;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.ProjetoDtos;
import com.flowtask.excecao.ConflitoException;
import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.MembroProjetoRepositorio;
import com.flowtask.repositorio.TarefaRepositorio;
import com.flowtask.suporte.SuporteTeste;

/**
 * Testes da gestão de integrantes: apenas OWNER altera/removes, transferência
 * de propriedade troca OWNER/MANAGER e remoção exige tarefas reatribuídas.
 */
class ServicoMembrosTest {

    private VerificadorAcesso verificador;
    private MembroProjetoRepositorio membroRepositorio;
    private TarefaRepositorio tarefaRepositorio;
    private ServicoAtividade servicoAtividade;
    private ServicoNotificacao servicoNotificacao;
    private ServicoMembros servico;

    private Usuario ana;
    private Usuario bruno;
    private Projeto projeto;
    private MembroProjeto membroAna;
    private MembroProjeto membroBruno;

    @BeforeEach
    void setUp() {
        verificador = mock(VerificadorAcesso.class);
        membroRepositorio = mock(MembroProjetoRepositorio.class);
        tarefaRepositorio = mock(TarefaRepositorio.class);
        servicoAtividade = mock(ServicoAtividade.class);
        servicoNotificacao = mock(ServicoNotificacao.class);
        servico = new ServicoMembros(verificador, membroRepositorio, tarefaRepositorio,
                servicoAtividade, servicoNotificacao);

        ana = SuporteTeste.usuario("Ana Souza", "ana@flowtask.dev", 1L);
        bruno = SuporteTeste.usuario("Bruno Lima", "bruno@flowtask.dev", 2L);
        projeto = SuporteTeste.projeto("Portal do cliente", ana, 10L);
        membroAna = SuporteTeste.membro(projeto, ana, PapelProjeto.OWNER, 100L);
        membroBruno = SuporteTeste.membro(projeto, bruno, PapelProjeto.MEMBER, 101L);
    }

    @Test
    void deveListarIntegrantesComFuncao() {
        when(verificador.exigirAcesso(10L, ana)).thenReturn(PapelProjeto.OWNER);
        when(membroRepositorio.listarPorProjeto(10L)).thenReturn(List.of(membroAna, membroBruno));

        List<ProjetoDtos.RespostaMembro> resposta = servico.listar(10L, ana);

        assertEquals(2, resposta.size());
        assertEquals(PapelProjeto.OWNER, resposta.get(0).funcao());
        assertEquals("Ana Souza", resposta.get(0).usuario().nome());
        assertEquals(PapelProjeto.MEMBER, resposta.get(1).funcao());
    }

    @Test
    void deveAlterarFuncaoDeMembroComum() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.membroVinculado(101L, 10L)).thenReturn(membroBruno);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);

        ProjetoDtos.RespostaMembro resposta = servico.alterarFuncao(10L, 101L, PapelProjeto.MANAGER, ana);

        assertEquals(PapelProjeto.MANAGER, resposta.funcao());
        assertEquals(PapelProjeto.MANAGER, membroBruno.getFuncao());
        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.FUNCAO_ALTERADA),
                eq(ServicoAtividade.ENT_MEMBRO), eq(101L), contains("de MEMBER para MANAGER"),
                anyMap(), anyMap());
    }

    @Test
    void deveRecusarRebaixarDiretamenteOProprietario() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.membroVinculado(100L, 10L)).thenReturn(membroAna);

        assertThrows(RegraNegocioException.class,
                () -> servico.alterarFuncao(10L, 100L, PapelProjeto.MANAGER, ana));
        assertEquals(PapelProjeto.OWNER, membroAna.getFuncao());
    }

    @Test
    void deveTransferirPropriedadeENomearAntigoComoManager() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.membroVinculado(101L, 10L)).thenReturn(membroBruno);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(membroRepositorio.findByProjetoIdAndUsuarioId(10L, 1L)).thenReturn(Optional.of(membroAna));

        ProjetoDtos.RespostaMembro resposta = servico.alterarFuncao(10L, 101L, PapelProjeto.OWNER, ana);

        assertEquals(PapelProjeto.OWNER, resposta.funcao());
        assertEquals(PapelProjeto.OWNER, membroBruno.getFuncao());
        assertEquals(PapelProjeto.MANAGER, membroAna.getFuncao());
        assertEquals(bruno, projeto.getDono());
        verify(servicoAtividade).registrar(same(projeto), same(ana), eq(TipoAtividade.PROPRIEDADE_TRANSFERIDA),
                eq(ServicoAtividade.ENT_PROJETO), eq(10L), contains("Bruno Lima"));
        verify(servicoNotificacao).criarSeDiferenteDoAtor(same(ana), same(bruno),
                eq(TipoNotificacao.PROPRIEDADE_TRANSFERIDA), anyString(), same(projeto), isNull());
    }

    @Test
    void deveRecusarTransferenciaDePropriedadeParaSiMesmo() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.membroVinculado(100L, 10L)).thenReturn(membroAna);

        assertThrows(RegraNegocioException.class,
                () -> servico.alterarFuncao(10L, 100L, PapelProjeto.OWNER, ana));
        assertEquals(ana, projeto.getDono());
    }

    @Test
    void deveBloquearRemocaoDoProprietario() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.membroVinculado(100L, 10L)).thenReturn(membroAna);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);

        assertThrows(RegraNegocioException.class, () -> servico.remover(10L, 100L, ana));
        verify(membroRepositorio, never()).delete(anyMembro());
    }

    private MembroProjeto anyMembro() {
        return org.mockito.ArgumentMatchers.any(MembroProjeto.class);
    }

    @Test
    void deveBloquearRemocaoDeMembroComTarefasAtribuidas() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.membroVinculado(101L, 10L)).thenReturn(membroBruno);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(tarefaRepositorio.existsByResponsavelIdAndProjetoIdAndArquivadaFalse(2L, 10L)).thenReturn(true);

        assertThrows(ConflitoException.class, () -> servico.remover(10L, 101L, ana));
        verify(membroRepositorio, never()).delete(anyMembro());
    }

    @Test
    void deveRemoverMembroSemTarefasENotificar() {
        when(verificador.exigirPapel(10L, ana, PapelProjeto.OWNER)).thenReturn(PapelProjeto.OWNER);
        when(verificador.membroVinculado(101L, 10L)).thenReturn(membroBruno);
        when(verificador.projetoPorId(10L)).thenReturn(projeto);
        when(tarefaRepositorio.existsByResponsavelIdAndProjetoIdAndArquivadaFalse(2L, 10L)).thenReturn(false);

        servico.remover(10L, 101L, ana);

        verify(membroRepositorio).delete(membroBruno);
        verify(servicoNotificacao).criarSeDiferenteDoAtor(same(ana), same(bruno),
                eq(TipoNotificacao.MEMBRO_REMOVIDO), anyString(), same(projeto), isNull());
    }

    @Test
    void deveExigirOwnerParaAlterarFuncao() {
        when(verificador.exigirPapel(10L, bruno, PapelProjeto.OWNER))
                .thenThrow(new com.flowtask.excecao.AcessoNegadoException("sem permissão"));

        assertThrows(com.flowtask.excecao.AcessoNegadoException.class,
                () -> servico.alterarFuncao(10L, 101L, PapelProjeto.MANAGER, bruno));
        assertEquals(PapelProjeto.MEMBER, membroBruno.getFuncao());
    }
}
