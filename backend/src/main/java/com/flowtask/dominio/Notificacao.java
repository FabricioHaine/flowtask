package com.flowtask.dominio;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "notificacoes")
public class Notificacao {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "destinatario_id", nullable = false)
    private Usuario destinatario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 60)
    private TipoNotificacao tipo;

    @Column(name = "mensagem", nullable = false, length = 600)
    private String mensagem;

    @Column(name = "lida", nullable = false)
    private boolean lida;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "projeto_id")
    private Projeto projeto;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tarefa_id")
    private Tarefa tarefa;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected Notificacao() {
        // usado pelo JPA
    }

    public Notificacao(Usuario destinatario, TipoNotificacao tipo, String mensagem, Projeto projeto, Tarefa tarefa) {
        this.destinatario = destinatario;
        this.tipo = tipo;
        this.mensagem = mensagem;
        this.projeto = projeto;
        this.tarefa = tarefa;
        this.criadoEm = LocalDateTime.now();
    }

    public void marcarComoLida() {
        this.lida = true;
    }

    public Long getId() {
        return id;
    }

    public Usuario getDestinatario() {
        return destinatario;
    }

    public TipoNotificacao getTipo() {
        return tipo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public boolean isLida() {
        return lida;
    }

    public Projeto getProjeto() {
        return projeto;
    }

    public Tarefa getTarefa() {
        return tarefa;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
