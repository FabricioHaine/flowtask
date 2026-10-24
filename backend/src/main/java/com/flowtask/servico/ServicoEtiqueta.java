package com.flowtask.servico;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.flowtask.dominio.Etiqueta;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.Usuario;
import com.flowtask.dto.ColaboracaoDtos;
import com.flowtask.excecao.ConflitoException;
import com.flowtask.excecao.RecursoNaoEncontradoException;
import com.flowtask.repositorio.EtiquetaRepositorio;

/**
 * Etiquetas de projeto (categoria visual das tarefas). Criação/edição são de
 * MANAGER/OWNER; associação a tarefas acontece em ServicoTarefa.
 */
@Service
public class ServicoEtiqueta {

    private final EtiquetaRepositorio repositorio;
    private final VerificadorAcesso verificador;

    public ServicoEtiqueta(EtiquetaRepositorio repositorio, VerificadorAcesso verificador) {
        this.repositorio = repositorio;
        this.verificador = verificador;
    }

    @Transactional(readOnly = true)
    public List<ColaboracaoDtos.RespostaEtiqueta> listar(Long projetoId, Usuario ator) {
        verificador.exigirAcesso(projetoId, ator);
        List<ColaboracaoDtos.RespostaEtiqueta> resposta = new ArrayList<>();
        for (Etiqueta etiqueta : repositorio.findByProjetoIdOrderByNome(projetoId)) {
            resposta.add(new ColaboracaoDtos.RespostaEtiqueta(etiqueta.getId(), etiqueta.getNome(), etiqueta.getCor()));
        }
        return resposta;
    }

    @Transactional
    public ColaboracaoDtos.RespostaEtiqueta criar(Long projetoId, ColaboracaoDtos.CriarEtiqueta dto, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.MANAGER);
        Projeto projeto = verificador.projetoPorId(projetoId);
        String nome = dto.nome().trim();
        if (repositorio.existsByProjetoIdAndNomeIgnoreCase(projetoId, nome)) {
            throw new ConflitoException("Já existe uma etiqueta com este nome no projeto.");
        }
        Etiqueta etiqueta = repositorio.save(new Etiqueta(projeto, nome, dto.cor()));
        return new ColaboracaoDtos.RespostaEtiqueta(etiqueta.getId(), etiqueta.getNome(), etiqueta.getCor());
    }

    @Transactional
    public ColaboracaoDtos.RespostaEtiqueta editar(Long projetoId, Long etiquetaId, ColaboracaoDtos.CriarEtiqueta dto,
                                                   Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.MANAGER);
        Etiqueta etiqueta = repositorio.findByIdAndProjetoId(etiquetaId, projetoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Etiqueta não encontrada neste projeto."));
        String nome = dto.nome().trim();
        if (!nome.equalsIgnoreCase(etiqueta.getNome())
                && repositorio.existsByProjetoIdAndNomeIgnoreCaseAndIdNot(projetoId, nome, etiquetaId)) {
            throw new ConflitoException("Já existe uma etiqueta com este nome no projeto.");
        }
        etiqueta.setNome(nome);
        if (dto.cor() != null) {
            etiqueta.setCor(dto.cor());
        }
        return new ColaboracaoDtos.RespostaEtiqueta(etiqueta.getId(), etiqueta.getNome(), etiqueta.getCor());
    }

    @Transactional
    public void excluir(Long projetoId, Long etiquetaId, Usuario ator) {
        verificador.exigirPapel(projetoId, ator, PapelProjeto.MANAGER);
        Etiqueta etiqueta = repositorio.findByIdAndProjetoId(etiquetaId, projetoId)
                .orElseThrow(() -> new RecursoNaoEncontradoException("Etiqueta não encontrada neste projeto."));
        repositorio.delete(etiqueta);
    }
}
