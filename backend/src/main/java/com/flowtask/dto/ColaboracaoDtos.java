package com.flowtask.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import com.flowtask.dominio.PrioridadeTarefa;
import com.flowtask.dominio.TipoAtividade;
import com.flowtask.dominio.TipoNotificacao;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de etiquetas, comentários, atividades e notificações. */
public final class ColaboracaoDtos {

    private ColaboracaoDtos() {
    }

    // ------------------------------ Etiquetas ------------------------------
    public record CriarEtiqueta(
            @NotBlank(message = "Informe o nome da etiqueta.")
            @Size(min = 2, max = 40, message = "O nome da etiqueta deve ter de 2 a 40 caracteres.")
            String nome,

            @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "A cor deve seguir o padrão #RRGGBB.")
            String cor) {
    }

    public record RespostaEtiqueta(Long id, String nome, String cor) {
    }

    // ------------------------------ Comentários ------------------------------
    public record CriarComentario(
            @NotBlank(message = "Escreva o conteúdo do comentário.")
            @Size(min = 1, max = 4000, message = "O comentário pode ter até 4000 caracteres.")
            String conteudo) {
    }

    public record RespostaComentario(Long id, UsuarioResumo autor, String conteudo,
                                     LocalDateTime criadoEm, LocalDateTime editadoEm) {
    }

    // ------------------------------ Atividades ------------------------------
    public record RespostaAtividade(Long id, TipoAtividade tipo, String entidade, Long entidadeId,
                                    String resumo, String dadosAnteriores, String dadosPosteriores,
                                    UsuarioResumo usuario, LocalDateTime criadoEm) {
    }

    // ------------------------------ Notificações ------------------------------
    public record RespostaNotificacao(Long id, TipoNotificacao tipo, String mensagem, boolean lida,
                                      Long projetoId, Long tarefaId, LocalDateTime criadoEm) {
    }

    public record RespostaContagem(long naoLidas) {
    }
}
