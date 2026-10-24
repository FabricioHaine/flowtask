package com.flowtask.repositorio;

/**
 * Projeção de contagem de tarefas por projeto (cartões de projetos e dashboard).
 * Usada com consultas JPQL que iniciam por t.projeto.id.
 */
public interface ContagemTarefasProjeto {
    Long getProjetoId();

    Long getTotal();

    Long getConcluidas();

    Long getAtrasadas();
}
