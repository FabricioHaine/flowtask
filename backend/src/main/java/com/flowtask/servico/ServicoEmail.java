package com.flowtask.servico;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

import com.flowtask.config.PropriedadesApp;
import com.flowtask.dominio.Usuario;

/**
 * Envio opcional de e-mail para convites. Habilitado por configuração
 * (app.email.habilitado). Sem SMTP, o fluxo de convite segue local:
 * a API cria o convite e o front-end informa o link para compartilhar.
 */
@Service
public class ServicoEmail {

    private static final Logger LOG = LoggerFactory.getLogger(ServicoEmail.class);

    private final PropriedadesApp propriedades;
    private final JavaMailSender remetenteMail;

    public ServicoEmail(PropriedadesApp propriedades, @Autowired(required = false) JavaMailSender remetenteMail) {
        this.propriedades = propriedades;
        this.remetenteMail = remetenteMail;
    }

    public boolean emailHabilitado() {
        return propriedades.getEmail().isHabilitado() && remetenteMail != null;
    }

    /** Nunca lança exceção: falha de SMTP não pode bloquear a criação do convite. */
    public void enviarConvite(Usuario convidado, String emailDestinatario, String nomeProjeto, String nomeCriador) {
        if (!emailHabilitado()) {
            LOG.info("E-mail não configurado. Convite para '{}' do projeto '{}' segue no fluxo local.",
                    emailDestinatario, nomeProjeto);
            return;
        }
        SimpleMailMessage mensagem = new SimpleMailMessage();
        mensagem.setFrom(propriedades.getEmail().getRemetente());
        mensagem.setTo(emailDestinatario);
        mensagem.setSubject("Você foi convidado para o projeto " + nomeProjeto + " no FlowTask");
        mensagem.setText("""
                Olá!

                %s convidou você para colaborar no projeto '%s' no FlowTask.

                1. Crie sua conta ou faça login com o e-mail %s.
                2. Acesse o menu Notificações ou a lista de convites no aplicativo.
                3. Aceite o convite para participar do projeto.

                Equipe FlowTask
                """.formatted(nomeCriador, nomeProjeto, emailDestinatario));
        try {
            remetenteMail.send(mensagem);
        } catch (Exception e) {
            LOG.warn("Falha ao enviar e-mail de convite para {}: {}", emailDestinatario, e.getMessage());
        }
    }
}
