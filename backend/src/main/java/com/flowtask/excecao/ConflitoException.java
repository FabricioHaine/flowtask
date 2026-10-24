package com.flowtask.excecao;

/** Violações de unicidade, exclusões indevidas e conflitos de concorrência. */
public class ConflitoException extends ExcecaoApp {

    public ConflitoException(String mensagem) {
        super(409, mensagem);
    }
}
