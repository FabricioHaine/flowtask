package com.flowtask.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "colunas_kanban")
public class ColunaKanban {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projeto_id", nullable = false)
    private Projeto projeto;

    @Column(name = "nome", nullable = false, length = 80)
    private String nome;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "ativa", nullable = false)
    private boolean ativa = true;

    /** Coluna com este flag conclui (ou reabre) a tarefa movida para ela. */
    @Column(name = "coluna_conclusao", nullable = false)
    private boolean colunaConclusao;

    protected ColunaKanban() {
        // usado pelo JPA
    }

    public ColunaKanban(Projeto projeto, String nome, int ordem, boolean colunaConclusao) {
        this.projeto = projeto;
        this.nome = nome;
        this.ordem = ordem;
        this.colunaConclusao = colunaConclusao;
    }

    public Long getId() {
        return id;
    }

    public Projeto getProjeto() {
        return projeto;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }

    public boolean isColunaConclusao() {
        return colunaConclusao;
    }

    public void setColunaConclusao(boolean colunaConclusao) {
        this.colunaConclusao = colunaConclusao;
    }
}
