package com.flowtask.controle;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.servico.ServicoComentario;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Comentários")
public class ControladorComentarios {

    private final ServicoComentario servicoComentario;

    public ControladorComentarios(ServicoComentario servicoComentario) {
        this.servicoComentario = servicoComentario;
    }

    @Operation(summary = "Comentários da tarefa em ordem cronológica")
    @GetMapping("/api/tasks/{tarefaId}/comments")
    public List<ColaboracaoDtos.RespostaComentario> listar(@PathVariable Long tarefaId) {
        return servicoComentario.listar(tarefaId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Publica um comentário na tarefa (integrantes do projeto)")
    @PostMapping("/api/tasks/{tarefaId}/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ColaboracaoDtos.RespostaComentario criar(@PathVariable Long tarefaId,
                                                    @Valid @RequestBody ColaboracaoDtos.CriarComentario dados) {
        return servicoComentario.criar(tarefaId, dados.conteudo(), FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Edita o próprio comentário")
    @PutMapping("/api/comments/{comentarioId}")
    public ColaboracaoDtos.RespostaComentario editar(@PathVariable Long comentarioId,
                                                     @Valid @RequestBody ColaboracaoDtos.CriarComentario dados) {
        return servicoComentario.editar(comentarioId, dados.conteudo(), FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Exclui o próprio comentário (OWNER pode excluir qualquer)")
    @DeleteMapping("/api/comments/{comentarioId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long comentarioId) {
        servicoComentario.excluir(comentarioId, FiltroJwtPapeis.usuarioCorrente());
    }
}
