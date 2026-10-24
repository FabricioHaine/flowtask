package com.flowtask.repositorio;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowtask.dominio.ConviteProjeto;

public interface ConviteRepositorio extends JpaRepository<ConviteProjeto, Long> {

    @Query("""
            select c
            from ConviteProjeto c
            join fetch c.projeto
            join fetch c.criadoPor
            where c.projeto.id = :projetoId
            order by c.criadoEm desc
            """)
    List<ConviteProjeto> listarPorProjeto(@Param("projetoId") Long projetoId);

    @Query("""
            select c
            from ConviteProjeto c
            join fetch c.projeto
            join fetch c.criadoPor
            where lower(c.email) = lower(:email)
            order by c.criadoEm desc
            """)
    List<ConviteProjeto> listarPorEmail(@Param("email") String email);

    boolean existsByProjetoIdAndEmailIgnoreCaseAndEstado(Long projetoId, String email, com.flowtask.dominio.EstadoConvite estado);

    /** Exclusão em lote dos convites de um projeto (exclusão do projeto). */
    @org.springframework.data.jpa.repository.Modifying
    @org.springframework.data.jpa.repository.Query("delete from ConviteProjeto c where c.projeto.id = :projetoId")
    long excluirPorProjeto(@Param("projetoId") Long projetoId);
}
