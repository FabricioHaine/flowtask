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

/**
 * Trilha de auditoria de eventos do projeto. Gravação feita pelo back-end;
 * usuários comuns não podem alterar nem apagar o histórico.
 */
@Entity
@Table(name = "atividades")
public class Atividade {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projeto_id", nullable = false)
    private Projeto projeto;

    /** Ator autenticado que realizou a ação. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "usuario_id", nullable = false)
    private Usuario usuario;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo", nullable = false, length = 60)
    private TipoAtividade tipo;

    @Column(name = "entidade", nullable = false, length = 30)
    private String entidade;

    @Column(name = "entidade_id")
    private Long entidadeId;

    @Column(name = "resumo", nullable = false, length = 300)
    private String resumo;

    @Column(name = "dados_anteriores", length = 2000)
    private String dadosAnteriores;

    @Column(name = "dados_posteriores", length = 2000)
    private String dadosPosteriores;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    protected Atividade() {
        // usado pelo JPA
    }

    public Atividade(Projeto projeto, Usuario usuario, TipoAtividade tipo, String entidade,
                     Long entidadeId, String resumo, String dadosAnteriores, String dadosPosteriores) {
        this.projeto = projeto;
        this.usuario = usuario;
        this.tipo = tipo;
        this.entidade = entidade;
        this.entidadeId = entidadeId;
        this.resumo = resumo;
        this.dadosAnteriores = dadosAnteriores;
        this.dadosPosteriores = dadosPosteriores;
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

    public TipoAtividade getTipo() {
        return tipo;
    }

    public String getEntidade() {
        return entidade;
    }

    public Long getEntidadeId() {
        return entidadeId;
    }

    public String getResumo() {
        return resumo;
    }

    public String getDadosAnteriores() {
        return dadosAnteriores;
    }

    public String getDadosPosteriores() {
        return dadosPosteriores;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }
}
