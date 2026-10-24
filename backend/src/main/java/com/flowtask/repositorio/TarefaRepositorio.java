package com.flowtask.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowtask.dominio.Tarefa;

public interface TarefaRepositorio extends JpaRepository<Tarefa, Long> {

    /** Tarefas de uma coluna em ordem — base da reordenação no movimento do kanban. */
    List<Tarefa> findByColunaIdOrderByOrdemAsc(Long colunaId);

    long countByColunaId(Long colunaId);

    /** Todas as tarefas NÃO arquivadas de um projeto, com coluna e etiquetas. */
    @Query("""
            select distinct t
            from Tarefa t
            join fetch t.coluna
            left join fetch t.etiquetas
            where t.projeto.id = :projetoId
              and t.arquivada = false
            order by t.coluna.ordem, t.ordem
            """)
    List<Tarefa> listarAtivasPorProjeto(@Param("projetoId") Long projetoId);

    @Query("""
            select t
            from Tarefa t
            where t.projeto.id = :projetoId
              and t.arquivada = true
            order by t.atualizadoEm desc
            """)
    List<Tarefa> listarArquivadasPorProjeto(@Param("projetoId") Long projetoId);

    @Query("""
            select distinct t
            from Tarefa t
            join fetch t.coluna
            left join fetch t.etiquetas
            where t.id = :id
            """)
    Optional<Tarefa> buscarCompleta(@Param("id") Long id);

    /** Tarefas atribuídas ao usuário em projetos em que ele é integrante. */
    @Query("""
            select distinct t
            from Tarefa t
            join fetch t.coluna
            join fetch t.projeto
            left join fetch t.etiquetas
            where t.responsavel.id = :usuarioId
              and t.arquivada = false
            order by t.prazo asc nulls last, t.atualizadoEm desc
            """)
    List<Tarefa> listarDoResponsavel(@Param("usuarioId") Long usuarioId);

    /** Tarefas das quais o usuário é responsável, filtrando arquivadas ou não. */
    long countByProjetoId(Long projetoId);

    @Query("select t.coluna.id, count(t.id) from Tarefa t where t.projeto.id = :projetoId and t.arquivada = false group by t.coluna.id")
    List<Object[]> contarPorColuna(@Param("projetoId") Long projetoId);

    boolean existsByIdAndProjetoId(Long id, Long projetoId);

    /** Verifica se um integrante ainda possui tarefas atribuídas (bloqueio de remoção). */
    boolean existsByResponsavelIdAndProjetoIdAndArquivadaFalse(Long responsavelId, Long projetoId);

    /** Sincroniza a data de conclusão das tarefas quando uma coluna passa a concluir tarefas. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Tarefa t set t.dataConclusao = :instante, t.atualizadoEm = :instante where t.coluna.id = :colunaId")
    void marcarConclusaoDasTarefas(@Param("colunaId") Long colunaId, @Param("instante") java.time.LocalDateTime instante);

    /** Ao remover o sinalizador de conclusão, as tarefas da coluna são reabertas. */
    @org.springframework.data.jpa.repository.Modifying
    @Query("update Tarefa t set t.dataConclusao = null, t.atualizadoEm = :agora where t.coluna.id = :colunaId")
    void limparConclusaoDasTarefas(@Param("colunaId") Long colunaId,
                                   @Param("agora") java.time.LocalDateTime agora);

    /** Tarefas ativas de vários projetos (dashboard do usuário). */
    @Query("""
            select distinct t
            from Tarefa t
            join fetch t.coluna
            join fetch t.projeto
            left join fetch t.etiquetas
            where t.projeto.id in :projetoIds
              and t.arquivada = false
            """)
    List<Tarefa> listarAtivasPorProjetos(@Param("projetoIds") java.util.Collection<Long> projetoIds);

    /** Tarefas ativas de um projeto (resumo do projeto + canvas). */
    @Query("""
            select t
            from Tarefa t
            where t.projeto.id = :projetoId
              and t.arquivada = false
            """)
    List<Tarefa> listarAtivasSemColuna(@Param("projetoId") Long projetoId);
}
