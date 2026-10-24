package com.flowtask.suporte;

import java.lang.reflect.Field;

import com.flowtask.dominio.ColunaKanban;
import com.flowtask.dominio.MembroProjeto;
import com.flowtask.dominio.PapelProjeto;
import com.flowtask.dominio.PrioridadeTarefa;
import com.flowtask.dominio.Projeto;
import com.flowtask.dominio.Tarefa;
import com.flowtask.dominio.Usuario;

/**
 * Fábrica de entidades para testes unitários. Os ids das entidades não têm
 * setters (o JPA os define), então são atribuídos por reflexão.
 */
public final class SuporteTeste {

    private SuporteTeste() {
    }

    public static <T> T comId(T entidade, Long id) {
        try {
            Field campo = entidade.getClass().getDeclaredField("id");
            campo.setAccessible(true);
            campo.set(entidade, id);
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException("Não foi possível definir o id de teste.", e);
        }
        return entidade;
    }

    public static Usuario usuario(String nome, String email, Long id) {
        return comId(new Usuario(nome, email, "hash-bcrypt"), id);
    }

    public static Projeto projeto(String nome, Usuario dono, Long id) {
        return comId(new Projeto(nome, null, dono), id);
    }

    public static ColunaKanban coluna(Projeto projeto, String nome, int ordem, boolean colunaConclusao, Long id) {
        return comId(new ColunaKanban(projeto, nome, ordem, colunaConclusao), id);
    }

    public static Tarefa tarefa(Projeto projeto, ColunaKanban coluna, String titulo, Usuario criador, Long id) {
        return comId(new Tarefa(projeto, coluna, titulo, PrioridadeTarefa.MEDIA, criador), id);
    }

    public static MembroProjeto membro(Projeto projeto, Usuario usuario, PapelProjeto funcao, Long id) {
        return comId(new MembroProjeto(projeto, usuario, funcao), id);
    }
}
