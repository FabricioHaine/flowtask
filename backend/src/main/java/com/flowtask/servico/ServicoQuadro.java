package com.flowtask.servico;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowtask.dominio.ColunaKanban;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.Tarefa;
import com.flowtask.dominio.TipoAtividade;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.dto.QuadroDtos;
import com.flowtask.dto.UsuarioResumo;
import com.flowtask.excecao.ConflitoException;
import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.ColunaRepositorio;
import com.flowtask.repositorio.ComentarioRepositorio;
import com.flowtask.repositorio.TarefaRepositorio;

/**
 * Gestão do quadro Kanban: colunas configuráveis (OWNER), leitura do quadro
 * com tarefas ordenadas e proteção contra remoção de colunas com tarefas.
 */
@Service
public class ServicoQuadro {

    private final VerificadorAcesso verificador;
    private final ColunaRepositorio colunaRepositorio;
    private final TarefaRepositorio tarefaRepositorio;
    private final ComentarioRepositorio comentarioRepositorio;
    private final ServicoAtividade servicoAtividade;

    public ServicoQuadro(VerificadorAcesso verificador,
                         ColunaRepositorio colunaRepositorio,
                         TarefaRepositorio tarefaRepositorio,
                         ComentarioRepositorio comentarioRepositorio,
                         ServicoAtividade servicoAtividade) {
        this.verificador = verificador;
        this.colunaRepositorio = colunaRepositorio;
        this.tarefaRepositorio = tarefaRepositorio;
        this.comentarioRepositorio = comentarioRepositorio;
        this.servicoAtividade = servicoAtividade;
    }

    // ---------------------------------------------------------------- quadro

    @Transactional(readOnly = true)
    public QuadroDtos.RespostaQuadro obterQuadro(Long projetoId, Usuario ator) {
        verificador.exigirAcesso(projetoId, ator);

        List<ColunaKanban> colunas = colunaRepositorio.listarAtivasPorProjeto(projetoId);
        List<Tarefa> tarefas = tarefaRepositorio.listarAtivasPorProjeto(projetoId);
        Map<Long, Long> comentariosPorTarefa = contarComentarios(projetoId);

        List<QuadroDtos.RespostaColunaQuadro> colunasDto = new ArrayList<>();
        for (ColunaKanban coluna : colunas) {
            List<QuadroDtos.RespostaTarefaQuadro> cartoes = new ArrayList<>();
            for (Tarefa tarefa : tarefas) {
                if (!tarefa.getColuna().getId().equals(coluna.getId())) {
                    continue;
                }
                cartoes.add(new QuadroDtos.RespostaTarefaQuadro(
                        tarefa.getId(),
                        tarefa.getTitulo(),
                        tarefa.getPrioridade(),
                        tarefa.getPrazo(),
                        Mapeadores.resumo(tarefa.getResponsavel()),
                        mapearEtiquetas(tarefa),
                        comentariosPorTarefa.getOrDefault(tarefa.getId(), 0L),
                        tarefa.getOrdem(),
                        tarefa.getDataConclusao()));
            }
            cartoes.sort((a, b) -> Integer.compare(a.ordem(), b.ordem()));
            colunasDto.add(new QuadroDtos.RespostaColunaQuadro(
                    coluna.getId(), coluna.getNome(), coluna.getOrdem(), coluna.isAtiva(),
                    coluna.isColunaConclusao(), cartoes));
        }
        return new QuadroDtos.RespostaQuadro(projetoId, colunasDto);
    }

    /** Colunas do projeto (incluindo arquivadas) para a área de configuração do OWNER. */
    @Transactional(readOnly = true)
    public List<QuadroDtos.RespostaColunaQuadro> listarColunas(Long projetoId, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.OWNER);
        List<QuadroDtos.RespostaColunaQuadro> resposta = new ArrayList<>();
        for (ColunaKanban coluna : colunaRepositorio.findByProjetoIdOrderByOrdem(projetoId)) {
            resposta.add(new QuadroDtos.RespostaColunaQuadro(
                    coluna.getId(), coluna.getNome(), coluna.getOrdem(), coluna.isAtiva(),
                    coluna.isColunaConclusao(), List.of()));
        }
        return resposta;
    }

    // ---------------------------------------------------------------- colunas (OWNER)

    @Transactional
    public QuadroDtos.RespostaColunaQuadro criarColuna(Long projetoId, String nome, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.OWNER);
        Projeto projeto = verificador.projetoPorId(projetoId);
        String nomeLimpa = nome.trim();

        if (colunaRepositorio.existsByProjetoIdAndNomeIgnoreCase(projetoId, nomeLimpa)) {
            throw new ConflitoException("Já existe uma coluna com este nome no projeto.");
        }
        int novaOrdem = (int) colunaRepositorio.countByProjetoId(projetoId);
        ColunaKanban coluna = new ColunaKanban(projeto, nomeLimpa, novaOrdem, false);
        colunaRepositorio.save(coluna);
        projeto.addColuna(coluna);

        servicoAtividade.registrar(projeto, ator, TipoAtividade.COLUNA_CRIADA, ServicoAtividade.ENT_COLUNA,
                coluna.getId(), "Criou a coluna '%s'.".formatted(nomeLimpa));
        return resposta(coluna);
    }

    @Transactional
    public QuadroDtos.RespostaColunaQuadro atualizarColuna(Long projetoId, Long colunaId,
                                                           QuadroDtos.AtualizarColuna dto, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.OWNER);
        ColunaKanban coluna = colunaRepositorio.findByIdAndProjetoId(colunaId, projetoId)
                .orElseThrow(() -> new com.flowtask.excecao.RecursoNaoEncontradoException("Coluna não encontrada neste projeto."));
        Projeto projeto = verificador.projetoPorId(projetoId);

        String nomeNovo = dto.nome().trim();
        if (!nomeNovo.equalsIgnoreCase(coluna.getNome())
                && colunaRepositorio.existsByProjetoIdAndNomeIgnoreCaseAndIdNot(projetoId, nomeNovo, colunaId)) {
            throw new ConflitoException("Já existe uma coluna com este nome no projeto.");
        }

        String nomeAnterior = coluna.getNome();
        Map<String, Object> antes = new HashMap<>();
        Map<String, Object> depois = new HashMap<>();
        boolean mudou = false;

        if (!nomeNovo.equals(coluna.getNome())) {
            antes.put("nome", nomeAnterior);
            coluna.setNome(nomeNovo);
            depois.put("nome", nomeNovo);
            mudou = true;
        }

        if (dto.colunaConclusao() != null && dto.colunaConclusao() != coluna.isColunaConclusao()) {
            aplicarColunaConclusao(projetoId, coluna, dto.colunaConclusao());
            antes.put("colunaConclusao", coluna.isColunaConclusao());
            depois.put("colunaConclusao", dto.colunaConclusao());
            mudou = true;
        }

        if (mudou) {
            servicoAtividade.registrar(projeto, ator, TipoAtividade.COLUNA_RENOMEADA, ServicoAtividade.ENT_COLUNA,
                    coluna.getId(), "Atualizou a coluna '%s'.".formatted(coluna.getNome()), antes, depois);
            return resposta(coluna);
        }
        return resposta(coluna);
    }

    /**
     * Garante que apenas UMA coluna do projeto conclui tarefas. Ao ativar/desativar
     * o sinalizador, os dados de conclusão das tarefas da coluna são sincronizados.
     */
    private void aplicarColunaConclusao(Long projetoId, ColunaKanban coluna, boolean novaColunaConclusao) {
        if (novaColunaConclusao) {
            colunaRepositorio.findByProjetoIdAndColunaConclusaoTrue(projetoId)
                    .ifPresent(anterior -> {
                        if (!anterior.getId().equals(coluna.getId())) {
                            anterior.setColunaConclusao(false);
                        }
                    });
            coluna.setColunaConclusao(true);
            tarefaRepositorio.marcarConclusaoDasTarefas(coluna.getId(), LocalDateTime.now());
        } else {
            coluna.setColunaConclusao(false);
            tarefaRepositorio.limparConclusaoDasTarefas(coluna.getId(), LocalDateTime.now());
        }
    }

    @Transactional
    public void reordenar(Long projetoId, List<Long> colunasIds, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.OWNER);
        Projeto projeto = verificador.projetoPorId(projetoId);

        List<ColunaKanban> colunas = colunaRepositorio.findByProjetoIdOrderByOrdem(projetoId);
        Set<Long> idsAtuais = new HashSet<>();
        Map<Long, ColunaKanban> porId = new HashMap<>();
        for (ColunaKanban c : colunas) {
            idsAtuais.add(c.getId());
            porId.put(c.getId(), c);
        }
        if (!new HashSet<>(colunasIds).equals(idsAtuais) || colunasIds.size() != colunas.size()) {
            throw new RegraNegocioException(
                    "A lista enviada deve conter TODAS as colunas do projeto, exatamente uma vez.");
        }

        for (int i = 0; i < colunasIds.size(); i++) {
            ColunaKanban coluna = porId.get(colunasIds.get(i));
            coluna.setOrdem(i);
        }

        servicoAtividade.registrar(projeto, ator, TipoAtividade.COLUNA_REORDENADA, ServicoAtividade.ENT_COLUNA,
                projeto.getId(), String.format("Reordenou as colunas do quadro (%s).",
                        String.join(" > ", colunasIds.stream().map(String::valueOf).toList())));
    }

    @Transactional
    public QuadroDtos.RespostaColunaQuadro arquivar(Long projetoId, Long colunaId, boolean ativa, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.OWNER);
        ColunaKanban coluna = colunaRepositorio.findByIdAndProjetoId(colunaId, projetoId)
                .orElseThrow(() -> new com.flowtask.excecao.RecursoNaoEncontradoException("Coluna não encontrada neste projeto."));
        Projeto projeto = verificador.projetoPorId(projetoId);

        coluna.setAtiva(ativa);
        servicoAtividade.registrar(projeto, ator, TipoAtividade.COLUNA_ARQUIVADA, ServicoAtividade.ENT_COLUNA,
                coluna.getId(),
                ativa ? "Restaurou a coluna '%s'.".formatted(coluna.getNome())
                        : "Arquivou a coluna '%s'.".formatted(coluna.getNome()));
        return resposta(coluna);
    }

    @Transactional
    public void remover(Long projetoId, Long colunaId, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.OWNER);
        ColunaKanban coluna = colunaRepositorio.findByIdAndProjetoId(colunaId, projetoId)
                .orElseThrow(() -> new com.flowtask.excecao.RecursoNaoEncontradoException("Coluna não encontrada neste projeto."));
        Projeto projeto = verificador.projetoPorId(projetoId);

        long tarefas = tarefaRepositorio.countByColunaId(colunaId);
        if (tarefas > 0) {
            throw new ConflitoException(
                    "A coluna '%s' contém %d tarefa(s). Mova ou arquive as tarefas antes de excluir a coluna."
                            .formatted(coluna.getNome(), tarefas));
        }
        projeto.removeColuna(coluna);
        colunaRepositorio.delete(coluna);

        servicoAtividade.registrar(projeto, ator, TipoAtividade.COLUNA_REMOVIDA, ServicoAtividade.ENT_COLUNA,
                colunaId, "Removeu a coluna '%s'.".formatted(coluna.getNome()));
    }

    // ---------------------------------------------------------------- helpers

    private Map<Long, Long> contarComentarios(Long projetoId) {
        Map<Long, Long> contagens = new HashMap<>();
        for (Object[] linha : comentarioRepositorio.contarPorProjeto(projetoId)) {
            Long tarefaId = (Long) linha[0];
            Long total = (Long) linha[1];
            contagens.put(tarefaId, total);
        }
        return contagens;
    }

    private List<ColaboracaoDtos.RespostaEtiqueta> mapearEtiquetas(Tarefa tarefa) {
        List<ColaboracaoDtos.RespostaEtiqueta> etiquetas = new ArrayList<>();
        for (com.flowtask.dominio.Etiqueta etiqueta : tarefa.getEtiquetas()) {
            etiquetas.add(new ColaboracaoDtos.RespostaEtiqueta(etiqueta.getId(), etiqueta.getNome(), etiqueta.getCor()));
        }
        return etiquetas;
    }

    private QuadroDtos.RespostaColunaQuadro resposta(ColunaKanban coluna) {
        return new QuadroDtos.RespostaColunaQuadro(
                coluna.getId(), coluna.getNome(), coluna.getOrdem(), coluna.isAtiva(),
                coluna.isColunaConclusao(), List.of());
    }
}
