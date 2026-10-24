package com.flowtask.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowtask.dominio.ColunaKanban;

public interface ColunaRepositorio extends JpaRepository<ColunaKanban, Long> {

    List<ColunaKanban> findByProjetoIdOrderByOrdem(Long projetoId);

    Optional<ColunaKanban> findByIdAndProjetoId(Long id, Long projetoId);

    Optional<ColunaKanban> findByProjetoIdAndColunaConclusaoTrue(Long projetoId);

    boolean existsByProjetoIdAndColunaConclusaoTrue(Long projetoId);

    boolean existsByProjetoIdAndNomeIgnoreCase(Long projetoId, String nome);

    boolean existsByProjetoIdAndNomeIgnoreCaseAndIdNot(Long projetoId, String nome, Long id);

    /** Colunas ativas do projeto; tarefas são agregadas em consulta separada, evitando N+1. */
    @Query("""
            select c
            from ColunaKanban c
            where c.projeto.id = :projetoId
              and c.ativa = true
            order by c.ordem
            """)
    List<ColunaKanban> listarAtivasPorProjeto(@Param("projetoId") Long projetoId);

    /** Colunas com tarefas atribuídas dentro do projeto, incluindo arquivadas (contagem p/ remoção). */
    long countByProjetoId(Long projetoId);
}
