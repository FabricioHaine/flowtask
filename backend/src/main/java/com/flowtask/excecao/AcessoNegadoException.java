package com.flowtask.excecao;

public class AcessoNegadoException extends ExcecaoApp {

    public AcessoNegadoException(String mensagem) {
        super(403, mensagem);
    }
}
