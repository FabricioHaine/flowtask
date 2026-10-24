/** Lógica pura de permissões (espelha PapelProjeto.do backend). */

import { Papel } from './models';

const NIVEIS: Record<Papel, number> = { OWNER: 3, MANAGER: 2, MEMBER: 1 };

export function nivelPapel(papel: Papel | null | undefined): number {
  return papel ? NIVEIS[papel] : 0;
}

/** Tem, no mínimo, o nível do papel exigido. */
export function temAoMenos(papel: Papel | null | undefined, minimo: Papel): boolean {
  return nivelPapel(papel) >= NIVEIS[minimo];
}

/** Cria/edita tarefas, atribui, prioridade, prazo, etiquetas, convites: MANAGER+. */
export function podeGerenciarTarefas(papel: Papel | null | undefined): boolean {
  return temAoMenos(papel, 'MANAGER');
}

/** Edição de textos (título/descrição) das tarefas: MANAGER+ OU o próprio responsável. */
export function podeEditarTarefa(
  papel: Papel | null | undefined,
  usuarioId: number | null,
  responsavelId: number | null,
): boolean {
  return podeGerenciarTarefas(papel) || (usuarioId !== null && usuarioId === responsavelId);
}

/** Colunas do quadro (criar/reordenar/arquivar/excluir): somente o OWNER. */
export function podeGerenciarQuadro(papel: Papel | null | undefined): boolean {
  return papel === 'OWNER';
}

/** Membros e transferência de propriedade: somente o OWNER. */
export function podeGerenciarMembros(papel: Papel | null | undefined): boolean {
  return papel === 'OWNER';
}

/** Convidar pessoas e cancelar convites: MANAGER+. */
export function podeConvidar(papel: Papel | null | undefined): boolean {
  return temAoMenos(papel, 'MANAGER');
}

/** Excluir tarefa: somente o OWNER (o backend bloqueia quando há comentários). */
export function podeExcluirTarefa(papel: Papel | null | undefined): boolean {
  return papel === 'OWNER';
}

/** Comentar: qualquer integrante autenticado do projeto. */
export function podeComentar(papel: Papel | null | undefined): boolean {
  return papel !== null && papel !== undefined;
}
