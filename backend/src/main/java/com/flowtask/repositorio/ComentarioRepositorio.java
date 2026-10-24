package com.flowtask.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowtask.dominio.Comentario;

public interface ComentarioRepositorio extends JpaRepository<Comentario, Long> {

    @Query("""
            select c
            from Comentario c
            join fetch c.autor
            where c.tarefa.id = :tarefaId
            order by c.criadoEm asc
            """)
    List<Comentario> listarPorTarefa(@Param("tarefaId") Long tarefaId);

    long countByTarefaId(Long tarefaId);

    /** Total de comentários por tarefa dentro de um projeto (cartões do quadro). */
    @Query("""
            select c.tarefa.id, count(c.id)
            from Comentario c
            where c.tarefa.id in (select t.id from Tarefa t where t.projeto.id = :projetoId)
            group by c.tarefa.id
            """)
    List<Object[]> contarPorProjeto(@Param("projetoId") Long projetoId);
}
