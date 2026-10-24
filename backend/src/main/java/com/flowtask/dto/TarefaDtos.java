package com.flowtask.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.flowtask.dominio.PrioridadeTarefa;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs de tarefas (criação, edição, movimentação, atribuição e resposta). */
public final class TarefaDtos {

    private TarefaDtos() {
    }

    public record CriarTarefa(
            @NotBlank(message = "Informe o título da tarefa.")
            @Size(min = 3, max = 160, message = "O título deve ter de 3 a 160 caracteres.")
            String titulo,

            @Size(max = 4000, message = "A descrição pode ter até 4000 caracteres.")
            String descricao,

            @NotNull(message = "Informe a coluna de destino.") Long colunaId,

            PrioridadeTarefa prioridade,

            Long responsavelId,

            LocalDate prazo,

            List<Long> etiquetasIds) {
    }

    public record EditarTarefaTexto(
            @NotBlank(message = "Informe o título da tarefa.")
            @Size(min = 3, max = 160, message = "O título deve ter de 3 a 160 caracteres.")
            String titulo,

            @Size(max = 4000, message = "A descrição pode ter até 4000 caracteres.")
            String descricao) {
    }

    public record AtribuirResponsavelTarefa(Long responsavelId) {
    }

    public record MudarPrioridadeTarefa(
            @NotNull(message = "Informe a prioridade.")
            PrioridadeTarefa prioridade) {
    }

    public record MudarPrazoTarefa(LocalDate prazo) {
    }

    public record AtualizarEtiquetasTarefa(
            @NotNull(message = "Informe a lista de etiquetas.")
            List<Long> etiquetasIds) {
    }

    public record ArquivarTarefa(
            @NotNull(message = "Informe se a tarefa deve ficar arquivada.")
            Boolean arquivada) {
    }

    public record RespostaTarefa(
            Long id,
            Long projetoId,
            String projetoNome,
            Long colunaId,
            String colunaNome,
            String titulo,
            String descricao,
            PrioridadeTarefa prioridade,
            UsuarioResumo criador,
            UsuarioResumo responsavel,
            LocalDate prazo,
            LocalDateTime dataConclusao,
            int ordem,
            boolean arquivada,
            boolean concluida,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm,
            List<ColaboracaoDtos.RespostaEtiqueta> etiquetas,
            long totalComentarios) {
    }
}
