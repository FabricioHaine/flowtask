package com.flowtask.dto;

import java.time.LocalDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** DTOs de cadastro, login e perfil. */
public final class AutenticacaoDtos {

    private AutenticacaoDtos() {
    }

    public record CadastrarConta(
            @NotBlank(message = "Informe seu nome.")
            @Size(max = 120, message = "O nome deve ter até 120 caracteres.")
            String nome,

            @NotBlank(message = "Informe um e-mail.")
            @Email(message = "E-mail inválido.")
            @Size(max = 255, message = "E-mail muito longo.")
            String email,

            @NotBlank(message = "Informe uma senha.")
            @Pattern(
                    regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,64}$",
                    message = "A senha deve ter de 8 a 64 caracteres, com pelo menos 1 letra e 1 número.")
            String senha) {
    }

    public record EntrarConta(
            @NotBlank(message = "Informe o e-mail.")
            @Email(message = "E-mail inválido.")
            String email,

            @NotBlank(message = "Informe a senha.")
            String senha) {
    }

    public record AtualizarPerfil(
            @NotBlank(message = "Informe o nome.")
            @Size(max = 120, message = "O nome deve ter até 120 caracteres.")
            String nome) {
    }

    public record RespostaUsuario(Long id, String nome, String email, LocalDateTime criadoEm) {
    }

    public record RespostaLogin(String token, long expiraEmSegundos, RespostaUsuario usuario) {
    }
}
