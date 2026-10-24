package com.flowtask.servico;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowtask.dominio.ColunaKanban;
import com.flowtask.dominio.Etiqueta;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.PrioridadeTarefa;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.Tarefa;
import com.flowtask.dominio.TipoAtividade;
import com.flowtask.dominio.TipoNotificacao;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.dto.QuadroDtos;
import com.flowtask.dto.TarefaDtos;
import com.flowtask.excecao.AcessoNegadoException;
import com.flowtask.excecao.ConflitoException;
import com.flowtask.excecao.RecursoNaoEncontradoException;
import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.ColunaRepositorio;
import com.flowtask.repositorio.ComentarioRepositorio;
import com.flowtask.repositorio.EtiquetaRepositorio;
import com.flowtask.repositorio.MembroProjetoRepositorio;
import com.flowtask.repositorio.ProjetoRepositorio;
import com.flowtask.repositorio.TarefaRepositorio;
import com.flowtask.repositorio.UsuarioRepositorio;

/**
 * Regras de negócio das tarefas: criação, edição, movimentação persistida no
 * quadro, responsáveis, prazos, prioridades, etiquetas, arquivamento e
 * exclusão protegida. Todas as operações exigem acesso ao projeto e um papel
 * compatível, sempre verificados aqui (nunca apenas no front-end).
 *
 * Permissões adotadas:
 * - OWNER e MANAGER: tudo sobre tarefas do projeto;
 * - MEMBER: consultas, movimentação no quadro e comentários; edita textos,
 *   etiquetas e arquivamento apenas das tarefas em que é o responsável.
 */
@Service
public class ServicoTarefa {

    private final TarefaRepositorio tarefaRepositorio;
    private final ColunaRepositorio colunaRepositorio;
    private final ComentarioRepositorio comentarioRepositorio;
    private final EtiquetaRepositorio etiquetaRepositorio;
    private final MembroProjetoRepositorio membroRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final ProjetoRepositorio projetoRepositorio;
    private final VerificadorAcesso verificador;
    private final ServicoAtividade servicoAtividade;
    private final ServicoNotificacao servicoNotificacao;

    public ServicoTarefa(TarefaRepositorio tarefaRepositorio,
                         ColunaRepositorio colunaRepositorio,
                         ComentarioRepositorio comentarioRepositorio,
                         EtiquetaRepositorio etiquetaRepositorio,
                         MembroProjetoRepositorio membroRepositorio,
                         UsuarioRepositorio usuarioRepositorio,
                         ProjetoRepositorio projetoRepositorio,
                         VerificadorAcesso verificador,
                         ServicoAtividade servicoAtividade,
                         ServicoNotificacao servicoNotificacao) {
        this.tarefaRepositorio = tarefaRepositorio;
        this.colunaRepositorio = colunaRepositorio;
        this.comentarioRepositorio = comentarioRepositorio;
        this.etiquetaRepositorio = etiquetaRepositorio;
        this.membroRepositorio = membroRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.projetoRepositorio = projetoRepositorio;
        this.verificador = verificador;
        this.servicoAtividade = servicoAtividade;
        this.servicoNotificacao = servicoNotificacao;
    }

    // --------------------------------------------------------------- criação

    @Transactional
    public TarefaDtos.RespostaTarefa criar(Long projetoId, TarefaDtos.CriarTarefa dto, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.MANAGER);
        Projeto projeto = verificador.projetoPorId(projetoId);

        ColunaKanban coluna = colunaRepositorio.findByIdAndProjetoId(dto.colunaId(), projetoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Coluna não encontrada neste projeto."));
        if (!coluna.isAtiva()) {
            throw new RegraNegocioException(
                    "Não é possível adicionar tarefas em colunas arquivadas. Restaure a coluna antes.");
        }

        PrioridadeTarefa prioridade = dto.prioridade() == null ? PrioridadeTarefa.MEDIA : dto.prioridade();
        Usuario responsavel = validarResponsavel(projetoId, dto.responsavelId());

        int novaOrdem = (int) tarefaRepositorio.countByColunaId(coluna.getId());
        Tarefa tarefa = new Tarefa(projeto, coluna, dto.titulo().trim(), prioridade, ator);
        tarefa.setDescricao(dto.descricao() == null ? null : dto.descricao().trim());
        tarefa.setResponsavel(responsavel);
        tarefa.setPrazo(dto.prazo());
        tarefa.setOrdem(novaOrdem);
        atribuirEtiquetas(tarefa, projeto, dto.etiquetasIds());
        tarefaRepositorio.save(tarefa);

        servicoAtividade.registrar(projeto, ator, TipoAtividade.TAREFA_CRIADA, ServicoAtividade.ENT_TAREFA,
                tarefa.getId(),
                "Criou a tarefa '%s' na coluna '%s'.".formatted(tarefa.getTitulo(), coluna.getNome()));

        if (responsavel != null) {
            servicoNotificacao.criarSeDiferenteDoAtor(ator, responsavel, TipoNotificacao.TAREFA_ATRIBUIDA,
                    "Você se tornou responsável pela tarefa '%s' (projeto '%s')."
                            .formatted(tarefa.getTitulo(), projeto.getNome()),
                    projeto, tarefa);
        }
        return resposta(tarefa, 0L);
    }

    // --------------------------------------------------------------- consultas

    @Transactional(readOnly = true)
    public List<TarefaDtos.RespostaTarefa> listar(Long projetoId, String busca, Long responsavelId,
                                                  PrioridadeTarefa prioridade, Long colunaId,
                                                  boolean arquivadas, Usuario ator) {
        verificador.exigirAcesso(projetoId, ator);
        List<Tarefa> tarefas = arquivadas
                ? tarefaRepositorio.listarArquivadasPorProjeto(projetoId)
                : tarefaRepositorio.listarAtivasPorProjeto(projetoId);
        Map<Long, Long> comentarios = contarComentarios(projetoId);

        String alvo = busca == null ? null : busca.trim().toLowerCase(Locale.ROOT);
        List<TarefaDtos.RespostaTarefa> respostas = new ArrayList<>();
        for (Tarefa tarefa : tarefas) {
            if (alvo != null && !alvo.isBlank()
                    && !tarefa.getTitulo().toLowerCase(Locale.ROOT).contains(alvo)) {
                continue;
            }
            if (responsavelId != null
                    && (tarefa.getResponsavel() == null || !tarefa.getResponsavel().getId().equals(responsavelId))) {
                continue;
            }
            if (prioridade != null && tarefa.getPrioridade() != prioridade) {
                continue;
            }
            if (colunaId != null && !tarefa.getColuna().getId().equals(colunaId)) {
                continue;
            }
            respostas.add(resposta(tarefa, comentarios.getOrDefault(tarefa.getId(), 0L)));
        }
        return respostas;
    }

    @Transactional(readOnly = true)
    public TarefaDtos.RespostaTarefa obter(Long tarefaId, Usuario ator) {
        Tarefa tarefa = verificador.exigirTarefaAcessivel(tarefaId, ator);
        return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
    }

    @Transactional(readOnly = true)
    public List<TarefaDtos.RespostaTarefa> minhasTarefas(Usuario ator) {
        List<Tarefa> tarefas = tarefaRepositorio.listarDoResponsavel(ator.getId());
        List<TarefaDtos.RespostaTarefa> respostas = new ArrayList<>(tarefas.size());
        for (Tarefa tarefa : tarefas) {
            respostas.add(resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefa.getId())));
        }
        return respostas;
    }

    // --------------------------------------------------------------- move (kanban)

    /**
     * Movimento no quadro: valida permissões e destinatário, reordena a coluna
     * de destino, atualiza status/conclusão e registra atividade. O projeto é
     * travado pessimisticamente para serializar movimentos concorrentes; a
     * versão da tarefa protege contra edições simultâneas.
     */
    @Transactional
    public TarefaDtos.RespostaTarefa mover(Long tarefaId, QuadroDtos.MoverTarefa dto, Usuario ator) {
        Tarefa tarefa = tarefaRepositorio.buscarCompleta(tarefaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tarefa não encontrada."));
        Long projetoId = tarefa.getProjeto().getId();
        Projeto projeto = verificador.projetoPorId(projetoId);
        verificador.exigirAcesso(projetoId, ator);

        if (projeto.getEstado() == com.flowtask.dominio.EstadoProjeto.ARQUIVADO) {
            throw new RegraNegocioException("O projeto está arquivado. Restaure-o para movimentar tarefas.");
        }
        projetoRepositorio.buscarComBloqueio(projetoId); // serializa movimentos do mesmo quadro

        if (tarefa.isArquivada()) {
            throw new RegraNegocioException("Tarefa arquivada não pode ser movimentada. Restaure-a primeiro.");
        }
        ColunaKanban destino = colunaRepositorio.findByIdAndProjetoId(dto.colunaId(), projetoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Coluna de destino não encontrada neste projeto."));
        if (!destino.isAtiva()) {
            throw new RegraNegocioException("A coluna de destino está arquivada.");
        }

        boolean mesmaColuna = tarefa.getColuna().getId().equals(destino.getId());
        String nomeOrigem = tarefa.getColuna().getNome();
        boolean antesConcluida = tarefa.getDataConclusao() != null;

        List<Tarefa> naColuna = new ArrayList<>(tarefaRepositorio.findByColunaIdOrderByOrdemAsc(destino.getId()));
        naColuna.removeIf(t -> Objects.equals(t.getId(), tarefaId));
        int indice = Math.max(0, Math.min(dto.indice(), naColuna.size()));
        naColuna.add(indice, tarefa);

        if (!mesmaColuna) {
            tarefa.setColuna(destino);
        }
        for (int i = 0; i < naColuna.size(); i++) {
            Tarefa t = naColuna.get(i);
            t.setOrdem(i);
            if (t != tarefa) {
                t.tocar();
            }
        }
        boolean agoraConcluida = destino.isColunaConclusao();
        if (agoraConcluida) {
            if (tarefa.getDataConclusao() == null) {
                tarefa.setDataConclusao(LocalDateTime.now());
            }
        } else {
            tarefa.setDataConclusao(null);
        }
        tarefa.tocar();

        if (!mesmaColuna || agoraConcluida != antesConcluida) {
            servicoAtividade.registrar(projeto, ator, TipoAtividade.TAREFA_MOVIDA, ServicoAtividade.ENT_TAREFA,
                    tarefaId, "Moveu '%s' de '%s' para '%s'.".formatted(tarefa.getTitulo(), nomeOrigem, destino.getNome()));
        }
        if (agoraConcluida && !antesConcluida) {
            servicoAtividade.registrar(projeto, ator, TipoAtividade.TAREFA_CONCLUIDA, ServicoAtividade.ENT_TAREFA,
                    tarefaId, "Concluiu a tarefa '%s'.".formatted(tarefa.getTitulo()));
            servicoNotificacao.criarSeDiferenteDoAtor(ator, tarefa.getResponsavel(), TipoNotificacao.TAREFA_CONCLUIDA,
                    "A tarefa '%s' foi concluída por %s.".formatted(tarefa.getTitulo(), ator.getNome()),
                    projeto, tarefa);
        } else if (!agoraConcluida && antesConcluida) {
            servicoAtividade.registrar(projeto, ator, TipoAtividade.TAREFA_REABERTA, ServicoAtividade.ENT_TAREFA,
                    tarefaId, "Reabriu a tarefa '%s'.".formatted(tarefa.getTitulo()));
            servicoNotificacao.criarSeDiferenteDoAtor(ator, tarefa.getResponsavel(), TipoNotificacao.TAREFA_REABERTA,
                    "A tarefa '%s' foi reaberta por %s.".formatted(tarefa.getTitulo(), ator.getNome()),
                    projeto, tarefa);
        }
        return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
    }

    // --------------------------------------------------------------- edição

    @Transactional
    public TarefaDtos.RespostaTarefa editarTextos(Long tarefaId, TarefaDtos.EditarTarefaTexto dto, Usuario ator) {
        Tarefa tarefa = exigirTarefaEditavel(tarefaId, ator);
        String tituloAnterior = tarefa.getTitulo();
        String descricaoAnterior = nullVazio(tarefa.getDescricao());

        tarefa.setTitulo(dto.titulo().trim());
        tarefa.setDescricao(dto.descricao() == null ? null : dto.descricao().trim());

        boolean mudou = !tituloAnterior.equals(tarefa.getTitulo())
                || !descricaoAnterior.equals(nullVazio(tarefa.getDescricao()));
        if (mudou) {
            servicoAtividade.registrar(tarefa.getProjeto(), ator, TipoAtividade.TAREFA_ATUALIZADA,
                    ServicoAtividade.ENT_TAREFA, tarefaId, "Atualizou o conteúdo da tarefa '%s'.".formatted(tarefa.getTitulo()),
                    Map.of("titulo", tituloAnterior, "descricao", descricaoAnterior),
                    Map.of("titulo", tarefa.getTitulo(), "descricao", nullVazio(tarefa.getDescricao())));
        }
        return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
    }

    @Transactional
    public TarefaDtos.RespostaTarefa atribuir(Long tarefaId, Long responsavelId, Usuario ator) {
        Tarefa tarefa = exigirTarefaGerencial(tarefaId, ator);
        Long projetoId = tarefa.getProjeto().getId();

        Usuario anterior = tarefa.getResponsavel();
        Usuario novo = validarResponsavel(projetoId, responsavelId);
        if (mesmoUsuario(anterior, novo)) {
            return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
        }
        tarefa.setResponsavel(novo);

        String resumo = novo == null
                ? "Removeu o responsável da tarefa '%s'.".formatted(tarefa.getTitulo())
                : "Definiu %s como responsável pela tarefa '%s'.".formatted(novo.getNome(), tarefa.getTitulo());
        servicoAtividade.registrar(tarefa.getProjeto(), ator, TipoAtividade.TAREFA_ATRIBUIDA,
                ServicoAtividade.ENT_TAREFA, tarefaId, resumo,
                Map.of("responsavel", nomeOuVazio(anterior)), Map.of("responsavel", nomeOuVazio(novo)));

        if (novo != null) {
            servicoNotificacao.criarSeDiferenteDoAtor(ator, novo, TipoNotificacao.TAREFA_ATRIBUIDA,
                    "Você se tornou responsável pela tarefa '%s' (projeto '%s')."
                            .formatted(tarefa.getTitulo(), tarefa.getProjeto().getNome()),
                    tarefa.getProjeto(), tarefa);
        }
        if (anterior != null) {
            servicoNotificacao.criarSeDiferenteDoAtor(ator, anterior, TipoNotificacao.TAREFA_ATRIBUIDA_REMOVIDA,
                    "Você não é mais responsável pela tarefa '%s'.".formatted(tarefa.getTitulo()),
                    tarefa.getProjeto(), tarefa);
        }
        return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
    }

    @Transactional
    public TarefaDtos.RespostaTarefa mudarPrioridade(Long tarefaId, PrioridadeTarefa prioridade, Usuario ator) {
        Tarefa tarefa = exigirTarefaGerencial(tarefaId, ator);
        if (tarefa.getPrioridade() == prioridade) {
            return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
        }
        PrioridadeTarefa anterior = tarefa.getPrioridade();
        tarefa.setPrioridade(prioridade);

        servicoAtividade.registrar(tarefa.getProjeto(), ator, TipoAtividade.TAREFA_PRIORIDADE_ALTERADA,
                ServicoAtividade.ENT_TAREFA, tarefaId,
                "Alterou a prioridade da tarefa '%s' de %s para %s.".formatted(tarefa.getTitulo(), anterior, prioridade),
                Map.of("prioridade", anterior), Map.of("prioridade", prioridade));
        servicoNotificacao.criarSeDiferenteDoAtor(ator, tarefa.getResponsavel(), TipoNotificacao.TAREFA_PRIORIDADE_ALTERADA,
                "A prioridade da tarefa '%s' mudou para %s.".formatted(tarefa.getTitulo(), prioridade),
                tarefa.getProjeto(), tarefa);
        return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
    }

    @Transactional
    public TarefaDtos.RespostaTarefa mudarPrazo(Long tarefaId, LocalDate prazo, Usuario ator) {
        Tarefa tarefa = exigirTarefaGerencial(tarefaId, ator);
        if (Objects.equals(tarefa.getPrazo(), prazo)) {
            return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
        }
        LocalDate anterior = tarefa.getPrazo();
        tarefa.setPrazo(prazo);

        servicoAtividade.registrar(tarefa.getProjeto(), ator, TipoAtividade.TAREFA_PRAZO_ALTERADO,
                ServicoAtividade.ENT_TAREFA, tarefaId,
                "Alterou o prazo da tarefa '%s' de %s para %s.".formatted(tarefa.getTitulo(),
                        dataNao(anterior), dataNao(prazo)),
                Map.of("prazo", dataNao(anterior)), Map.of("prazo", dataNao(prazo)));
        servicoNotificacao.criarSeDiferenteDoAtor(ator, tarefa.getResponsavel(), TipoNotificacao.TAREFA_PRAZO_ALTERADO,
                "O prazo da tarefa '%s' agora é %s.".formatted(tarefa.getTitulo(), dataNao(prazo)),
                tarefa.getProjeto(), tarefa);
        return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
    }

    @Transactional
    public TarefaDtos.RespostaTarefa atualizarEtiquetas(Long tarefaId, List<Long> etiquetasIds, Usuario ator) {
        Tarefa tarefa = exigirTarefaEditavel(tarefaId, ator);
        Projeto projeto = verificador.projetoPorId(tarefa.getProjeto().getId());

        List<Etiqueta> novas = new ArrayList<>();
        for (Long etiquetaId : etiquetasIds) {
            Etiqueta etiqueta = etiquetaRepositorio.findByIdAndProjetoId(etiquetaId, projeto.getId())
                    .orElseThrow(() -> new RegraNegocioException("Etiqueta inválida para este projeto."));
            novas.add(etiqueta);
        }
        tarefa.getEtiquetas().clear();
        tarefa.getEtiquetas().addAll(novas);
        tarefa.tocar();

        servicoAtividade.registrar(projeto, ator, TipoAtividade.TAREFA_ETIQUETAS_ALTERADAS,
                ServicoAtividade.ENT_TAREFA, tarefaId,
                "Definiu %d etiqueta(s) na tarefa '%s'.".formatted(novas.size(), tarefa.getTitulo()));
        return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
    }

    // --------------------------------------------------------------- arquivo/exclusão

    @Transactional
    public TarefaDtos.RespostaTarefa arquivar(Long tarefaId, boolean arquivar, Usuario ator) {
        Tarefa tarefa = exigirTarefaEditavel(tarefaId, ator);
        if (arquivar == tarefa.isArquivada()) {
            return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
        }

        if (arquivar) {
            tarefa.setArquivada(true);
            servicoAtividade.registrar(tarefa.getProjeto(), ator, TipoAtividade.TAREFA_ARQUIVADA,
                    ServicoAtividade.ENT_TAREFA, tarefaId, "Arquivou a tarefa '%s'.".formatted(tarefa.getTitulo()));
        } else {
            // Ao restaurar, garante que a tarefa volte para uma coluna ativa do projeto.
            ColunaKanban colunaAtual = tarefa.getColuna();
            if (!colunaAtual.isAtiva()) {
                ColunaKanban destino = colunaRepositorio.listarAtivasPorProjeto(tarefa.getProjeto().getId())
                        .stream().findFirst()
                        .orElseThrow(() -> new RegraNegocioException(
                                "Restaure pelo menos uma coluna ativa antes de restaurar tarefas."));
                tarefa.setColuna(destino);
                tarefa.setOrdem((int) tarefaRepositorio.countByColunaId(destino.getId()));
            }
            tarefa.setArquivada(false);
            servicoAtividade.registrar(tarefa.getProjeto(), ator, TipoAtividade.TAREFA_ARQUIVADA,
                    ServicoAtividade.ENT_TAREFA, tarefaId, "Restaurou a tarefa '%s'.".formatted(tarefa.getTitulo()));
        }
        tarefa.tocar();
        return resposta(tarefa, comentarioRepositorio.countByTarefaId(tarefaId));
    }

    @Transactional
    public void excluir(Long tarefaId, Usuario ator) {
        Tarefa tarefa = verificador.exigirTarefaAcessivel(tarefaId, ator);
        Projeto projeto = verificador.projetoPorId(tarefa.getProjeto().getId());
        verificador.exigirPapel(projeto.getId(), ator, PapelProjeto.OWNER);

        long comentarios = comentarioRepositorio.countByTarefaId(tarefaId);
        if (comentarios > 0) {
            throw new ConflitoException(
                    "A tarefa tem %d comentário(s). Prefira arquivá-la para preservar o histórico da conversa."
                            .formatted(comentarios));
        }
        String titulo = tarefa.getTitulo();
        tarefaRepositorio.delete(tarefa);
        servicoAtividade.registrar(projeto, ator, TipoAtividade.TAREFA_REMOVIDA, ServicoAtividade.ENT_TAREFA,
                tarefaId, "Excluiu a tarefa '%s'.".formatted(titulo));
    }

    // --------------------------------------------------------------- helpers

    private Usuario validarResponsavel(Long projetoId, Long responsavelId) {
        if (responsavelId == null) {
            return null;
        }
        Usuario usuario = usuarioRepositorio.findById(responsavelId)
                .orElseThrow(() -> new RegraNegocioException("O responsável informado não existe."));
        if (!membroRepositorio.existsByProjetoIdAndUsuarioId(projetoId, responsavelId)) {
            throw new RegraNegocioException("O responsável precisa ser integrante do projeto.");
        }
        return usuario;
    }

    private void atribuirEtiquetas(Tarefa tarefa, Projeto projeto, List<Long> etiquetasIds) {
        if (etiquetasIds == null) {
            return;
        }
        for (Long etiquetaId : etiquetasIds) {
            Etiqueta etiqueta = etiquetaRepositorio.findByIdAndProjetoId(etiquetaId, projeto.getId())
                    .orElseThrow(() -> new RegraNegocioException("Etiqueta inválida para este projeto."));
            tarefa.getEtiquetas().add(etiqueta);
        }
    }

    /** MANAGER/OWNER totalmente; MEMBER responsável pelo próprio progresso. */
    private Tarefa exigirTarefaEditavel(Long tarefaId, Usuario ator) {
        Tarefa tarefa = verificador.exigirTarefaAcessivel(tarefaId, ator);
        PapelProjeto papel = verificador.papelNoProjeto(tarefa.getProjeto().getId(), ator.getId());
        boolean pode = papel.temAoMenos(PapelProjeto.MANAGER)
                || (tarefa.getResponsavel() != null && tarefa.getResponsavel().getId().equals(ator.getId()));
        if (!pode) {
            throw new AcessoNegadoException(
                    "Somente MANAGER, OWNER ou o próprio responsável podem editar esta tarefa.");
        }
        return tarefa;
    }

    private Tarefa exigirTarefaGerencial(Long tarefaId, Usuario ator) {
        Tarefa tarefa = verificador.exigirTarefaAcessivel(tarefaId, ator);
        verificador.exigirPapel(tarefa.getProjeto().getId(), ator, PapelProjeto.MANAGER);
        return tarefa;
    }

    private String nomeOuVazio(Usuario usuario) {
        return usuario == null ? "" : usuario.getNome();
    }

    private String dataNao(LocalDate data) {
        return data == null ? "sem prazo" : data.toString();
    }

    private String nullVazio(String texto) {
        return texto == null ? "" : texto;
    }

    private boolean mesmoUsuario(Usuario a, Usuario b) {
        if (a == null && b == null) {
            return true;
        }
        return a != null && b != null && a.getId().equals(b.getId());
    }

    private Map<Long, Long> contarComentarios(Long projetoId) {
        Map<Long, Long> contagens = new HashMap<>();
        for (Object[] linha : comentarioRepositorio.contarPorProjeto(projetoId)) {
            contagens.put((Long) linha[0], (Long) linha[1]);
        }
        return contagens;
    }

    /** Mapeia a entidade em DTO completo—todas as referências já estão inicializadas. */
    private TarefaDtos.RespostaTarefa resposta(Tarefa tarefa, long totalComentarios) {
        List<ColaboracaoDtos.RespostaEtiqueta> etiquetas = new ArrayList<>();
        for (Etiqueta etiqueta : tarefa.getEtiquetas()) {
            etiquetas.add(new ColaboracaoDtos.RespostaEtiqueta(etiqueta.getId(), etiqueta.getNome(), etiqueta.getCor()));
        }
        boolean concluida = tarefa.getColuna().isColunaConclusao();
        return new TarefaDtos.RespostaTarefa(
                tarefa.getId(),
                tarefa.getProjeto().getId(),
                tarefa.getProjeto().getNome(),
                tarefa.getColuna().getId(),
                tarefa.getColuna().getNome(),
                tarefa.getTitulo(),
                tarefa.getDescricao(),
                tarefa.getPrioridade(),
                com.flowtask.servico.Mapeadores.resumo(tarefa.getCriador()),
                com.flowtask.servico.Mapeadores.resumo(tarefa.getResponsavel()),
                tarefa.getPrazo(),
                tarefa.getDataConclusao(),
                tarefa.getOrdem(),
                tarefa.isArquivada(),
                concluida,
                tarefa.getCriadoEm(),
                tarefa.getAtualizadoEm(),
                etiquetas,
                totalComentarios);
    }
}
