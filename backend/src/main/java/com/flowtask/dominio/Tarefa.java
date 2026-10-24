package com.flowtask.dominio;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

@Entity
@Table(name = "tarefas")
public class Tarefa {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projeto_id", nullable = false)
    private Projeto projeto;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "coluna_id", nullable = false)
    private ColunaKanban coluna;

    @Column(name = "titulo", nullable = false, length = 160)
    private String titulo;

    @Column(name = "descricao", length = 4000)
    private String descricao;

    @Enumerated(EnumType.STRING)
    @Column(name = "prioridade", nullable = false, length = 20)
    private PrioridadeTarefa prioridade;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criador_id", nullable = false)
    private Usuario criador;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "responsavel_id")
    private Usuario responsavel;

    @Column(name = "prazo")
    private LocalDate prazo;

    @Column(name = "data_conclusao")
    private LocalDateTime dataConclusao;

    @Column(name = "ordem", nullable = false)
    private int ordem;

    @Column(name = "arquivada", nullable = false)
    private boolean arquivada;

    @ManyToMany(fetch = FetchType.LAZY)
    @JoinTable(
            name = "tarefas_etiquetas",
            joinColumns = @JoinColumn(name = "tarefa_id"),
            inverseJoinColumns = @JoinColumn(name = "etiqueta_id"))
    private List<Etiqueta> etiquetas = new ArrayList<>();

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private LocalDateTime atualizadoEm;

    /** Versionamento otimista: protege contra duas pessoas alterando a mesma tarefa. */
    @Version
    @Column(name = "versao", nullable = false)
    private Long versao;

    protected Tarefa() {
        // usado pelo JPA
    }

    public Tarefa(Projeto projeto, ColunaKanban coluna, String titulo, PrioridadeTarefa prioridade, Usuario criador) {
        this.projeto = projeto;
        this.coluna = coluna;
        this.setTituloInline(titulo);
        this.prioridade = prioridade;
        this.criador = criador;
        this.criadoEm = LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
    }

    private void setTituloInline(String titulo) {
        this.titulo = titulo;
    }

    public Long getId() {
        return id;
    }

    public Projeto getProjeto() {
        return projeto;
    }

    public ColunaKanban getColuna() {
        return coluna;
    }

    public void setColuna(ColunaKanban coluna) {
        this.coluna = coluna;
    }

    public String getTitulo() {
        return titulo;
    }

    public void setTitulo(String titulo) {
        this.titulo = titulo;
        this.tocar();
    }

    public String getDescricao() {
        return descricao;
    }

    public void setDescricao(String descricao) {
        this.descricao = descricao;
        this.tocar();
    }

    public PrioridadeTarefa getPrioridade() {
        return prioridade;
    }

    public void setPrioridade(PrioridadeTarefa prioridade) {
        this.prioridade = prioridade;
        this.tocar();
    }

    public Usuario getCriador() {
        return criador;
    }

    public Usuario getResponsavel() {
        return responsavel;
    }

    public void setResponsavel(Usuario responsavel) {
        this.responsavel = responsavel;
        this.tocar();
    }

    public LocalDate getPrazo() {
        return prazo;
    }

    public void setPrazo(LocalDate prazo) {
        this.prazo = prazo;
        this.tocar();
    }

    public LocalDateTime getDataConclusao() {
        return dataConclusao;
    }

    public void setDataConclusao(LocalDateTime dataConclusao) {
        this.dataConclusao = dataConclusao;
    }

    public int getOrdem() {
        return ordem;
    }

    public void setOrdem(int ordem) {
        this.ordem = ordem;
    }

    public boolean isArquivada() {
        return arquivada;
    }

    public void setArquivada(boolean arquivada) {
        this.arquivada = arquivada;
        if (arquivada) {
            this.tocar();
        }
    }

    public List<Etiqueta> getEtiquetas() {
        return etiquetas;
    }

    /**
     * Marca o instante de atualização manualmente em mudanças que não passam
     * por setters (ex.: reordenação em lote).
     */
    public void tocar() {
        this.atualizadoEm = LocalDateTime.now();
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }

    /** Uma tarefa está concluída quando está em uma coluna marcada como coluna de conclusão. */
    public boolean isConcluida() {
        return this.coluna != null && this.coluna.isColunaConclusao();
    }

    /** A tarefa está atrasada se tem prazo vencido e não está concluída nem arquivada. */
    public boolean isAtrasada() {
        return this.prazo != null && this.prazo.isBefore(LocalDate.now()) && !isConcluida() && !this.arquivada;
    }
}
