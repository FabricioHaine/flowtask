package com.flowtask.controle;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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

import com.flowtask.dominio.EstadoProjeto;
import com.flowtask.dto.ProjetoDtos;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.servico.ServicoProjeto;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/projects")
@Tag(name = "Projetos")
public class ControladorProjetos {

    private final ServicoProjeto servicoProjeto;

    public ControladorProjetos(ServicoProjeto servicoProjeto) {
        this.servicoProjeto = servicoProjeto;
    }

    @Operation(summary = "Registra um novo projeto; o criador se torna OWNER e as colunas padrão são criadas")
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ProjetoDtos.RespostaProjeto criar(@Valid @RequestBody ProjetoDtos.CriarProjeto dados) {
        return servicoProjeto.criar(dados, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Lista projetos que o usuário integra, com busca, estado e ordenação")
    @GetMapping
    public List<ProjetoDtos.RespostaProjeto> listar(
            @RequestParam(required = false) String busca,
            @RequestParam(required = false) EstadoProjeto estado,
            @RequestParam(required = false) ServicoProjeto.OrdenacaoProjeto ordenacao) {
        return servicoProjeto.listar(FiltroJwtPapeis.usuarioCorrente(), busca, estado, ordenacao);
    }

    @Operation(summary = "Detalha um projeto em que o usuário participa")
    @GetMapping("/{id}")
    public ProjetoDtos.RespostaProjeto detalhe(@PathVariable Long id) {
        return servicoProjeto.obterDetalhe(id, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Atualiza nome, descrição e datas (OWNER)")
    @PutMapping("/{id}")
    public ProjetoDtos.RespostaProjeto atualizar(@PathVariable Long id,
                                                 @Valid @RequestBody ProjetoDtos.AtualizarProjeto dados) {
        return servicoProjeto.atualizar(id, dados, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Altera o estado do projeto (OWNER)")
    @PatchMapping("/{id}/status")
    public ResponseEntity<ProjetoDtos.RespostaProjeto> mudarEstado(
            @PathVariable Long id,
            @Valid @RequestBody ProjetoDtos.MudarEstadoProjeto dados) {
        return ResponseEntity.ok(servicoProjeto.mudarEstado(id, dados, FiltroJwtPapeis.usuarioCorrente()));
    }
}
