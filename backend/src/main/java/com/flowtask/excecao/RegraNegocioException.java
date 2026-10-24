package com.flowtask.excecao;

public class RegraNegocioException extends ExcecaoApp {

    public RegraNegocioException(String mensagem) {
        super(400, mensagem);
    }
}
