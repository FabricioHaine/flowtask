package com.flowtask.repositorio;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowtask.dominio.Projeto;

public interface ProjetoRepositorio extends JpaRepository<Projeto, Long> {

    /**
     * Carrega o projeto com bloqueio pessimista de escrita. Usado por operações
     * que reordenam o quadro, para serializar movimentações concorrentes do
     * mesmo projeto e evitar deadlocks.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select p from Projeto p where p.id = :id")
    Optional<Projeto> buscarComBloqueio(@Param("id") Long id);

    @Query("""
            select p
            from Projeto p
            join MembroProjeto m on m.projeto = p
            where m.usuario.id = :usuarioId
            order by p.atualizadoEm desc
            """)
    java.util.List<Projeto> listarDoUsuario(@Param("usuarioId") Long usuarioId, Pageable pageable);
}
