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
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flowtask.dto.QuadroDtos;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.servico.ServicoQuadro;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{projetoId}")
@Tag(name = "Quadro Kanban")
public class ControladorQuadro {

    private final ServicoQuadro servicoQuadro;

    public ControladorQuadro(ServicoQuadro servicoQuadro) {
        this.servicoQuadro = servicoQuadro;
    }

    @Operation(summary = "Quadro com colunas ativas e cartões ordenados")
    @GetMapping("/board")
    public QuadroDtos.RespostaQuadro obterQuadro(@PathVariable Long projetoId) {
        return servicoQuadro.obterQuadro(projetoId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Colunas do projeto, incluindo arquivadas (OWNER)")
    @GetMapping("/columns")
    public List<QuadroDtos.RespostaColunaQuadro> listarColunas(@PathVariable Long projetoId) {
        return servicoQuadro.listarColunas(projetoId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Cria uma nova coluna no fim do quadro (OWNER)")
    @PostMapping("/columns")
    @ResponseStatus(HttpStatus.CREATED)
    public QuadroDtos.RespostaColunaQuadro criarColuna(@PathVariable Long projetoId,
                                                                  @Valid @RequestBody QuadroDtos.CriarColuna dados) {
        return servicoQuadro.criarColuna(projetoId, dados.nome(), FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Renomeia/atualiza uma coluna e o sinalizador de conclusão (OWNER)")
    @PutMapping("/columns/{colunaId}")
    public QuadroDtos.RespostaColunaQuadro atualizarColuna(@PathVariable Long projetoId,
                                                                      @PathVariable Long colunaId,
                                                                      @Valid @RequestBody QuadroDtos.AtualizarColuna dados) {
        return servicoQuadro.atualizarColuna(projetoId, colunaId, dados, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Reordena TODAS as colunas do quadro (OWNER)")
    @PatchMapping("/columns/reorder")
    public java.util.List<QuadroDtos.RespostaColunaQuadro> reordenar(
            @PathVariable Long projetoId,
            @Valid @RequestBody QuadroDtos.ReordenarColunas dados) {
        servicoQuadro.reordenar(projetoId, dados.colunasIds(), FiltroJwtPapeis.usuarioCorrente());
        return servicoQuadro.listarColunas(projetoId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Arquiva ou restaura uma coluna (OWNER)")
    @PatchMapping("/columns/{colunaId}/archive")
    public QuadroDtos.RespostaColunaQuadro arquivar(@PathVariable Long projetoId,
                                                               @PathVariable Long colunaId,
                                                               @Valid @RequestBody QuadroDtos.ArquivarColuna dados) {
        return servicoQuadro.arquivar(projetoId, colunaId, dados.ativa(), FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Exclui a coluna (OWNER); exige coluna sem tarefas")
    @DeleteMapping("/columns/{colunaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void remover(@PathVariable Long projetoId, @PathVariable Long colunaId) {
        servicoQuadro.remover(projetoId, colunaId, FiltroJwtPapeis.usuarioCorrente());
    }
}
