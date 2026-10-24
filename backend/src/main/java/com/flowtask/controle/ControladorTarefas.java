package com.flowtask.controle;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flowtask.dominio.PrioridadeTarefa;
import com.flowtask.dto.QuadroDtos;
import com.flowtask.dto.TarefaDtos;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.servico.ServicoTarefa;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Tarefas")
public class ControladorTarefas {

    private final ServicoTarefa servicoTarefa;

    public ControladorTarefas(ServicoTarefa servicoTarefa) {
        this.servicoTarefa = servicoTarefa;
    }

    // --------------------------------------------------------- tarefas do projeto

    @Operation(summary = "Cria uma tarefa em uma coluna do projeto (MANAGER/OWNER)")
    @PostMapping("/api/projects/{projetoId}/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public TarefaDtos.RespostaTarefa criar(@PathVariable Long projetoId,
                                           @Valid @RequestBody TarefaDtos.CriarTarefa dados) {
        return servicoTarefa.criar(projetoId, dados, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Lista tarefas do projeto com filtros (busca, responsável, prioridade, coluna, arquivadas)")
    @GetMapping("/api/projects/{projetoId}/tasks")
    public List<TarefaDtos.RespostaTarefa> listar(
            @PathVariable Long projetoId,
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) Long responsavelId,
            @RequestParam(required = false) PrioridadeTarefa prioridade,
            @RequestParam(required = false) Long colunaId,
            @RequestParam(required = false, defaultValue = "false") boolean arquivadas) {
        return servicoTarefa.listar(projetoId, busca, responsavelId, prioridade, colunaId, arquivadas,
                FiltroJwtPapeis.usuarioCorrente());
    }

    // --------------------------------------------------------- tarefa única

    @Operation(summary = "Tarefas atribuídas ao usuário autenticado em todos os projetos")
    @GetMapping("/api/tasks/me")
    public List<TarefaDtos.RespostaTarefa> minhasTarefas() {
        return servicoTarefa.minhasTarefas(FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Detalha uma tarefa; exige acesso ao projeto")
    @GetMapping("/api/tasks/{tarefaId}")
    public TarefaDtos.RespostaTarefa obter(@PathVariable Long tarefaId) {
        return servicoTarefa.obter(tarefaId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Edita título e descrição (MANAGER/OWNER ou o responsável)")
    @PutMapping("/api/tasks/{tarefaId}")
    public TarefaDtos.RespostaTarefa editar(@PathVariable Long tarefaId,
                                            @Valid @RequestBody TarefaDtos.EditarTarefaTexto dados) {
        return servicoTarefa.editarTextos(tarefaId, dados, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Movimenta a tarefa para uma coluna/posição; conclui ou reabre conforme a coluna")
    @PatchMapping("/api/tasks/{tarefaId}/move")
    public TarefaDtos.RespostaTarefa mover(@PathVariable Long tarefaId,
                                           @Valid @RequestBody QuadroDtos.MoverTarefa dados) {
        return servicoTarefa.mover(tarefaId, dados, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Define ou remove o responsável (MANAGER/OWNER); precisa ser integrante")
    @PatchMapping("/api/tasks/{tarefaId}/assignee")
    public TarefaDtos.RespostaTarefa atribuir(@PathVariable Long tarefaId,
                                              @RequestBody(required = false) TarefaDtos.AtribuirResponsavelTarefa dados) {
        Long responsavelId = dados == null ? null : dados.responsavelId();
        return servicoTarefa.atribuir(tarefaId, responsavelId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Altera a prioridade (MANAGER/OWNER)")
    @PatchMapping("/api/tasks/{tarefaId}/priority")
    public TarefaDtos.RespostaTarefa prioridade(@PathVariable Long tarefaId,
                                                @Valid @RequestBody TarefaDtos.MudarPrioridadeTarefa dados) {
        return servicoTarefa.mudarPrioridade(tarefaId, dados.prioridade(), FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Define, altera ou limpa o prazo (MANAGER/OWNER)")
    @PatchMapping("/api/tasks/{tarefaId}/due-date")
    public TarefaDtos.RespostaTarefa prazo(@PathVariable Long tarefaId,
                                           @RequestBody(required = false) TarefaDtos.MudarPrazoTarefa dados) {
        java.time.LocalDate prazo = dados == null ? null : dados.prazo();
        return servicoTarefa.mudarPrazo(tarefaId, prazo, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Substitui as etiquetas da tarefa (MANAGER/OWNER ou o responsável)")
    @PatchMapping("/api/tasks/{tarefaId}/labels")
    public TarefaDtos.RespostaTarefa etiquetas(@PathVariable Long tarefaId,
                                               @Valid @RequestBody TarefaDtos.AtualizarEtiquetasTarefa dados) {
        return servicoTarefa.atualizarEtiquetas(tarefaId, dados.etiquetasIds(), FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Arquiva ou restaura a tarefa (MANAGER/OWNER ou o responsável)")
    @PatchMapping("/api/tasks/{tarefaId}/archive")
    public TarefaDtos.RespostaTarefa arquivar(@PathVariable Long tarefaId,
                                              @Valid @RequestBody TarefaDtos.ArquivarTarefa dados) {
        return servicoTarefa.arquivar(tarefaId, dados.arquivada(), FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Exclui a tarefa (OWNER); bloqueado se houver comentários")
    @DeleteMapping("/api/tasks/{tarefaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long tarefaId) {
        servicoTarefa.excluir(tarefaId, FiltroJwtPapeis.usuarioCorrente());
    }
}
