package com.flowtask.dominio;

/**
 * Funções de um usuário dentro de um projeto.
 *
 * OWNER  - proprietário do projeto, controla configurações e integrantes;
 * MANAGER - integra e gerencia tarefas, prioridades e convites;
 * MEMBER - integra, movimenta tarefas, comenta e atualiza seu próprio progresso.
 */
public enum PapelProjeto {
    OWNER,
    MANAGER,
    MEMBER;

    /** Nível hierárquico: OWNER (3) &gt; MANAGER (2) &gt; MEMBER (1). */
    public int nivel() {
        return switch (this) {
            case OWNER -> 3;
            case MANAGER -> 2;
            case MEMBER -> 1;
        };
    }

    /** Verifica se este papel possui NO MÍNIMO o nível do papel informado. */
    public boolean temAoMenos(PapelProjeto minimo) {
        return this.nivel() >= minimo.nivel();
    }

    /** Verifica se este papel é exatamente o papel informado. */
    public boolean eh(PapelProjeto papel) {
        return this == papel;
    }
}
