package com.flowtask.servico;

import org.springframework.stereotype.Service;

import com.flowtask.dominio.MembroProjeto;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.Usuario;
import com.flowtask.excecao.AcessoNegadoException;
import com.flowtask.excecao.RecursoNaoEncontradoException;
import com.flowtask.repositorio.MembroProjetoRepositorio;
import com.flowtask.repositorio.ProjetoRepositorio;
import com.flowtask.repositorio.TarefaRepositorio;

/**
 * Verificação centralizada de acesso: NÃO confie nos guards do front-end.
 *
 * Regra de isolamento: se o usuário não é integrante do projeto, a resposta é
 * 404 (o projeto "não existe" para ele) — evita enumeração de ids por adivinhação.
 * Se é integrante, mas o papel é insuficiente, a resposta é 403.
 */
@Service
public class VerificadorAcesso {

    private final ProjetoRepositorio projetoRepositorio;
    private final MembroProjetoRepositorio membroRepositorio;
    private final TarefaRepositorio tarefaRepositorio;

    public VerificadorAcesso(ProjetoRepositorio projetoRepositorio,
                             MembroProjetoRepositorio membroRepositorio,
                             TarefaRepositorio tarefaRepositorio) {
        this.projetoRepositorio = projetoRepositorio;
        this.membroRepositorio = membroRepositorio;
        this.tarefaRepositorio = tarefaRepositorio;
    }

    /** Papel do usuário no projeto; 404 se o projeto não existir para ele. */
    public PapelProjeto papelNoProjeto(Long projetoId, Long usuarioId) {
        return membroRepositorio.findByProjetoIdAndUsuarioId(projetoId, usuarioId)
                .map(MembroProjeto::getFuncao)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Projeto não encontrado."));
    }

    /** Alias de {@link #exigirPapelAte}, usado pelos serviços de domínio. */
    public PapelProjeto exigirPapel(Long projetoId, Usuario usuario, PapelProjeto minimo) {
        return exigirPapelAte(projetoId, usuario, minimo);
    }

    /** Exige papel mínimo (OWNER &gt; MANAGER &gt; MEMBER); 403 quando insuficiente. */
    public PapelProjeto exigirPapelAte(Long projetoId, Usuario usuario, PapelProjeto minimo) {
        PapelProjeto papel = papelNoProjeto(projetoId, usuario.getId());
        if (!papel.temAoMenos(minimo)) {
            throw new AcessoNegadoException("Você não possui permissão para esta operação neste projeto.");
        }
        return papel;
    }

    /** Exige que o usuário possua um dos papéis exatos informados. */
    public PapelProjeto exigirUmDosPapeis(Long projetoId, Usuario usuario, PapelProjeto... papeis) {
        PapelProjeto papel = papelNoProjeto(projetoId, usuario.getId());
        for (PapelProjeto aceito : papeis) {
            if (papel.eh(aceito)) {
                return papel;
            }
        }
        throw new AcessoNegadoException("Você não possui permissão para esta operação neste projeto.");
    }

    /** Verifica apenas o acesso (inclusive MEMBER), sem papel mínimo. */
    public PapelProjeto exigirAcesso(Long projetoId, Usuario usuario) {
        return exigirPapelAte(projetoId, usuario, PapelProjeto.MEMBER);
    }

    /** Tarefa acessível: exige acesso ao projeto vinculado. */
    public com.flowtask.dominio.Tarefa exigirTarefaAcessivel(Long tarefaId, Usuario usuario) {
        com.flowtask.dominio.Tarefa tarefa = tarefaRepositorio.buscarCompleta(tarefaId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Tarefa não encontrada."));
        exigirAcesso(tarefa.getProjeto().getId(), usuario);
        return tarefa;
    }

    /** Carrega o vinculando do integrante (attachment) ou lança 404. */
    public MembroProjeto membroVinculado(Long membroId, Long projetoId) {
        MembroProjeto membro = membroRepositorio.findById(membroId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Integrante não encontrado."));
        if (!membro.getProjeto().getId().equals(projetoId)) {
            throw new RecursoNaoEncontradoException("Integrante não encontrado neste projeto.");
        }
        return membro;
    }

    /** Carrega projeto existente ou 404. */
    public Projeto projetoPorId(Long projetoId) {
        return projetoRepositorio.findById(projetoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Projeto não encontrado."));
    }
}
