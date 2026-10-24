package com.flowtask.servico;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.flowtask.dominio.Atividade;
import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.dto.PaginaResposta;
import com.flowtask.repositorio.AtividadeRepositorio;

/**
 * Trilha de auditoria: registra eventos relevantes com o ator autenticado e
 * expõe o histórico (somente leitura) aos membros dos projetos.
 *
 * A gravação acontece automaticamente nas operações de negócio; o cliente
 * nunca informa livremente o autor. Usuários comuns não alteram nem apagam o
 * histórico — não há endpoints de escrita ou remoção do histórico.
 */
@Service
public class ServicoAtividade {

    public static final String ENT_PROJETO = "PROJETO";
    public static final String ENT_TAREFA = "TAREFA";
    public static final String ENT_COLUNA = "COLUNA";
    public static final String ENT_MEMBRO = "MEMBRO";
    public static final String ENT_CONVITE = "CONVITE";
    public static final String ENT_COMENTARIO = "COMENTARIO";

    private final AtividadeRepositorio repositorio;
    private final ObjectMapper objectMapper;
    private final VerificadorAcesso verificador;

    public ServicoAtividade(AtividadeRepositorio repositorio, ObjectMapper objectMapper,
                            VerificadorAcesso verificador) {
        this.repositorio = repositorio;
        this.objectMapper = objectMapper;
        this.verificador = verificador;
    }

    // ---------------------------------------------------------------- gravação

    public void registrar(com.flowtask.dominio.Projeto projeto, com.flowtask.dominio.Usuario ator,
                          com.flowtask.dominio.TipoAtividade tipo, String entidade,
                          Long entidadeId, String resumo, Map<String, Object> antes, Map<String, Object> depois) {
        Atividade atividade = new Atividade(
                projeto, ator, tipo, entidade, entidadeId, resumo,
                serializa(antes), serializa(depois));
        repositorio.save(atividade);
    }

    public void registrar(com.flowtask.dominio.Projeto projeto, com.flowtask.dominio.Usuario ator,
                          com.flowtask.dominio.TipoAtividade tipo, String entidade,
                          Long entidadeId, String resumo) {
        registrar(projeto, ator, tipo, entidade, entidadeId, resumo, null, null);
    }

    // ---------------------------------------------------------------- consultas

    /** Histórico do projeto, paginado; exige vínculo (sem vínculo → 404). */
    @Transactional(readOnly = true)
    public PaginaResposta<ColaboracaoDtos.RespostaAtividade> listarProjeto(Long projetoId,
                                                                           com.flowtask.dominio.Usuario ator,
                                                                           int pagina, int tamanho) {
        verificador.exigirAcesso(projetoId, ator);
        List<Atividade> atividades = repositorio.listarPorProjeto(projetoId, PageRequest.of(pagina, tamanho));
        List<ColaboracaoDtos.RespostaAtividade> itens = new ArrayList<>(atividades.size());
        for (Atividade atividade : atividades) {
            itens.add(resposta(atividade));
        }
        long total = repositorio.countByProjetoId(projetoId);
        return PaginaResposta.de(itens, pagina, tamanho, total);
    }

    /** Histórico da tarefa, paginado. */
    @Transactional(readOnly = true)
    public PaginaResposta<ColaboracaoDtos.RespostaAtividade> listarTarefa(Long tarefaId,
                                                                          com.flowtask.dominio.Usuario ator,
                                                                          int pagina, int tamanho) {
        com.flowtask.dominio.Tarefa tarefa = verificador.exigirTarefaAcessivel(tarefaId, ator);
        List<Atividade> atividades = repositorio.listarPorEntidade(
                tarefa.getProjeto().getId(), ENT_TAREFA, tarefa.getId(), PageRequest.of(pagina, tamanho));
        List<ColaboracaoDtos.RespostaAtividade> itens = new ArrayList<>(atividades.size());
        for (Atividade atividade : atividades) {
            itens.add(resposta(atividade));
        }
        long total = repositorio.countByProjetoIdAndEntidadeAndEntidadeId(
                tarefa.getProjeto().getId(), ENT_TAREFA, tarefa.getId());
        return PaginaResposta.de(itens, pagina, tamanho, total);
    }

    // ---------------------------------------------------------------- helpers

    private String serializa(Map<String, Object> dados) {
        if (dados == null || dados.isEmpty()) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(dados);
        } catch (JsonProcessingException e) {
            return dados.toString();
        }
    }

    private ColaboracaoDtos.RespostaAtividade resposta(Atividade atividade) {
        return new ColaboracaoDtos.RespostaAtividade(
                atividade.getId(),
                atividade.getTipo(),
                atividade.getEntidade(),
                atividade.getEntidadeId(),
                atividade.getResumo(),
                atividade.getDadosAnteriores(),
                atividade.getDadosPosteriores(),
                Mapeadores.resumo(atividade.getUsuario()),
                atividade.getCriadoEm());
    }
}
