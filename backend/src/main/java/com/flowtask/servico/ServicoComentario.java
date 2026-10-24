package com.flowtask.servico;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowtask.dominio.Comentario;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.Tarefa;
import com.flowtask.dominio.TipoAtividade;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.excecao.AcessoNegadoException;
import com.flowtask.excecao.RecursoNaoEncontradoException;
import com.flowtask.repositorio.ComentarioRepositorio;

/**
 * Comentários em tarefas. Qualquer integrante pode comentar; edição fica a
 * cargo do autor; remoção é do autor ou do OWNER do projeto. Todos os
 * acessos passam pela trilha de atividade da tarefa.
 */
@Service
public class ServicoComentario {

    private final ComentarioRepositorio repositorio;
    private final VerificadorAcesso verificador;
    private final ServicoAtividade servicoAtividade;
    private final ServicoNotificacao servicoNotificacao;

    public ServicoComentario(ComentarioRepositorio repositorio,
                             VerificadorAcesso verificador,
                             ServicoAtividade servicoAtividade,
                             ServicoNotificacao servicoNotificacao) {
        this.repositorio = repositorio;
        this.verificador = verificador;
        this.servicoAtividade = servicoAtividade;
        this.servicoNotificacao = servicoNotificacao;
    }

    @Transactional(readOnly = true)
    public List<ColaboracaoDtos.RespostaComentario> listar(Long tarefaId, Usuario ator) {
        Tarefa tarefa = verificador.exigirTarefaAcessivel(tarefaId, ator);
        List<ColaboracaoDtos.RespostaComentario> resposta = new ArrayList<>();
        for (Comentario comentario : repositorio.listarPorTarefa(tarefa.getId())) {
            resposta.add(resposta(comentario));
        }
        return resposta;
    }

    @Transactional
    public ColaboracaoDtos.RespostaComentario criar(Long tarefaId, String conteudo, Usuario ator) {
        Tarefa tarefa = verificador.exigirTarefaAcessivel(tarefaId, ator);
        Comentario comentario = repositorio.save(new Comentario(tarefa, ator, conteudo.trim()));

        servicoAtividade.registrar(tarefa.getProjeto(), ator, TipoAtividade.COMENTARIO_CRIADO,
                ServicoAtividade.ENT_TAREFA, tarefaId,
                "Comentou na tarefa '%s'.".formatted(tarefa.getTitulo()));
        notificarInteressados(tarefa, ator);
        return resposta(comentario);
    }

    @Transactional
    public ColaboracaoDtos.RespostaComentario editar(Long comentarioId, String conteudo, Usuario ator) {
        Comentario comentario = carregar(comentarioId);
        verificador.exigirAcesso(comentario.getTarefa().getProjeto().getId(), ator);
        if (!comentario.getAutor().getId().equals(ator.getId())) {
            throw new AcessoNegadoException("Você só pode editar o próprio comentário.");
        }
        comentario.editar(conteudo.trim());
        servicoAtividade.registrar(comentario.getTarefa().getProjeto(), ator, TipoAtividade.COMENTARIO_EDITADO,
                ServicoAtividade.ENT_COMENTARIO, comentarioId,
                "Editou um comentário na tarefa '%s'.".formatted(comentario.getTarefa().getTitulo()));
        return resposta(comentario);
    }

    @Transactional
    public void excluir(Long comentarioId, Usuario ator) {
        Comentario comentario = carregar(comentarioId);
        Tarefa tarefa = comentario.getTarefa();
        verificador.exigirAcesso(tarefa.getProjeto().getId(), ator);
        PapelProjeto papel = verificador.papelNoProjeto(tarefa.getProjeto().getId(), ator.getId());
        boolean dono = papel.eh(PapelProjeto.OWNER);
        if (!comentario.getAutor().getId().equals(ator.getId()) && !dono) {
            throw new AcessoNegadoException("Você só pode excluir o próprio comentário.");
        }
        repositorio.delete(comentario);
        servicoAtividade.registrar(tarefa.getProjeto(), ator, TipoAtividade.COMENTARIO_REMOVIDO,
                ServicoAtividade.ENT_COMENTARIO, comentarioId,
                "Removeu um comentário da tarefa '%s'.".formatted(tarefa.getTitulo()));
    }

    private void notificarInteressados(Tarefa tarefa, Usuario ator) {
        // Notifica responsável e criador da tarefa, sem duplicar destinatário
        // e sem notificar o próprio ator.
        List<com.flowtask.dominio.Usuario> interessados = new ArrayList<>();
        if (tarefa.getResponsavel() != null) {
            interessados.add(tarefa.getResponsavel());
        }
        if (tarefa.getCriador() != null && interessados.stream().noneMatch(u -> u.getId().equals(tarefa.getCriador().getId()))) {
            interessados.add(tarefa.getCriador());
        }
        for (com.flowtask.dominio.Usuario destinatario : interessados) {
            servicoNotificacao.criarSeDiferenteDoAtor(ator, destinatario,
                    com.flowtask.dominio.TipoNotificacao.COMENTARIO_NOVO,
                    "%s comentou na tarefa '%s'.".formatted(ator.getNome(), tarefa.getTitulo()),
                    tarefa.getProjeto(), tarefa);
        }
    }

    private Comentario carregar(Long comentarioId) {
        return repositorio.findById(comentarioId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Comentário não encontrado."));
    }

    private ColaboracaoDtos.RespostaComentario resposta(Comentario comentario) {
        return new ColaboracaoDtos.RespostaComentario(
                comentario.getId(),
                Mapeadores.resumo(comentario.getAutor()),
                comentario.getConteudo(),
                comentario.getCriadoEm(),
                comentario.getEditadoEm());
    }
}
