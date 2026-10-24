package com.flowtask.controle;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flowtask.dto.ProjetoDtos;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.servico.ServicoMembros;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{projetoId}/members")
@Tag(name = "Integrantes")
public class ControladorMembros {

    private final ServicoMembros servicoMembros;

    public ControladorMembros(ServicoMembros servicoMembros) {
        this.servicoMembros = servicoMembros;
    }

    @Operation(summary = "Lista integrantes do projeto")
    @GetMapping
    public List<ProjetoDtos.RespostaMembro> listar(@PathVariable Long projetoId) {
        return servicoMembros.listar(projetoId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Altera a função de um integrante (OWNER). OWNER nela = transferir propriedade")
    @PatchMapping("/{membroId}/role")
    public ProjetoDtos.RespostaMembro alterarFuncao(@PathVariable Long projetoId, @PathVariable Long membroId,
                                                    @Valid @RequestBody ProjetoDtos.AlterarFuncaoMembro dados) {
        return servicoMembros.alterarFuncao(projetoId, membroId, dados.funcao(), FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Remove um integrante (OWNER); bloqueado com tarefas pendentes atribuídas")
    @DeleteMapping("/{membroId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long projetoId, @PathVariable Long membroId) {
        servicoMembros.remover(projetoId, membroId, FiltroJwtPapeis.usuarioCorrente());
    }
}
