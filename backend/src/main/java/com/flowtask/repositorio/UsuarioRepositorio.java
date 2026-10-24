package com.flowtask.repositorio;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.flowtask.dominio.Usuario;

public interface UsuarioRepositorio extends JpaRepository<Usuario, Long> {

    Optional<Usuario> findByEmailIgnoreCase(String email);

    boolean existsByEmailIgnoreCase(String email);
}
