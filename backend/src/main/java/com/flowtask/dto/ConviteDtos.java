package com.flowtask.dto;

import java.time.LocalDateTime;

import com.flowtask.dominio.EstadoConvite;
import com.flowtask.dominio.PapelProjeto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/** DTOs do fluxo de convites por e-mail. */
public final class ConviteDtos {

    private ConviteDtos() {
    }

    public record CriarConvite(
            @NotBlank(message = "Informe o e-mail do convidado.")
            @Email(message = "E-mail inválido.")
            @jakarta.validation.constraints.Size(max = 255, message = "E-mail muito longo.")
            String email,

            @NotNull(message = "Informe a função proposta.")
            PapelProjeto funcaoProposta) {
    }

    public record RespostaConvite(
            Long id,
            Long projetoId,
            String projetoNome,
            String email,
            PapelProjeto funcaoProposta,
            UsuarioResumo criadoPor,
            LocalDateTime criadoEm,
            LocalDateTime expiraEm,
            EstadoConvite estado,
            boolean emailCadastrado) {
    }
}
