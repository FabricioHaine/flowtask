package com.flowtask.servico;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowtask.dominio.MembroProjeto;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.TipoAtividade;
import com.flowtask.dominio.TipoNotificacao;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.ProjetoDtos;
import com.flowtask.dto.UsuarioResumo;
import com.flowtask.excecao.ConflitoException;
import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.MembroProjetoRepositorio;
import com.flowtask.repositorio.ProjetoRepositorio;
import com.flowtask.repositorio.TarefaRepositorio;

/**
 * Gestão de integrantes de acordo com as permissões:
 * - Somente OWNER altera funções e remove integrantes;
 * - OWNER nunca é removido sem transferência explícita de propriedade;
 * - Integrantes com tarefas atribuídas só saem após reatribuição.
 */
@Service
public class ServicoMembros {

    private final VerificadorAcesso verificador;
    private final MembroProjetoRepositorio membroRepositorio;
    private final TarefaRepositorio tarefaRepositorio;
    private final ServicoAtividade servicoAtividade;
    private final ServicoNotificacao servicoNotificacao;

    public ServicoMembros(VerificadorAcesso verificador,
                          MembroProjetoRepositorio membroRepositorio,
                          TarefaRepositorio tarefaRepositorio,
                          ServicoAtividade servicoAtividade,
                          ServicoNotificacao servicoNotificacao) {
        this.verificador = verificador;
        this.membroRepositorio = membroRepositorio;
        this.tarefaRepositorio = tarefaRepositorio;
        this.servicoAtividade = servicoAtividade;
        this.servicoNotificacao = servicoNotificacao;
    }

    @Transactional(readOnly = true)
    public List<ProjetoDtos.RespostaMembro> listar(Long projetoId, Usuario ator) {
        verificador.exigirAcesso(projetoId, ator);
        List<ProjetoDtos.RespostaMembro> resposta = new ArrayList<>();
        for (MembroProjeto membro : membroRepositorio.listarPorProjeto(projetoId)) {
            UsuarioResumo resumo = Mapeadores.resumo(membro.getUsuario());
            resposta.add(new ProjetoDtos.RespostaMembro(membro.getId(), resumo, membro.getFuncao(), membro.getCriadoEm()));
        }
        return resposta;
    }

    @Transactional
    public ProjetoDtos.RespostaMembro alterarFuncao(Long projetoId, Long membroId, PapelProjeto novaFuncao, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.OWNER);
        MembroProjeto membro = verificador.membroVinculado(membroId, projetoId);
        PapelProjeto anterior = membro.getFuncao();

        if (anterior == PapelProjeto.OWNER && novaFuncao != PapelProjeto.OWNER) {
            throw new RegraNegocioException(
                    "O proprietário não perde a função diretamente: defina OWNER em outro integrante para transferir a propriedade.");
        }
        if (novaFuncao == PapelProjeto.OWNER) {
            return transferirPropriedade(projetoId, membro, ator);
        }
        if (membro.getUsuario().getId().equals(ator.getId()) && anterior.equals(novaFuncao)) {
            return resposta(membro);
        }

        membro.setFuncao(novaFuncao);
        Projeto projeto = verificador.projetoPorId(projetoId);
        String nomeMembro = membro.getUsuario().getNome();
        servicoAtividade.registrar(projeto, ator, TipoAtividade.FUNCAO_ALTERADA, ServicoAtividade.ENT_MEMBRO,
                membro.getId(), "Alterou a função de %s de %s para %s.".formatted(nomeMembro, anterior, novaFuncao),
                java.util.Map.of("funcao", anterior), java.util.Map.of("funcao", novaFuncao));
        return resposta(membro);
    }

    /** Transferência de propriedade: novo dono recebe OWNER, o antigo passa a MANAGER. */
    private ProjetoDtos.RespostaMembro transferirPropriedade(Long projetoId, MembroProjeto membroAlvo, Usuario ator) {
        if (membroAlvo.getUsuario().getId().equals(ator.getId())) {
            throw new RegraNegocioException("Você já é o proprietário deste projeto.");
        }
        Projeto projeto = verificador.projetoPorId(projetoId);
        Usuario antigoDonoUsuario = projeto.getDono();
        MembroProjeto membroDoAntigo = membroRepositorio
                .findByProjetoIdAndUsuarioId(projetoId, antigoDonoUsuario.getId())
                .orElseThrow(() -> new RegraNegocioException("Vinculo do proprietário não encontrado."));

        membroAlvo.setFuncao(PapelProjeto.OWNER);
        membroDoAntigo.setFuncao(PapelProjeto.MANAGER);
        projeto.setDono(membroAlvo.getUsuario());
        projeto.setAtualizadoEm(LocalDateTime.now());

        String nomeNovo = membroAlvo.getUsuario().getNome();
        String nomeAntigo = antigoDonoUsuario.getNome();
        servicoAtividade.registrar(projeto, ator, TipoAtividade.PROPRIEDADE_TRANSFERIDA,
                ServicoAtividade.ENT_PROJETO, projetoId,
                "%s assumiu a propriedade do projeto (proprietário anterior: %s).".formatted(nomeNovo, nomeAntigo));
        servicoNotificacao.criarSeDiferenteDoAtor(ator, membroAlvo.getUsuario(),
                TipoNotificacao.PROPRIEDADE_TRANSFERIDA,
                "%s transferiu a propriedade do projeto '%s' para você.".formatted(ator.getNome(), projeto.getNome()),
                projeto, null);
        return resposta(membroAlvo);
    }

    @Transactional
    public void remover(Long projetoId, Long membroId, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.OWNER);
        MembroProjeto membro = verificador.membroVinculado(membroId, projetoId);
        Projeto projeto = verificador.projetoPorId(projetoId);

        if (membro.getUsuario().getId().equals(projeto.getDono().getId())) {
            throw new RegraNegocioException(
                    "O proprietário não pode ser removido comum integrante. Transfira a propriedade antes.");
        }
        if (tarefaRepositorio.existsByResponsavelIdAndProjetoIdAndArquivadaFalse(membro.getUsuario().getId(), projetoId)) {
            throw new ConflitoException(
                    "Este integrante ainda tem tarefas atribuídas. Reatribua ou remova as tarefas antes de excluí-lo do projeto.");
        }

        membroRepositorio.delete(membro);
        String nomeRemovido = membro.getUsuario().getNome();
        servicoAtividade.registrar(projeto, ator, TipoAtividade.MEMBRO_REMOVIDO, ServicoAtividade.ENT_MEMBRO, membroId,
                "Removeu o integrante %s do projeto.".formatted(nomeRemovido));
        servicoNotificacao.criarSeDiferenteDoAtor(ator, membro.getUsuario(), TipoNotificacao.MEMBRO_REMOVIDO,
                "Você foi removido do projeto '%s'.".formatted(projeto.getNome()), projeto, null);
    }

    private ProjetoDtos.RespostaMembro resposta(MembroProjeto membro) {
        return new ProjetoDtos.RespostaMembro(membro.getId(), Mapeadores.resumo(membro.getUsuario()),
                membro.getFuncao(), membro.getCriadoEm());
    }
}
