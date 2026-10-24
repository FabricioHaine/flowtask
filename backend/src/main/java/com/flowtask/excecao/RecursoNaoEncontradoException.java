package com.flowtask.excecao;

public class RecursoNaoEncontradoException extends ExcecaoApp {

    public RecursoNaoEncontradoException(String mensagem) {
        super(404, mensagem);
    }
}
