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
@Table(name = "membros_projeto")
public class MembroProjeto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projeto_id", nullable = false)
    private Projeto projeto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "funcao", nullable = false, length = 20)
    private PapelProjeto funcao;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected MembroProjeto() {
        // usado pelo JPA
    }

    public MembroProjeto(Projeto projeto, Usuario usuario, PapelProjeto funcao) {
        this.projeto = projeto;
        this.usuario = usuario;
        this.funcao = funcao;
        this.criadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Projeto getProjeto() {
        return projeto;
    }

    public Usuario getUsuario() {
        return usuario;
    }

    public PapelProjeto getFuncao() {
        return funcao;
    }

    public void setFuncao(PapelProjeto funcao) {
        this.funcao = funcao;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
