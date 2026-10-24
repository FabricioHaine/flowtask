package com.flowtask.dominio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "usuarios")
public class Usuario {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "nome", nullable = false, length = 120)
    private String nome;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    /** Hash BCrypt - a senha em texto puro nunca é armazenada. */
    @Column(name = "senha_hash", nullable = false, length = 120)
    private String senhaHash;

    @Column(name = "criado_em", nullable = false)
    private java.time.LocalDateTime criadoEm;

    @Column(name = "atualizado_em", nullable = false)
    private java.time.LocalDateTime atualizadoEm;

    protected Usuario() {
        // usado pelo JPA
    }

    public Usuario(String nome, String email, String senhaHash) {
        this.nome = nome;
        this.email = email;
        this.senhaHash = senhaHash;
        this.criadoEm = java.time.LocalDateTime.now();
        this.atualizadoEm = this.criadoEm;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public String getSenhaHash() {
        return senhaHash;
    }

    public void setSenhaHash(String senhaHash) {
        this.senhaHash = senhaHash;
    }

    public java.time.LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public java.time.LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }

    public void setAtualizadoEm(java.time.LocalDateTime atualizadoEm) {
        this.atualizadoEm = atualizadoEm;
    }
}
