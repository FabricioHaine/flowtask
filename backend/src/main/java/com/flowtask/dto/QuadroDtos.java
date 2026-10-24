package com.flowtask.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.flowtask.dominio.PrioridadeTarefa;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs do quadro kanban (colunas, tarefas no cartão e reordenação). */
public final class QuadroDtos {

    private QuadroDtos() {
    }

    public record RespostaTarefaQuadro(
            Long id,
            String titulo,
            PrioridadeTarefa prioridade,
            LocalDate prazo,
            UsuarioResumo responsavel,
            List<ColaboracaoDtos.RespostaEtiqueta> etiquetas,
            long totalComentarios,
            int ordem,
            LocalDateTime dataConclusao) {
    }

    public record RespostaColunaQuadro(
            Long id,
            String nome,
            int ordem,
            boolean ativa,
            boolean colunaConclusao,
            List<RespostaTarefaQuadro> tarefas) {
    }

    public record RespostaQuadro(Long projetoId, List<RespostaColunaQuadro> colunas) {
    }

    public record CriarColuna(
            @NotBlank(message = "Informe o nome da coluna.")
            @Size(min = 2, max = 80, message = "O nome da coluna deve ter de 2 a 80 caracteres.")
            String nome) {
    }

    public record AtualizarColuna(
            @NotBlank(message = "Informe o nome da coluna.")
            @Size(min = 2, max = 80, message = "O nome da coluna deve ter de 2 a 80 caracteres.")
            String nome,

            Boolean colunaConclusao) {
    }

    public record ReordenarColunas(
            @NotNull(message = "Informe a lista de colunas.")
            @NotEmpty(message = "A lista de colunas não pode ser vazia.")
            List<Long> colunasIds) {
    }

    public record ArquivarColuna(
            @NotNull(message = "Informe se a coluna deve ficar ativa.")
            Boolean ativa) {
    }

    public record MoverTarefa(
            @NotNull(message = "Informe a coluna de destino.") Long colunaId,
            @NotNull(message = "Informe a posição da tarefa.") @Min(value = 0, message = "A posição não pode ser negativa.") Integer indice) {
    }
}
