package com.flowtask.excecao;

/** Login com credenciais inválidas. */
public class CredenciaisInvalidasException extends ExcecaoApp {

    public CredenciaisInvalidasException(String mensagem) {
        super(401, mensagem);
    }
}
