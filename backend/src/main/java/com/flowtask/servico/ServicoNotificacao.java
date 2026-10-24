package com.flowtask.servico;

import java.util.ArrayList;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowtask.dominio.Notificacao;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.Tarefa;
import com.flowtask.dominio.TipoNotificacao;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.dto.PaginaResposta;
import com.flowtask.excecao.RecursoNaoEncontradoException;
import com.flowtask.repositorio.NotificacaoRepositorio;

/**
 * Notificações internas persistidas no banco. O serviço de negócio cria as
 * notificações (sempre excluindo o próprio ator); o usuário lista, marca como
 * lida e navega para o recurso relacionado. Sem WebSocket no MVP: o front-end
 * consulta periodicamente.
 */
@Service
public class ServicoNotificacao {

    private final NotificacaoRepositorio repositorio;

    public ServicoNotificacao(NotificacaoRepositorio repositorio) {
        this.repositorio = repositorio;
    }

    // ---------------------------------------------------------------- criação (usada pelos serviços)

    public void criar(Usuario destinatario, TipoNotificacao tipo, String mensagem, Projeto projeto, Tarefa tarefa) {
        if (destinatario == null) {
            return;
        }
        repositorio.save(new Notificacao(destinatario, tipo, mensagem, projeto, tarefa));
    }

    public void criarSeDiferenteDoAtor(Usuario ator, Usuario destinatario, TipoNotificacao tipo,
                                       String mensagem, Projeto projeto, Tarefa tarefa) {
        if (destinatario == null || ator.getId().equals(destinatario.getId())) {
            return;
        }
        criar(destinatario, tipo, mensagem, projeto, tarefa);
    }

    // ---------------------------------------------------------------- operações do usuário

    @Transactional(readOnly = true)
    public PaginaResposta<ColaboracaoDtos.RespostaNotificacao> listar(Long usuarioId, Boolean apenasNaoLidas,
                                                                      int pagina, int tamanho) {
        // O filtro por lida acontece após a consulta do usuário (lista pessoal, volume baixo).
        List<Notificacao> brutas = repositorio
                .listarDoUsuario(usuarioId, PageRequest.of(0, 2000));
        List<Notificacao> filtradas = new ArrayList<>();
        for (Notificacao notificacao : brutas) {
            if (apenasNaoLidas != null && apenasNaoLidas && notificacao.isLida()) {
                continue;
            }
            filtradas.add(notificacao);
        }
        long total = filtradas.size();
        int de = Math.min(pagina * tamanho, filtradas.size());
        int ate = Math.min(de + tamanho, filtradas.size());
        List<ColaboracaoDtos.RespostaNotificacao> itens = new ArrayList<>();
        for (Notificacao notificacao : filtradas.subList(de, ate)) {
            itens.add(resposta(notificacao));
        }
        return PaginaResposta.de(itens, pagina, tamanho, total);
    }

    @Transactional
    public ColaboracaoDtos.RespostaNotificacao marcarComoLida(Long id, Long usuarioId) {
        Notificacao notificacao = repositorio.buscarDoUsuario(id, usuarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Notificação não encontrada."));
        if (!notificacao.isLida()) {
            notificacao.marcarComoLida();
            repositorio.save(notificacao);
        }
        return resposta(notificacao);
    }

    @Transactional
    public int marcarTodasComoLidas(Long usuarioId) {
        return repositorio.marcarTodasComoLidas(usuarioId);
    }

    @Transactional(readOnly = true)
    public long contagemNaoLidas(Long usuarioId) {
        return repositorio.countByDestinatarioIdAndLidaFalse(usuarioId);
    }

    // ---------------------------------------------------------------- helpers

    private ColaboracaoDtos.RespostaNotificacao resposta(Notificacao notificacao) {
        Long projetoId = notificacao.getProjeto() == null ? null : notificacao.getProjeto().getId();
        Long tarefaId = notificacao.getTarefa() == null ? null : notificacao.getTarefa().getId();
        return new ColaboracaoDtos.RespostaNotificacao(
                notificacao.getId(),
                notificacao.getTipo(),
                notificacao.getMensagem(),
                notificacao.isLida(),
                projetoId,
                tarefaId,
                notificacao.getCriadoEm());
    }
}
