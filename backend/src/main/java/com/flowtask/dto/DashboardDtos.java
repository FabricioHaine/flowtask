package com.flowtask.dto;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/** DTOs de dashboard e indicadores (dados calculados pelo back-end). */
public final class DashboardDtos {

    private DashboardDtos() {
    }

    public record ContagemColunas(String nome, long quantidade) {
    }

    public record ProjetoProximoPrazo(Long id, String nome, LocalDate prazoFinal, Integer progressoPct) {
    }

    public record TarefaProximaPrazo(Long id, String titulo, LocalDate prazo, UsuarioResumo responsavel) {
    }

    public record CargaPorResponsavel(UsuarioResumo usuario, long atribuidas, long concluidas, long atrasadas) {
    }

    public record ResumoUsuario(
            long projetosAtivos,
            long tarefasAbertas,
            long tarefasEmAndamento,
            long tarefasConcluidasPeriodo,
            long tarefasAtrasadas,
            List<ProjetoProximoPrazo> projetosProximosDoPrazo,
            Map<String, Long> porPrioridade,
            List<ContagemColunas> porColuna,
            List<CargaPorResponsavel> porResponsavel,
            List<ColaboracaoDtos.RespostaAtividade> atividadeRecente) {
    }

    public record ResumoProjeto(
            long projetoId,
            long totalTarefas,
            List<ContagemColunas> porColuna,
            Map<String, Long> porPrioridade,
            long atrasadas,
            long concluidasPeriodo,
            Integer progressoPct,
            List<TarefaProximaPrazo> proximasDoPrazo,
            List<CargaPorResponsavel> cargaPorMembro,
            List<ColaboracaoDtos.RespostaAtividade> atividadeRecente) {
    }
}
