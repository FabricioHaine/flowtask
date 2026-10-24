package com.flowtask.controle;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flowtask.dto.DashboardDtos;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.servico.ServicoDashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@Tag(name = "Dashboard")
public class ControladorDashboard {

    private final ServicoDashboard servicoDashboard;

    public ControladorDashboard(ServicoDashboard servicoDashboard) {
        this.servicoDashboard = servicoDashboard;
    }

    @Operation(summary = "Indicadores consolidados dos projetos do usuário")
    @GetMapping("/api/dashboard/summary")
    public DashboardDtos.ResumoUsuario resumo(
            @RequestParam(required = false, defaultValue = "30") int periodo) {
        return servicoDashboard.resumoUsuario(FiltroJwtPapeis.usuarioCorrente(),
                Math.clamp(periodo, 1, 365));
    }

    @Operation(summary = "Indicadores de um projeto específico")
    @GetMapping("/api/projects/{projetoId}/dashboard")
    public DashboardDtos.ResumoProjeto resumoProjeto(
            @PathVariable Long projetoId,
            @RequestParam(required = false, defaultValue = "30") int periodo) {
        return servicoDashboard.resumoProjeto(projetoId, FiltroJwtPapeis.usuarioCorrente(),
                Math.clamp(periodo, 1, 365));
    }
}
