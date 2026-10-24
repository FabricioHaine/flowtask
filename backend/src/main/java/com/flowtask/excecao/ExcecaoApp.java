package com.flowtask.excecao;

/** Exceção base com status HTTP associado, usada para erros de regra de negócio. */
public class ExcecaoApp extends RuntimeException {

    private final transient int status;

    protected ExcecaoApp(int status, String mensagem) {
        super(mensagem);
        this.status = status;
    }

    public int getStatus() {
        return status;
    }
}
