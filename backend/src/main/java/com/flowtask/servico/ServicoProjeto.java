package com.flowtask.servico;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowtask.dominio.ColunaKanban;
import com.flowtask.dominio.EstadoProjeto;
import com.flowtask.dominio.MembroProjeto;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.TipoAtividade;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.ProjetoDtos;
import com.flowtask.dto.UsuarioResumo;
import com.flowtask.excecao.RegraNegocioException;
import com.flowtask.repositorio.ColunaRepositorio;
import com.flowtask.repositorio.ContagemTarefasProjeto;
import com.flowtask.repositorio.MembroProjetoRepositorio;
import com.flowtask.repositorio.ProjetoRepositorio;

/**
 * Regras de negócio de projetos: criação (com colunas padrão), listagem
 * filtrada dos projetos acessíveis, edição e estados.
 */
@Service
public class ServicoProjeto {

    static final int LIMITE_LISTAGEM = 500;

    private final ProjetoRepositorio projetoRepositorio;
    private final MembroProjetoRepositorio membroRepositorio;
    private final ColunaRepositorio colunaRepositorio;
    private final VerificadorAcesso verificador;
    private final ServicoAtividade servicoAtividade;

    public ServicoProjeto(ProjetoRepositorio projetoRepositorio,
                          MembroProjetoRepositorio membroRepositorio,
                          ColunaRepositorio colunaRepositorio,
                          VerificadorAcesso verificador,
                          ServicoAtividade servicoAtividade) {
        this.projetoRepositorio = projetoRepositorio;
        this.membroRepositorio = membroRepositorio;
        this.colunaRepositorio = colunaRepositorio;
        this.verificador = verificador;
        this.servicoAtividade = servicoAtividade;
    }

    // ---------------------------------------------------------------- criação

    @Transactional
    public ProjetoDtos.RespostaProjeto criar(ProjetoDtos.CriarProjeto dto, Usuario ator) {
        validarDatas(dto.dataInicio(), dto.prazoFinal());
        Projeto projeto = new Projeto(dto.nome().trim(), recortar(dto.descricao()), ator);
        projeto.setDataInicio(dto.dataInicio());
        projeto.setPrazoFinal(dto.prazoFinal());
        projetoRepositorio.save(projeto);

        membroRepositorio.save(new MembroProjeto(projeto, ator, PapelProjeto.OWNER));
        criarColunasPadrao(projeto);

        servicoAtividade.registrar(projeto, ator, TipoAtividade.PROJETO_CRIADO, ServicoAtividade.ENT_PROJETO,
                projeto.getId(), "Criou o projeto '%s'.".formatted(projeto.getNome()));

        return resposta(projeto, PapelProjeto.OWNER, null);
    }

    /** Colunas padrão exigidas pelo quadro: 4 colunas; CONCLUÍDO marca a conclusão. */
    private void criarColunasPadrao(Projeto projeto) {
        String[][] padrao = {
                {"A FAZER", "false"},
                {"EM ANDAMENTO", "false"},
                {"EM REVISÃO", "false"},
                {"CONCLUÍDO", "true"}
        };
        for (int i = 0; i < padrao.length; i++) {
            ColunaKanban coluna = new ColunaKanban(projeto, padrao[i][0], i, Boolean.parseBoolean(padrao[i][1]));
            colunaRepositorio.save(coluna);
            projeto.addColuna(coluna);
        }
    }

    // ---------------------------------------------------------------- consulta

    @Transactional(readOnly = true)
    public List<ProjetoDtos.RespostaProjeto> listar(Usuario ator, String busca, EstadoProjeto estado, OrdenacaoProjeto ordenacao) {
        List<Projeto> projetos = projetoRepositorio
                .listarDoUsuario(ator.getId(), PageRequest.of(0, LIMITE_LISTAGEM));

        Map<Long, ContagemTarefasProjeto> contagens = new HashMap<>();
        for (ContagemTarefasProjeto c : membroRepositorio.contarTarefasPorProjeto(ator.getId(), LocalDate.now())) {
            contagens.put(c.getProjetoId(), c);
        }

        List<ProjetoDtos.RespostaProjeto> respostas = new ArrayList<>(projetos.size());
        for (Projeto projeto : projetos) {
            if (busca != null && !busca.isBlank()) {
                String alvo = busca.trim().toLowerCase(Locale.ROOT);
                boolean casa = (projeto.getNome() != null && projeto.getNome().toLowerCase(Locale.ROOT).contains(alvo))
                        || (projeto.getDescricao() != null
                        && projeto.getDescricao().toLowerCase(Locale.ROOT).contains(alvo));
                if (!casa) {
                    continue;
                }
            }
            if (estado != null && projeto.getEstado() != estado) {
                continue;
            }

            List<MembroProjeto> membros = membroRepositorio.listarPorProjeto(projeto.getId());
            PapelProjeto papel = PapelProjeto.MEMBER;
            for (MembroProjeto m : membros) {
                if (m.getUsuario().getId().equals(ator.getId())) {
                    papel = m.getFuncao();
                    break;
                }
            }

            respostas.add(montarResposta(projeto, papel, membros, contagens.get(projeto.getId())));
        }

        ordenar(respostas, ordenacao);
        return respostas;
    }

    @Transactional(readOnly = true)
    public ProjetoDtos.RespostaProjeto obterDetalhe(Long projetoId, Usuario ator) {
        PapelProjeto papel = verificador.exigirAcesso(projetoId, ator);
        Projeto projeto = verificador.projetoPorId(projetoId);
        List<MembroProjeto> membros = membroRepositorio.listarPorProjeto(projetoId);
        Map<Long, ContagemTarefasProjeto> contagens = contarDoUsuario(ator);
        return montarResposta(projeto, papel, membros, contagens.get(projetoId));
    }

    private Map<Long, ContagemTarefasProjeto> contarDoUsuario(Usuario ator) {
        Map<Long, ContagemTarefasProjeto> contagens = new HashMap<>();
        for (ContagemTarefasProjeto c : membroRepositorio.contarTarefasPorProjeto(ator.getId(), LocalDate.now())) {
            contagens.put(c.getProjetoId(), c);
        }
        return contagens;
    }

    // ---------------------------------------------------------------- escrita

    @Transactional
    public ProjetoDtos.RespostaProjeto atualizar(Long projetoId, ProjetoDtos.AtualizarProjeto dto, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.OWNER);
        Projeto projeto = verificador.projetoPorId(projetoId);
        validarDatas(dto.dataInicio(), dto.prazoFinal());

        Map<String, Object> antes = Map.of(
                "nome", projeto.getNome(),
                "descricao", Optional.ofNullable(projeto.getDescricao()).orElse(""),
                "dataInicio", Optional.ofNullable(projeto.getDataInicio()).map(LocalDate::toString).orElse(""),
                "prazoFinal", Optional.ofNullable(projeto.getPrazoFinal()).map(LocalDate::toString).orElse(""));

        String nomeAntes = projeto.getNome();
        projeto.setNome(dto.nome().trim());
        projeto.setDescricao(recortar(dto.descricao()));
        projeto.setDataInicio(dto.dataInicio());
        projeto.setPrazoFinal(dto.prazoFinal());
        projeto.setAtualizadoEm(java.time.LocalDateTime.now());

        boolean mudou = !Objects.equals(nomeAntes, projeto.getNome())
                || !Objects.equals(antes.get("descricao"), valorDescricao(dto.descricao()))
                || !Objects.equals(antes.get("dataInicio"), valorData(dto.dataInicio()))
                || !Objects.equals(antes.get("prazoFinal"), valorData(dto.prazoFinal()));

        Map<String, Object> depois = Map.of(
                "nome", projeto.getNome(),
                "descricao", valorDescricao(dto.descricao()),
                "dataInicio", valorData(dto.dataInicio()),
                "prazoFinal", valorData(dto.prazoFinal()));

        if (mudou) {
            servicoAtividade.registrar(projeto, ator, TipoAtividade.PROJETO_ATUALIZADO,
                    ServicoAtividade.ENT_PROJETO, projeto.getId(), "Atualizou as configurações do projeto.", antes, depois);
        }
        return resposta(projeto, PapelProjeto.OWNER, null);
    }

    @Transactional
    public ProjetoDtos.RespostaProjeto mudarEstado(Long projetoId, ProjetoDtos.MudarEstadoProjeto dto, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.OWNER);
        Projeto projeto = verificador.projetoPorId(projetoId);
        EstadoProjeto anterior = projeto.getEstado();
        if (anterior == dto.estado()) {
            return resposta(projeto, PapelProjeto.OWNER, null);
        }

        projeto.setEstado(dto.estado());
        projeto.setAtualizadoEm(java.time.LocalDateTime.now());

        servicoAtividade.registrar(projeto, ator, TipoAtividade.PROJETO_ESTADO_ALTERADO, ServicoAtividade.ENT_PROJETO,
                projeto.getId(), "Alterou o estado do projeto de %s para %s.".formatted(anterior, dto.estado()),
                Map.of("estado", anterior), Map.of("estado", dto.estado()));

        // Histórico registra a mudança; convites e quadro continuam acessíveis,
        // movimentações são bloqueadas apenas para projetos arquivados (verificado nas tarefas).
        return resposta(projeto, PapelProjeto.OWNER, null);
    }

    // ---------------------------------------------------------------- helpers

    private void validarDatas(LocalDate dataInicio, LocalDate prazoFinal) {
        if (dataInicio != null && prazoFinal != null && prazoFinal.isBefore(dataInicio)) {
            throw new RegraNegocioException("O prazo final não pode ser anterior à data de início.");
        }
    }

    private String recortar(String texto) {
        if (texto == null) {
            return null;
        }
        String t = texto.trim();
        return t.isEmpty() ? null : t;
    }

    private String valorDescricao(String valor) {
        return Optional.ofNullable(valor).orElse("").trim();
    }

    private String valorData(LocalDate data) {
        return Optional.ofNullable(data).map(LocalDate::toString).orElse("");
    }

    private void ordenar(List<ProjetoDtos.RespostaProjeto> respostas, OrdenacaoProjeto ordenacao) {
        OrdenacaoProjeto criterio = ordenacao == null ? OrdenacaoProjeto.ATUALIZADO : ordenacao;
        switch (criterio) {
            case NOME -> respostas.sort((a, b) -> a.nome().compareToIgnoreCase(b.nome()));
            case PRAZO -> respostas.sort((a, b) -> {
                if (a.prazoFinal() == null && b.prazoFinal() == null) return 0;
                if (a.prazoFinal() == null) return 1;
                if (b.prazoFinal() == null) return -1;
                return a.prazoFinal().compareTo(b.prazoFinal());
            });
            case ATUALIZADO -> {
                // já vem por atualizadoEm do repositório
            }
        }
    }

    private ProjetoDtos.RespostaProjeto resposta(Projeto projeto, PapelProjeto papel,
                                                 ContagemTarefasProjeto contagem) {
        List<MembroProjeto> membros = membroRepositorio.listarPorProjeto(projeto.getId());
        return montarResposta(projeto, papel, membros, contagem);
    }

    ProjetoDtos.RespostaProjeto resposta(Projeto projeto, PapelProjeto papel) {
        return resposta(projeto, papel, null);
    }

    /** Monta o DTO de resposta com membros, contagens e papel do usuário. */
    ProjetoDtos.RespostaProjeto montarResposta(Projeto projeto, PapelProjeto papelUsuario,
                                               List<MembroProjeto> membros,
                                               ContagemTarefasProjeto contagem) {
        long total = contagem == null ? 0L : contagem.getTotal();
        long concluidas = contagem == null ? 0L : contagem.getConcluidas();
        long atrasadas = contagem == null ? 0L : contagem.getAtrasadas();
        int progresso = total > 0 ? (int) Math.round((concluidas * 100.0) / total) : 0;

        List<UsuarioResumo> resumoMembros = new ArrayList<>(membros.size());
        for (MembroProjeto m : membros) {
            resumoMembros.add(Mapeadores.resumo(m.getUsuario()));
        }

        return new ProjetoDtos.RespostaProjeto(
                projeto.getId(),
                projeto.getNome(),
                projeto.getDescricao(),
                projeto.getEstado(),
                projeto.getDataInicio(),
                projeto.getPrazoFinal(),
                projeto.getCriadoEm(),
                projeto.getAtualizadoEm(),
                Mapeadores.resumo(projeto.getDono()),
                papelUsuario,
                total,
                concluidas,
                progresso,
                resumoMembros);
    }

    // ---------------------------------------------------------------- enums

    public enum OrdenacaoProjeto {
        ATUALIZADO,
        NOME,
        PRAZO
    }
}
