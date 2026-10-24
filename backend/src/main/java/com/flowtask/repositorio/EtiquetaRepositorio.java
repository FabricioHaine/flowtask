package com.flowtask.repositorio;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flowtask.dominio.Etiqueta;

public interface EtiquetaRepositorio extends JpaRepository<Etiqueta, Long> {

    List<Etiqueta> findByProjetoIdOrderByNome(Long projetoId);

    Optional<Etiqueta> findByIdAndProjetoId(Long id, Long projetoId);

    boolean existsByProjetoIdAndNomeIgnoreCase(Long projetoId, String nome);

    /** Verifica existência ignorando caixa em atualização, excluindo a própria etiqueta. */
    boolean existsByProjetoIdAndNomeIgnoreCaseAndIdNot(Long projetoId, String nome, Long id);
}
