package com.flowtask.servico;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowtask.dominio.Atividade;
import com.flowtask.dominio.ColunaKanban;
import com.flowtask.dominio.EstadoProjeto;
import com.flowtask.dominio.MembroProjeto;
import com.flowtask.dominio.PrioridadeTarefa;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.Tarefa;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.dto.DashboardDtos;
import com.flowtask.dto.UsuarioResumo;
import com.flowtask.repositorio.AtividadeRepositorio;
import com.flowtask.repositorio.ColunaRepositorio;
import com.flowtask.repositorio.ContagemTarefasProjeto;
import com.flowtask.repositorio.MembroProjetoRepositorio;
import com.flowtask.repositorio.TarefaRepositorio;

/**
 * Indicadores calculados no back-end respeitando o isolamento dos projetos
 * do usuário. Nada de dados fixos: tudo vem de consultas reais.
 *
 * Definições usadas:
 * - "Em andamento": tarefa não concluída cuja coluna não é a primeira do quadro;
 * - "Pendente": tarefa não concluída e não arquivada (total do backlog);
 * - "Concluídas no período": data de conclusão dentro dos últimos N dias;
 * - "Atrasadas": prazo vencido sem conclusão e sem arquivamento.
 */
@Service
public class ServicoDashboard {

    private static final int LIMITE_ATIVIDADE_RECENTE = 10;

    private final VerificadorAcesso verificador;
    private final MembroProjetoRepositorio membroRepositorio;
    private final TarefaRepositorio tarefaRepositorio;
    private final ColunaRepositorio colunaRepositorio;
    private final AtividadeRepositorio atividadeRepositorio;

    public ServicoDashboard(VerificadorAcesso verificador,
                            MembroProjetoRepositorio membroRepositorio,
                            TarefaRepositorio tarefaRepositorio,
                            ColunaRepositorio colunaRepositorio,
                            AtividadeRepositorio atividadeRepositorio) {
        this.verificador = verificador;
        this.membroRepositorio = membroRepositorio;
        this.tarefaRepositorio = tarefaRepositorio;
        this.colunaRepositorio = colunaRepositorio;
        this.atividadeRepositorio = atividadeRepositorio;
    }

    // ---------------------------------------------------------------- resumo do usuário

    @Transactional(readOnly = true)
    public DashboardDtos.ResumoUsuario resumoUsuario(Usuario ator, int periodoDias) {
        List<MembroProjeto> vinculos = membroRepositorio.listarDoUsuario(ator.getId());
        if (vinculos.isEmpty()) {
            return new DashboardDtos.ResumoUsuario(0, 0, 0, 0, 0,
                    List.of(), Map.of(), List.of(), List.of(), List.of());
        }

        List<Projeto> projetos = vinculos.stream().map(MembroProjeto::getProjeto).toList();
        List<Long> projetoIds = projetos.stream().map(Projeto::getId).toList();
        LocalDate hoje = LocalDate.now();
        LocalDateTime instanteInicio = LocalDateTime.now().minusDays(periodoDias);

        long projetosAtivos = projetos.stream().filter(p -> p.getEstado() == EstadoProjeto.ATIVO).count();

        Map<Long, ContagemTarefasProjeto> contagens = new HashMap<>();
        for (ContagemTarefasProjeto c : membroRepositorio.contarTarefasPorProjeto(ator.getId(), hoje)) {
            contagens.put(c.getProjetoId(), c);
        }

        List<Tarefa> tarefas = tarefaRepositorio.listarAtivasPorProjetos(projetoIds);

        long abertas = 0;
        long emAndamento = 0;
        long atrasadas = 0;
        long concluidasNoPeriodo = 0;
        Map<String, Long> porPrioridade = new HashMap<>();
        Map<String, Long> porColuna = new LinkedHashMap<>();
        Map<String, DashboardDtos.CargaPorResponsavel> carga = new LinkedHashMap<>();

        for (Tarefa tarefa : tarefas) {
            boolean concluida = tarefa.isConcluida();
            if (concluida) {
                if (tarefa.getDataConclusao() != null && tarefa.getDataConclusao().isAfter(instanteInicio)) {
                    concluidasNoPeriodo++;
                }
            } else {
                abertas++;
                if (tarefa.getColuna().getOrdem() > 0) {
                    emAndamento++;
                }
            }
            if (tarefa.isAtrasada()) {
                atrasadas++;
            }
            porPrioridade.merge(tarefa.getPrioridade().name(), 1L, Long::sum);
            porColuna.merge(tarefa.getColuna().getNome(), 1L, Long::sum);
            if (tarefa.getResponsavel() != null) {
                computarCarga(carga, tarefa.getResponsavel(), concluida, tarefa.isAtrasada());
            }
        }

        List<DashboardDtos.ProjetoProximoPrazo> proximos = new ArrayList<>();
        for (Projeto projeto : projetos) {
            boolean encerrado = projeto.getEstado() == EstadoProjeto.CONCLUIDO
                    || projeto.getEstado() == EstadoProjeto.ARQUIVADO;
            if (!encerrado && projeto.getPrazoFinal() != null) {
                LocalDate prazo = projeto.getPrazoFinal();
                if (!prazo.isBefore(hoje) && prazo.isBefore(hoje.plusDays(15))) {
                    ContagemTarefasProjeto contagem = contagens.get(projeto.getId());
                    long total = contagem == null ? 0 : contagem.getTotal();
                    long concluidas = contagem == null ? 0 : contagem.getConcluidas();
                    int progresso = total > 0 ? (int) Math.round((concluidas * 100.0) / total) : 0;
                    proximos.add(new DashboardDtos.ProjetoProximoPrazo(
                            projeto.getId(), projeto.getNome(), prazo, progresso));
                }
            }
        }

        List<ColaboracaoDtos.RespostaAtividade> recentes = new ArrayList<>();
        for (Atividade atividade : atividadeRepositorio
                .listarRecentesDoUsuario(ator.getId(), PageRequest.of(0, LIMITE_ATIVIDADE_RECENTE))) {
            recentes.add(respostaAtividade(atividade));
        }

        return new DashboardDtos.ResumoUsuario(
                projetosAtivos,
                abertas,
                emAndamento,
                concluidasNoPeriodo,
                atrasadas,
                proximos,
                porPrioridade,
                listaContagensOrdenadas(porColuna),
                List.copyOf(ordenarCarga(carga)),
                recentes);
    }

    // ---------------------------------------------------------------- resumo do projeto

    @Transactional(readOnly = true)
    public DashboardDtos.ResumoProjeto resumoProjeto(Long projetoId, Usuario ator, int periodoDias) {
        verificador.exigirAcesso(projetoId, ator);
        Projeto projeto = verificador.projetoPorId(projetoId);

        List<ColunaKanban> colunas = colunaRepositorio.findByProjetoIdOrderByOrdem(projetoId);
        Map<Long, String> nomesDeColuna = new HashMap<>();
        Map<Long, Long> ordemDeColuna = new HashMap<>();
        for (ColunaKanban coluna : colunas) {
            nomesDeColuna.put(coluna.getId(), coluna.getNome());
            ordemDeColuna.put(coluna.getId(), (long) coluna.getOrdem());
        }

        List<MembroProjeto> membros = membroRepositorio.listarPorProjeto(projetoId);
        List<Tarefa> tarefas = tarefaRepositorio.listarAtivasPorProjeto(projetoId);
        LocalDate hoje = LocalDate.now();
        LocalDateTime instanteInicio = LocalDateTime.now().minusDays(periodoDias);

        long atrasadas = 0;
        long concluidasNoPeriodo = 0;
        long concluidas = 0;
        Map<String, Long> porPrioridade = new HashMap<>();
        Map<String, Long> porColuna = new LinkedHashMap<>();
        for (ColunaKanban coluna : colunas) {
            porColuna.put(coluna.getNome(), 0L);
        }
        Map<String, DashboardDtos.CargaPorResponsavel> carga = new LinkedHashMap<>();

        for (Tarefa tarefa : tarefas) {
            boolean conclusao = tarefa.isConcluida();
            if (conclusao) {
                concluidas++;
                if (tarefa.getDataConclusao() != null && tarefa.getDataConclusao().isAfter(instanteInicio)) {
                    concluidasNoPeriodo++;
                }
            } else if (tarefa.isAtrasada()) {
                atrasadas++;
            }
            porPrioridade.merge(tarefa.getPrioridade().name(), 1L, Long::sum);
            porColuna.merge(nomesDeColuna.get(tarefa.getColuna().getId()), 1L, Long::sum);
            if (tarefa.getResponsavel() != null) {
                computarCarga(carga, tarefa.getResponsavel(), conclusao, tarefa.isAtrasada());
            }
        }

        List<DashboardDtos.TarefaProximaPrazo> proximoPrazo = new ArrayList<>();
        tarefas.stream()
                .filter(t -> t.getPrazo() != null && !t.isConcluida())
                .filter(t -> !t.getPrazo().isBefore(hoje))
                .sorted((a, b) -> a.getPrazo().compareTo(b.getPrazo()))
                .limit(5)
                .forEach(t -> proximoPrazo.add(new DashboardDtos.TarefaProximaPrazo(
                        t.getId(), t.getTitulo(), t.getPrazo(), Mapeadores.resumo(t.getResponsavel()))));

        List<ColaboracaoDtos.RespostaAtividade> recentes = new ArrayList<>();
        for (Atividade atividade : atividadeRepositorio
                .listarPorProjeto(projetoId, PageRequest.of(0, LIMITE_ATIVIDADE_RECENTE))) {
            recentes.add(respostaAtividade(atividade));
        }

        int progresso = tarefas.isEmpty() ? 0 : (int) Math.round((concluidas * 100.0) / tarefas.size());
        return new DashboardDtos.ResumoProjeto(
                projeto.getId(),
                tarefas.size(),
                listaContagensOrdenadas(porColuna),
                porPrioridade,
                atrasadas,
                concluidasNoPeriodo,
                progresso,
                proximoPrazo,
                List.copyOf(ordenarCarga(carga)),
                recentes);
    }

    // ---------------------------------------------------------------- helpers

    private void computarCarga(Map<String, DashboardDtos.CargaPorResponsavel> carga, Usuario responsavel,
                               boolean concluida, boolean atrasada) {
        String chave = String.valueOf(responsavel.getId());
        DashboardDtos.CargaPorResponsavel atual = carga.get(chave);
        long atribuidas = atual == null ? 0 : atual.atribuidas();
        long concluidasN = atual == null ? 0 : atual.concluidas();
        long atrasadasN = atual == null ? 0 : atual.atrasadas();
        UsuarioResumo resumo = atual == null ? Mapeadores.resumo(responsavel) : atual.usuario();
        if (parceiroVazio(resumo)) {
            resumo = Mapeadores.resumo(responsavel);
        }
        carga.put(chave, new DashboardDtos.CargaPorResponsavel(
                resumo, atribuidas + 1, concluidasN + (concluida ? 1 : 0), atrasadasN + (atrasada ? 1 : 0)));
    }

    private boolean parceiroVazio(UsuarioResumo resumo) {
        return resumo == null || resumo.id() == null;
    }

    private List<DashboardDtos.CargaPorResponsavel> ordenarCarga(Map<String, DashboardDtos.CargaPorResponsavel> carga) {
        List<DashboardDtos.CargaPorResponsavel> lista = new ArrayList<>(carga.values());
        lista.sort((a, b) -> Long.compare(b.atribuidas(), a.atribuidas()));
        return lista;
    }

    /** Colunas em ordem decrescente por contagem (melhor leitura no dashboard). */
    private List<DashboardDtos.ContagemColunas> listaContagensOrdenadas(Map<String, Long> porColuna) {
        List<Map.Entry<String, Long>> entradas = new ArrayList<>(porColuna.entrySet());
        entradas.sort((a, b) -> Long.compare(b.getValue(), a.getValue()));
        List<DashboardDtos.ContagemColunas> resposta = new ArrayList<>();
        for (Map.Entry<String, Long> entrada : entradas) {
            resposta.add(new DashboardDtos.ContagemColunas(entrada.getKey(), entrada.getValue()));
        }
        return resposta;
    }

    private ColaboracaoDtos.RespostaAtividade respostaAtividade(Atividade atividade) {
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
