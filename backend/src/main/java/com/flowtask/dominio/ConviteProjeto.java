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
@Table(name = "convites_projeto")
public class ConviteProjeto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "projeto_id", nullable = false)
    private Projeto projeto;

    /** E-mail de destino; o convite só pode ser aceite por uma conta com esse e-mail. */
    @Column(name = "email", nullable = false, length = 255)
    private String email;

    @Enumerated(EnumType.STRING)
    @Column(name = "funcao_proposta", nullable = false, length = 20)
    private PapelProjeto funcaoProposta;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "criado_por", nullable = false)
    private Usuario criadoPor;

    @Column(name = "criado_em", nullable = false)
    private LocalDateTime criadoEm;

    @Column(name = "expira_em", nullable = false)
    private LocalDateTime expiraEm;

    @Enumerated(EnumType.STRING)
    @Column(name = "estado", nullable = false, length = 20)
    private EstadoConvite estado;

    protected ConviteProjeto() {
        // usado pelo JPA
    }

    public ConviteProjeto(Projeto projeto, String email, PapelProjeto funcaoProposta, Usuario criadoPor, LocalDateTime expiraEm) {
        this.projeto = projeto;
        this.email = email;
        this.funcaoProposta = funcaoProposta;
        this.criadoPor = criadoPor;
        this.criadoEm = LocalDateTime.now();
        this.expiraEm = expiraEm;
        this.estado = EstadoConvite.PENDENTE;
    }

    /**
     * Atualiza o estado para EXPIRADO quando o prazo passou.
     * A verificação é preguiçosa (ao ler/aceitar o convite).
     */
    public void expirarSeNecessario() {
        if (this.estado == EstadoConvite.PENDENTE && this.expiraEm.isBefore(LocalDateTime.now())) {
            this.estado = EstadoConvite.EXPIRADO;
        }
    }

    public Long getId() {
        return id;
    }

    public Projeto getProjeto() {
        return projeto;
    }

    public String getEmail() {
        return email;
    }

    public PapelProjeto getFuncaoProposta() {
        return funcaoProposta;
    }

    public Usuario getCriadoPor() {
        return criadoPor;
    }

    public LocalDateTime getCriadoEm() {
        return criadoEm;
    }

    public LocalDateTime getExpiraEm() {
        return expiraEm;
    }

    public EstadoConvite getEstado() {
        return estado;
    }

    public void setEstado(EstadoConvite estado) {
        this.estado = estado;
    }
}
