package com.flowtask.servico;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowtask.config.PropriedadesApp;
import com.flowtask.dominio.ConviteProjeto;
import com.flowtask.dominio.EstadoConvite;
import com.flowtask.dominio.MembroProjeto;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.TipoAtividade;
import com.flowtask.dominio.TipoNotificacao;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.ConviteDtos;
import com.flowtask.excecao.AcessoNegadoException;
import com.flowtask.excecao.ConflitoException;
import com.flowtask.excecao.RecursoNaoEncontradoException;
import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.ConviteRepositorio;
import com.flowtask.repositorio.MembroProjetoRepositorio;
import com.flowtask.repositorio.UsuarioRepositorio;

/**
 * Convites por e-mail. Quando um SMTP está configurado o back-end envia o
 * e-mail; sem provedor o fluxo é local (o front-end entrega o convite), o que
 * fica explícito na resposta da API e na interface.
 *
 * O convite só concede acesso após ser ACEITO por uma conta autenticada com o
 * mesmo e-mail. Nunca criamos contas em silêncio para os convidados.
 */
@Service
public class ServicoConvite {

    private final ConviteRepositorio conviteRepositorio;
    private final UsuarioRepositorio usuarioRepositorio;
    private final MembroProjetoRepositorio membroRepositorio;
    private final VerificadorAcesso verificador;
    private final ServicoAtividade servicoAtividade;
    private final ServicoNotificacao servicoNotificacao;
    private final ServicoEmail servicoEmail;
    private final PropriedadesApp propriedades;

    public ServicoConvite(ConviteRepositorio conviteRepositorio,
                          UsuarioRepositorio usuarioRepositorio,
                          MembroProjetoRepositorio membroRepositorio,
                          VerificadorAcesso verificador,
                          ServicoAtividade servicoAtividade,
                          ServicoNotificacao servicoNotificacao,
                          ServicoEmail servicoEmail,
                          PropriedadesApp propriedades) {
        this.conviteRepositorio = conviteRepositorio;
        this.usuarioRepositorio = usuarioRepositorio;
        this.membroRepositorio = membroRepositorio;
        this.verificador = verificador;
        this.servicoAtividade = servicoAtividade;
        this.servicoNotificacao = servicoNotificacao;
        this.servicoEmail = servicoEmail;
        this.propriedades = propriedades;
    }

    @Transactional
    public ConviteDtos.RespostaConvite criar(Long projetoId, ConviteDtos.CriarConvite dto, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.MANAGER);
        Projeto projeto = verificador.projetoPorId(projetoId);

        String email = dto.email().trim().toLowerCase(Locale.ROOT);
        if (dto.funcaoProposta() == PapelProjeto.OWNER) {
            throw new RegraNegocioException(
                    "Não é possível convidar como OWNER. A propriedade muda somente por transferência explícita.");
        }
        if (email.equals(ator.getEmail().toLowerCase(Locale.ROOT))) {
            throw new RegraNegocioException("Você já integra este projeto.");
        }

        usuarioRepositorio.findByEmailIgnoreCase(email).ifPresent(destino -> {
            if (membroRepositorio.existsByProjetoIdAndUsuarioId(projetoId, destino.getId())) {
                throw new RegraNegocioException("Este e-mail já é integrante do projeto.");
            }
        });

        boolean pendente = conviteRepositorio
                .existsByProjetoIdAndEmailIgnoreCaseAndEstado(projetoId, email, EstadoConvite.PENDENTE);
        if (pendente) {
            throw new ConflitoException("Já existe um convite pendente para este e-mail.");
        }

        LocalDateTime expiracao = LocalDateTime.now().plusDays(propriedades.getConvites().getExpiracaoDias());
        ConviteProjeto convite = new ConviteProjeto(projeto, email, dto.funcaoProposta(), ator, expiracao);
        conviteRepositorio.save(convite);

        servicoAtividade.registrar(projeto, ator, TipoAtividade.CONVITE_ENVIADO, ServicoAtividade.ENT_CONVITE,
                convite.getId(),
                "Convidou %s como %s (convite válido até %s).".formatted(email, dto.funcaoProposta(), expiracao.toLocalDate()));

        usuarioRepositorio.findByEmailIgnoreCase(email).ifPresent(destino ->
                servicoNotificacao.criarSeDiferenteDoAtor(ator, destino, TipoNotificacao.CONVITE_RECEBIDO,
                        "%s convidou você para o projeto '%s'.".formatted(ator.getNome(), projeto.getNome()),
                        projeto, null));

        servicoEmail.enviarConvite(ator, email, projeto.getNome(), ator.getNome());
        return resposta(convite);
    }

    @Transactional(readOnly = true)
    public List<ConviteDtos.RespostaConvite> listarPorProjeto(Long projetoId, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.MANAGER);
        List<ConviteDtos.RespostaConvite> resposta = new ArrayList<>();
        for (ConviteProjeto convite : conviteRepositorio.listarPorProjeto(projetoId)) {
            resposta.add(resposta(convite));
        }
        return resposta;
    }

    /** Convites endereçados ao e-mail do usuário autenticado (estado atualizado preguiçosamente). */
    @Transactional
    public List<ConviteDtos.RespostaConvite> listarMeus(Usuario ator) {
        List<ConviteDtos.RespostaConvite> resposta = new ArrayList<>();
        for (ConviteProjeto convite : conviteRepositorio.listarPorEmail(ator.getEmail())) {
            EstadoConvite antes = convite.getEstado();
            convite.expirarSeNecessario();
            if (antes != convite.getEstado()) {
                conviteRepositorio.save(convite);
            }
            resposta.add(resposta(convite));
        }
        return resposta;
    }

    @Transactional
    public ConviteDtos.RespostaConvite aceitar(Long conviteId, Usuario ator) {
        ConviteProjeto convite = conviteRepositorio.findById(conviteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Convite não encontrado."));
        convite.expirarSeNecessario();

        if (convite.getEstado() == EstadoConvite.EXPIRADO) {
            throw new ConflitoException("Este convite expirou. Solicite um novo convite por e-mail.");
        }
        if (convite.getEstado() != EstadoConvite.PENDENTE) {
            throw new ConflitoException("Este convite já foi processado.");
        }
        if (!convite.getEmail().equalsIgnoreCase(ator.getEmail())) {
            throw new AcessoNegadoException(
                    "Este convite não pertence à sua conta. Entre com o e-mail convidado ou crie uma conta com ele.");
        }

        Projeto projeto = convite.getProjeto();
        if (membroRepositorio.existsByProjetoIdAndUsuarioId(projeto.getId(), ator.getId())) {
            throw new ConflitoException("Você já é integrante deste projeto.");
        }

        membroRepositorio.save(new MembroProjeto(projeto, ator, convite.getFuncaoProposta()));
        convite.setEstado(EstadoConvite.ACEITO);
        conviteRepositorio.save(convite);

        servicoAtividade.registrar(projeto, ator, TipoAtividade.CONVITE_ACEITO, ServicoAtividade.ENT_CONVITE,
                convite.getId(),
                "%s aceitou o convite e entrou no projeto como %s.".formatted(ator.getNome(), convite.getFuncaoProposta()));

        servicoNotificacao.criarSeDiferenteDoAtor(ator, convite.getCriadoPor(), TipoNotificacao.CONVITE_ACEITO,
                "%s aceitou o convite para o projeto '%s'.".formatted(ator.getNome(), projeto.getNome()),
                projeto, null);
        return resposta(convite);
    }

    @Transactional
    public ConviteDtos.RespostaConvite recusar(Long conviteId, Usuario ator) {
        ConviteProjeto convite = conviteRepositorio.findById(conviteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Convite não encontrado."));
        convite.expirarSeNecessario();

        if (convite.getEstado() != EstadoConvite.PENDENTE) {
            throw new ConflitoException("Este convite já foi processado.");
        }
        if (!convite.getEmail().equalsIgnoreCase(ator.getEmail())) {
            throw new AcessoNegadoException("Este convite não pertence à sua conta.");
        }

        convite.setEstado(EstadoConvite.RECUSADO);
        conviteRepositorio.save(convite);

        servicoAtividade.registrar(convite.getProjeto(), ator, TipoAtividade.CONVITE_RECUSADO,
                ServicoAtividade.ENT_CONVITE, convite.getId(), "%s recusou o convite.".formatted(ator.getNome()));
        servicoNotificacao.criarSeDiferenteDoAtor(ator, convite.getCriadoPor(), TipoNotificacao.CONVITE_RECUSADO,
                "%s recusou o convite para o projeto '%s'.".formatted(ator.getNome(),
                        convite.getProjeto().getNome()),
                convite.getProjeto(), null);
        return resposta(convite);
    }

    @Transactional
    public void cancelar(Long projetoId, Long conviteId, Usuario ator) {
        PapelProjeto papel = verificador.exigirPapel(projetoId, ator, PapelProjeto.MANAGER);
        ConviteProjeto convite = conviteRepositorio.findById(conviteId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Convite não encontrado."));
        if (!convite.getProjeto().getId().equals(projetoId)) {
            throw new RecursoNaoEncontradoException("Convite não encontrado neste projeto.");
        }
        if (papel == PapelProjeto.MANAGER && !convite.getCriadoPor().getId().equals(ator.getId())) {
            throw new AcessoNegadoException("Você só pode cancelar convites criados por você.");
        }
        if (convite.getEstado() != EstadoConvite.PENDENTE) {
            throw new ConflitoException("Somente convites pendentes podem ser cancelados.");
        }

        convite.setEstado(EstadoConvite.CANCELADO);
        conviteRepositorio.save(convite);
        servicoAtividade.registrar(convite.getProjeto(), ator, TipoAtividade.CONVITE_CANCELADO,
                ServicoAtividade.ENT_CONVITE, convite.getId(), "Cancelou o convite para %s.".formatted(convite.getEmail()));
    }

    private ConviteDtos.RespostaConvite resposta(ConviteProjeto convite) {
        return new ConviteDtos.RespostaConvite(
                convite.getId(),
                convite.getProjeto().getId(),
                convite.getProjeto().getNome(),
                convite.getEmail(),
                convite.getFuncaoProposta(),
                Mapeadores.resumo(convite.getCriadoPor()),
                convite.getCriadoEm(),
                convite.getExpiraEm(),
                convite.getEstado(),
                usuarioRepositorio.existsByEmailIgnoreCase(convite.getEmail()));
    }
}
