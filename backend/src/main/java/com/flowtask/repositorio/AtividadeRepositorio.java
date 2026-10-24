package com.flowtask.repositorio;

import java.util.List;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowtask.dominio.Atividade;

public interface AtividadeRepositorio extends JpaRepository<Atividade, Long> {

    long countByProjetoId(Long projetoId);

    long countByProjetoIdAndEntidadeAndEntidadeId(Long projetoId, String entidade, Long entidadeId);

    @Query("""
            select a
            from Atividade a
            join fetch a.usuario
            where a.projeto.id = :projetoId
            order by a.criadoEm desc, a.id desc
            """)
    List<Atividade> listarPorProjeto(@Param("projetoId") Long projetoId, Pageable pageable);

    @Query("""
            select a
            from Atividade a
            join fetch a.usuario
            where a.projeto.id = :projetoId
              and a.entidade = :entidade
              and a.entidadeId = :entidadeId
            order by a.criadoEm desc, a.id desc
            """)
    List<Atividade> listarPorEntidade(@Param("projetoId") Long projetoId,
                                      @Param("entidade") String entidade,
                                      @Param("entidadeId") Long entidadeId,
                                      Pageable pageable);

    /** Atividade recente nos projetos do usuário (usada no dashboard). */
    @Query("""
            select a
            from Atividade a
            join fetch a.usuario
            join fetch a.projeto
            where a.projeto.id in (select m.projeto.id from MembroProjeto m where m.usuario.id = :usuarioId)
            order by a.criadoEm desc, a.id desc
            """)
    List<Atividade> listarRecentesDoUsuario(@Param("usuarioId") Long usuarioId, Pageable pageable);
}
