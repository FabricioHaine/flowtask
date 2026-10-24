package com.flowtask.dominio;

import java.time.LocalDateTime;

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
@Table(name = "comentarios")
public class Comentario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tarefa_id", nullable = false)
    private Tarefa tarefa;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "autor_id", nullable = false)
    private Usuario autor;

    @Column(name = "conteudo", nullable = false, length = 4000)
    private String conteudo;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "editado_em")
    private LocalDateTime editadoEm;

    protected Comentario() {
        // usado pelo JPA
    }

    public Comentario(Tarefa tarefa, Usuario autor, String conteudo) {
        this.tarefa = tarefa;
        this.autor = autor;
        this.conteudo = conteudo;
        this.criadoEm = LocalDateTime.now();
    }

    public void editar(String novoConteudo) {
        this.conteudo = novoConteudo;
        this.editadoEm = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Tarefa getTarefa() {
        return tarefa;
    }

    public Usuario getAutor() {
        return autor;
    }

    public String getConteudo() {
        return conteudo;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getEditadoEm() {
        return editadoEm;
    }
}
