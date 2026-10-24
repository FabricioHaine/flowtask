package com.flowtask.controle;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.dto.PaginaResposta;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.servico.ServicoAtividade;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Histórico de atividades (somente leitura). Gravação acontece nas operações.
 */
@RestController
@Tag(name = "Histórico de atividades")
public class ControladorAtividades {

    private final ServicoAtividade servicoAtividade;

    public ControladorAtividades(ServicoAtividade servicoAtividade) {
        this.servicoAtividade = servicoAtividade;
    }

    @Operation(summary = "Atividades do projeto (página)")
    @GetMapping("/api/projects/{projetoId}/activities")
    public PaginaResposta<ColaboracaoDtos.RespostaAtividade> listarPorProjeto(
            @PathVariable Long projetoId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return servicoAtividade
                .listarProjeto(projetoId, FiltroJwtPapeis.usuarioCorrente(),
                        Math.max(0, page), Math.clamp(size, 1, 100));
    }

    @Operation(summary = "Atividades relacionadas à tarefa (página)")
    @GetMapping("/api/tasks/{tarefaId}/activities")
    public PaginaResposta<ColaboracaoDtos.RespostaAtividade> listarPorTarefa(
            @PathVariable Long tarefaId,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "20") int size) {
        return servicoAtividade
                .listarTarefa(tarefaId, FiltroJwtPapeis.usuarioCorrente(),
                        Math.max(0, page), Math.clamp(size, 1, 100));
    }
}
