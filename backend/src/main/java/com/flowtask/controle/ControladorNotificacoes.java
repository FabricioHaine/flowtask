package com.flowtask.controle;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.dto.PaginaResposta;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.servico.ServicoNotificacao;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/notifications")
@Tag(name = "Notificações")
public class ControladorNotificacoes {

    private final ServicoNotificacao servicoNotificacao;

    public ControladorNotificacoes(ServicoNotificacao servicoNotificacao) {
        this.servicoNotificacao = servicoNotificacao;
    }

    @Operation(summary = "Lista as notificações do usuário autenticado")
    @GetMapping
    public PaginaResposta<ColaboracaoDtos.RespostaNotificacao> listar(
            @RequestParam(required = false) Boolean naoLidas,
            @RequestParam(required = false, defaultValue = "0") int page,
            @RequestParam(required = false, defaultValue = "30") int size) {
        return servicoNotificacao.listar(FiltroJwtPapeis.usuarioCorrente().getId(),
                naoLidas, Math.max(0, page), Math.clamp(size, 1, 100));
    }

    @Operation(summary = "Quantidade de notificações não lidas (usado pelo sino do cabeçalho)")
    @GetMapping("/count")
    public ColaboracaoDtos.RespostaContagem naoLidas() {
        return new ColaboracaoDtos.RespostaContagem(
                servicoNotificacao.contagemNaoLidas(FiltroJwtPapeis.usuarioCorrente().getId()));
    }

    @Operation(summary = "Marca uma notificação como lida")
    @PatchMapping("/{id}/read")
    public ColaboracaoDtos.RespostaNotificacao marcarLida(@PathVariable Long id) {
        return servicoNotificacao.marcarComoLida(id, FiltroJwtPapeis.usuarioCorrente().getId());
    }

    @Operation(summary = "Marca todas como lidas")
    @PatchMapping("/read-all")
    public ResponseEntity<java.util.Map<String, Long>> marcarTodas() {
        int atualizadas = servicoNotificacao.marcarTodasComoLidas(FiltroJwtPapeis.usuarioCorrente().getId());
        return ResponseEntity.ok(java.util.Map.of("atualizadas", (long) atualizadas));
    }
}
