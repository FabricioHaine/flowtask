package com.flowtask.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowtask.dominio.MembroProjeto;

/**
 * Associação usuário ↔ projeto e consultas auxiliares de contagem.
 */
public interface MembroProjetoRepositorio extends JpaRepository<MembroProjeto, Long> {

    @Query("""
            select m
            from MembroProjeto m
            join fetch m.usuario
            where m.projeto.id = :projetoId
            order by case m.funcao when 'OWNER' then 0 when 'MANAGER' then 1 else 2 end, m.criadoEm
            """)
    List<MembroProjeto> listarPorProjeto(@Param("projetoId") Long projetoId);

    @Query("""
            select m
            from MembroProjeto m
            join fetch m.projeto
            where m.usuario.id = :usuarioId
            """)
    List<MembroProjeto> listarDoUsuario(@Param("usuarioId") Long usuarioId);

    Optional<MembroProjeto> findByProjetoIdAndUsuarioId(Long projetoId, Long usuarioId);

    boolean existsByProjetoIdAndUsuarioId(Long projetoId, Long usuarioId);

    /**
     * Contagem de tarefas por projeto do usuário: total, concluídas (coluna de
     * conclusão) e atrasadas. Exclui tarefas arquivadas.
     */
    @Query("""
            select t.projeto.id as projetoId,
                   count(t.id) as total,
                   sum(case when t.coluna.colunaConclusao = true then 1 else 0 end) as concluidas,
                   sum(case when t.prazo < :hoje and t.coluna.colunaConclusao = false then 1 else 0 end) as atrasadas
            from Tarefa t
            where t.projeto.id in (select m.projeto.id from MembroProjeto m where m.usuario.id = :usuarioId)
              and t.arquivada = false
            group by t.projeto.id
            """)
    List<ContagemTarefasProjeto> contarTarefasPorProjeto(@Param("usuarioId") Long usuarioId,
                                                         @Param("hoje") java.time.LocalDate hoje);

    boolean existsByIdAndUsuarioId(Long id, Long usuarioId);

    /** Exclusão em lote dos vínculos de integrantes de um projeto (exclusão do projeto). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from MembroProjeto m where m.projeto.id = :projetoId")
    long excluirPorProjeto(@Param("projetoId") Long projetoId);
}
