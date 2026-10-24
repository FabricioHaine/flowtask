package com.flowtask.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.flowtask.dominio.EstadoProjeto;
import com.flowtask.dominio.PapelProjeto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** DTOs de projetos, integrantes e transferência de propriedade. */
public final class ProjetoDtos {

    private ProjetoDtos() {
    }

    public record CriarProjeto(
            @NotBlank(message = "Informe o nome do projeto.")
            @Size(min = 3, max = 140, message = "O nome do projeto deve ter de 3 a 140 caracteres.")
            String nome,

            @Size(max = 1000, message = "A descrição deve ter até 1000 caracteres.")
            String descricao,

            LocalDate dataInicio,

            LocalDate prazoFinal) {
    }

    public record AtualizarProjeto(
            @NotBlank(message = "Informe o nome do projeto.")
            @Size(min = 3, max = 140, message = "O nome do projeto deve ter de 3 a 140 caracteres.")
            String nome,

            @Size(max = 1000, message = "A descrição deve ter até 1000 caracteres.")
            String descricao,

            LocalDate dataInicio,

            LocalDate prazoFinal) {
    }

    public record MudarEstadoProjeto(@NotNull(message = "Informe o estado do projeto.") EstadoProjeto estado) {
    }

    public record AlterarFuncaoMembro(@NotNull(message = "Informe a função.") PapelProjeto funcao) {
    }

    public record TrocarProprietario(@NotNull(message = "Informe o novo proprietário.") Long usuarioId) {
    }

    public record RespostaMembro(Long id, UsuarioResumo usuario, PapelProjeto funcao, LocalDateTime criadoEm) {
    }

    public record RespostaProjeto(
            Long id,
            String nome,
            String descricao,
            EstadoProjeto estado,
            LocalDate dataInicio,
            LocalDate prazoFinal,
            LocalDateTime criadoEm,
            LocalDateTime atualizadoEm,
            UsuarioResumo dono,
            PapelProjeto papelUsuario,
            Long totalTarefas,
            Long tarefasConcluidas,
            Integer progressoPct,
            List<UsuarioResumo> membros) {
    }
}
