package com.flowtask.repositorio;

import java.util.Optional;

import jakarta.persistence.LockModeType;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.flowtask.dominio.Notificacao;

public interface NotificacaoRepositorio extends JpaRepository<Notificacao, Long> {

    @Query("select n from Notificacao n where n.destinatario.id = :usuarioId order by n.criadoEm desc, n.id desc")
    java.util.List<Notificacao> listarDoUsuario(@Param("usuarioId") Long usuarioId, Pageable pageable);

    long countByDestinatarioIdAndLidaFalse(Long usuarioId);

    @Query("select n from Notificacao n where n.id = :id and n.destinatario.id = :usuarioId")
    Optional<Notificacao> buscarDoUsuario(@Param("id") Long id, @Param("usuarioId") Long usuarioId);

    @Modifying
    @Query("update Notificacao n set n.lida = true where n.destinatario.id = :usuarioId and n.lida = false")
    int marcarTodasComoLidas(@Param("usuarioId") Long usuarioId);

    /** Exclusão em lote das notificações de um projeto (exclusão do projeto). */
    @Modifying
    @Query("delete from Notificacao n where n.projeto.id = :projetoId")
    long excluirPorProjeto(@Param("projetoId") Long projetoId);
}
