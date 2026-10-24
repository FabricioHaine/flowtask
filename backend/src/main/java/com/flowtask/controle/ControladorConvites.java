package com.flowtask.controle;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.flowtask.dto.ConviteDtos;
import com.flowtask.seguranca.FiltroJwtPapeis;
import com.flowtask.servico.ServicoConvite;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@Tag(name = "Convites")
public class ControladorConvites {

    private final ServicoConvite servicoConvite;

    public ControladorConvites(ServicoConvite servicoConvite) {
        this.servicoConvite = servicoConvite;
    }

    @Operation(summary = "Cria um convite de projeto por e-mail (MANAGER/OWNER)")
    @PostMapping("/api/projects/{projetoId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    public ConviteDtos.RespostaConvite criar(@PathVariable Long projetoId,
                                             @Valid @RequestBody ConviteDtos.CriarConvite dados) {
        return servicoConvite.criar(projetoId, dados, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Lista convites do projeto e seus estados (MANAGER/OWNER)")
    @GetMapping("/api/projects/{projetoId}/invitations")
    public List<ConviteDtos.RespostaConvite> listarDoProjeto(@PathVariable Long projetoId) {
        return servicoConvite.listarPorProjeto(projetoId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Cancela um convite pendente do projeto (OWNER; MANAGER cancela os seus)")
    @PatchMapping("/api/projects/{projetoId}/invitations/{conviteId}/cancel")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cancelar(@PathVariable Long projetoId, @PathVariable Long conviteId) {
        servicoConvite.cancelar(projetoId, conviteId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Convites endereçados ao e-mail do usuário autenticado")
    @GetMapping("/api/invitations/me")
    public List<ConviteDtos.RespostaConvite> listarMeus() {
        return servicoConvite.listarMeus(FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Aceita um convite (requer conta com o e-mail convidado)")
    @PatchMapping("/api/invitations/{conviteId}/accept")
    public ConviteDtos.RespostaConvite aceitar(@PathVariable Long conviteId) {
        return servicoConvite.aceitar(conviteId, FiltroJwtPapeis.usuarioCorrente());
    }

    @Operation(summary = "Recusa um convite")
    @PatchMapping("/api/invitations/{conviteId}/decline")
    public ConviteDtos.RespostaConvite recusar(@PathVariable Long conviteId) {
        return servicoConvite.recusar(conviteId, FiltroJwtPapeis.usuarioCorrente());
    }
}
