package com.flowtask.controle;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.servico.ServicoEtiqueta;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects/{projetoId}/labels")
@Tag(name = "Etiquetas")
public class ControladorEtiquetas {

    private final ServicoEtiqueta servicoEtiqueta;

    public ControladorEtiquetas(ServicoEtiqueta servicoEtiqueta) {
        this.servicoEtiqueta = servicoEtiqueta;
    }

    @Operation(summary = "Etiquetas do projeto")
    @GetMapping
    public List<ColaboracaoDtos.RespostaEtiqueta> listar(@PathVariable Long projetoId) {
        return servicoEtiqueta.listar(projetoId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Cria etiqueta (MANAGER/OWNER)")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ColaboracaoDtos.RespostaEtiqueta criar(@PathVariable Long projetoId,
                                                  @Valid @RequestBody ColaboracaoDtos.CriarEtiqueta dados) {
        return servicoEtiqueta.criar(projetoId, dados, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Atualiza etiqueta (MANAGER/OWNER)")
    @PutMapping("/{etiquetaId}")
    public ColaboracaoDtos.RespostaEtiqueta editar(@PathVariable Long projetoId, @PathVariable Long etiquetaId,
                                                   @Valid @RequestBody ColaboracaoDtos.CriarEtiqueta dados) {
        return servicoEtiqueta.editar(projetoId, etiquetaId, dados, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Exclui etiqueta (MANAGER/OWNER); vínculos com tarefas são removidos")
    @DeleteMapping("/{etiquetaId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void excluir(@PathVariable Long projetoId, @PathVariable Long etiquetaId) {
        servicoEtiqueta.excluir(projetoId, etiquetaId, FiltroJwtPapeis.usuarioCorrente());
    }
}
