package com.flowtask.excecao;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.NoSuchElementException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.validation.FieldError;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import com.flowtask.dto.ErroResposta;

/**
 * Tratamento centralizado de erros: respostas consistentes, códigos HTTP
 * corretos e mensagens seguras (sem expor detalhes internos).
 */
@RestControllerAdvice
public class TratadorExcecoes {

    private static final Logger LOG = LoggerFactory.getLogger(TratadorExcecoes.class);

    @ExceptionHandler(RecursoNaoEncontradoException.class)
    public ResponseEntity<ErroResposta> naoEncontrado(RecursoNaoEncontradoException e) {
        return ResponseEntity.status(404).body(ErroResposta.simples(404, e.getMessage()));
    }

    @ExceptionHandler(AcessoNegadoException.class)
    public ResponseEntity<ErroResposta> acessoNegado(AcessoNegadoException e) {
        return ResponseEntity.status(403).body(ErroResposta.simples(403, e.getMessage()));
    }

    @ExceptionHandler(RegraNegocioException.class)
    public ResponseEntity<ErroResposta> regraNegocio(RegraNegocioException e) {
        return ResponseEntity.status(400).body(ErroResposta.simples(400, e.getMessage()));
    }

    @ExceptionHandler(ConflitoException.class)
    public ResponseEntity<ErroResposta> conflito(ConflitoException e) {
        return ResponseEntity.status(409).body(ErroResposta.simples(409, e.getMessage()));
    }

    @ExceptionHandler(CredenciaisInvalidasException.class)
    public ResponseEntity<ErroResposta> credenciais(CredenciaisInvalidasException e) {
        return ResponseEntity.status(401).body(ErroResposta.simples(401, e.getMessage()));
    }

    @ExceptionHandler({OptimisticLockingFailureException.class, NoSuchElementException.class})
    public ResponseEntity<ErroResposta> concorrenciaOuElemento(Exception e) {
        if (e instanceof OptimisticLockingFailureException) {
            return ResponseEntity.status(409).body(ErroResposta.simples(409,
                    "Conflito de concorrência: o registro foi alterado por outra pessoa. Recarregue e tente novamente."));
        }
        return ResponseEntity.status(404).body(ErroResposta.simples(404, "Recurso não encontrado."));
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    public ResponseEntity<ErroResposta> integridade(DataIntegrityViolationException e) {
        LOG.warn("Violação de integridade: {}", e.getMessage());
        return ResponseEntity.status(409)
                .body(ErroResposta.simples(409, "Operação violou a integridade dos dados."));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErroResposta> validacaoCampos(MethodArgumentNotValidException e) {
        Map<String, String> erros = new LinkedHashMap<>();
        for (FieldError fe : e.getBindingResult().getFieldErrors()) {
            erros.putIfAbsent(fe.getField(), fe.getDefaultMessage());
        }
        return ResponseEntity.badRequest()
                .body(new ErroResposta(400, "Dados inválidos. Verifique os campos informados.", erros));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentTypeMismatchException.class})
    public ResponseEntity<ErroResposta> corpoInvalido(Exception e) {
        return ResponseEntity.badRequest()
                .body(ErroResposta.simples(400, "Corpo da requisição inválido."));
    }

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErroResposta> acessoNegadoSpring(AccessDeniedException e) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN)
                .body(ErroResposta.simples(403, "Você não possui permissão para esta operação."));
    }

    @ExceptionHandler(BadCredentialsException.class)
    public ResponseEntity<ErroResposta> credenciaisSpring(BadCredentialsException e) {
        return ResponseEntity.status(401)
                .body(ErroResposta.simples(401, "E-mail ou senha inválidos."));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    public ResponseEntity<ErroResposta> rotaInexistente(NoResourceFoundException e) {
        return ResponseEntity.status(404).body(ErroResposta.simples(404, "Recurso não encontrado."));
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    public ResponseEntity<ErroResposta> metodoNaoSuportado(HttpRequestMethodNotSupportedException e) {
        return ResponseEntity.status(405).body(ErroResposta.simples(405, "Método HTTP não suportado para este recurso."));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErroResposta> erroInesperado(Exception e) {
        LOG.error("Erro interno inesperado", e);
        return ResponseEntity.internalServerError()
                .body(ErroResposta.simples(500, "Erro interno inesperado. Tente novamente em instantes."));
    }
}
