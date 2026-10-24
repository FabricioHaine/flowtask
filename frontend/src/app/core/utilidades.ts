/** Formatação e rótulos compartilhados. */

import { EstadoProjeto, Papel, Prioridade } from './models';

export function iniciais(nome: string | null | undefined): string {
  if (!nome || !nome.trim()) {
    return '?';
  }
  const partes = nome.trim().split(/\s+/);
  const primeira = partes[0].charAt(0).toUpperCase();
  const segunda = partes.length > 1 ? partes[partes.length - 1].charAt(0).toUpperCase() : '';
  return segunda ? `${primeira}${segunda}` : primeira;
}

export function formatarData(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [data] = iso.split('T');
  return data; // "yyyy-MM-dd"
}

export function formatarDataLocal(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const soData = iso.split('T')[0];
  const [ano, mes, dia] = soData.split('-');
  return `${dia}/${mes}/${ano}`;
}

export function formatarDataHora(iso: string | null | undefined): string {
  if (!iso) {
    return '—';
  }
  const [data, horaBruta] = iso.split('T');
  const hora = (horaBruta ?? '').slice(0, 5);
  const [ano, mes, dia] = data.split('-');
  return hora ? `${dia}/${mes}/${ano} ${hora}` : `${dia}/${mes}/${ano}`;
}

/** Dias até o prazo (negativo = vencida). Retorna null quando não há prazo. */
export function diasRestantes(prazo: string | null | undefined): number | null {
  if (!prazo) {
    return null;
  }
  const alvo = new Date(`${prazo.split('T')[0]}T00:00:00`);
  const hoje = new Date();
  hoje.setHours(0, 0, 0, 0);
  return Math.round((alvo.getTime() - hoje.getTime()) / 86_400_000);
}

export function classePrioridade(prioridade: Prioridade | null): string {
  switch (prioridade) {
    case 'ALTA': return 'alta';
    case 'CRITICA': return 'critica';
    case 'MEDIA': return 'media';
    case 'BAIXA': return 'baixa';
    default: return 'neutro';
  }
}

export const ROTULO_PRIORIDADE: Record<Prioridade, string> = {
  BAIXA: 'Baixa',
  MEDIA: 'Média',
  ALTA: 'Alta',
  CRITICA: 'Crítica',
};

export const ROTULO_PAPEL: Record<Papel, string> = {
  OWNER: 'Proprietário',
  MANAGER: 'Gerente',
  MEMBER: 'Membro',
};

export const ROTULO_ESTADO: Record<EstadoProjeto, string> = {
  PLANEJADO: 'Planejado',
  ATIVO: 'Ativo',
  PAUSADO: 'Pausado',
  CONCLUIDO: 'Concluído',
  ARQUIVADO: 'Arquivado',
};

/** Classe de badge para o estado do projeto. */
export function classeEstado(estado: EstadoProjeto): string {
  switch (estado) {
    case 'ATIVO': return 'sucesso';
    case 'CONCLUIDO': return 'sucesso';
    case 'PAUSADO': return 'aviso';
    case 'ARQUIVADO': return 'perigo';
    default: return 'neutro';
  }
}

// ícones/tinta compartilhados entre atividade, notificações e feed do painel
const ICONES_TIPO: Record<string, string> = {
  PROJETO_CRIADO: 'pi-folder',
  PROJETO_ATUALIZADO: 'pi-pencil',
  PROJETO_ESTADO_ALTERADO: 'pi-flag',
  PROPRIEDADE_TRANSFERIDA: 'pi-key',
  CONVITE_ENVIADO: 'pi-user-plus',
  CONVITE_ACEITO: 'pi-user-plus',
  CONVITE_RECUSADO: 'pi-user-minus',
  CONVITE_CANCELADO: 'pi-user-minus',
  MEMBRO_ADICIONADO: 'pi-user-plus',
  MEMBRO_REMOVIDO: 'pi-user-minus',
  FUNCAO_ALTERADA: 'pi-id-card',
  COLUNA_CRIADA: 'pi-objects-column',
  COLUNA_RENOMEADA: 'pi-objects-column',
  COLUNA_REORDENADA: 'pi-sort-alt',
  COLUNA_ARQUIVADA: 'pi-inbox',
  COLUNA_REMOVIDA: 'pi-trash',
  TAREFA_CRIADA: 'pi-plus-square',
  TAREFA_ATUALIZADA: 'pi-pencil',
  TAREFA_ATRIBUIDA: 'pi-user-edit',
  TAREFA_MOVIDA: 'pi-directions',
  TAREFA_PRIORIDADE_ALTERADA: 'pi-sliders-v',
  TAREFA_PRAZO_ALTERADO: 'pi-calendar-plus',
  TAREFA_ETIQUETAS_ALTERADAS: 'pi-bookmark',
  TAREFA_ARQUIVADA: 'pi-inbox',
  TAREFA_CONCLUIDA: 'pi-check-circle',
  TAREFA_REABERTA: 'pi-replay',
  TAREFA_REMOVIDA: 'pi-trash',
  COMENTARIO_CRIADO: 'pi-comment',
  COMENTARIO_EDITADO: 'pi-comment',
  COMENTARIO_REMOVIDO: 'pi-comment',
};

/** Nome do ícone PrimeIcons coerente com o tipo de atividade. */
export function iconeAtividade(tipo: string): string {
  return ICONES_TIPO[tipo] ?? 'pi-flag';
}

/** Tinta visual do item (ok/risco/alerta/partiu). */
export function tonoAtividade(tipo: string): string {
  if (/CONCLUIDA$|ACEITO|ADICIONADO|_CRIADO$/.test(tipo)) {
    return 'ok';
  }
  if (/(REMOVID|RECUSADO|CANCELADO)/.test(tipo)) {
    return 'risco';
  }
  if (/PRAZO|ARQUIVADA|REABERTA/.test(tipo)) {
    return 'alerta';
  }
  return 'partiu';
}

/** Tinta do ícone de notificação (tipos de NotificaçãoDTO). */
export function iconeNotificacao(tipo: string): { icone: string; tono: string } {
  const mapa: Record<string, { icone: string; tono: string }> = {
    TAREFA_ATRIBUIDA: { icone: 'pi-user-edit', tono: 'partiu' },
    TAREFA_ATRIBUIDA_REMOVIDA: { icone: 'pi-user-minus', tono: 'partiu' },
    TAREFA_PRAZO_ALTERADO: { icone: 'pi-calendar-plus', tono: 'alerta' },
    TAREFA_PRIORIDADE_ALTERADA: { icone: 'pi-sliders-v', tono: 'partiu' },
    TAREFA_CONCLUIDA: { icone: 'pi-check-circle', tono: 'ok' },
    COMENTARIO_NOVO: { icone: 'pi-comment', tono: 'partiu' },
    CONVITE_RECEBIDO: { icone: 'pi-envelope', tono: 'partiu' },
    CONVITE_ACEITO: { icone: 'pi-user-plus', tono: 'ok' },
    CONVITE_RECUSADO: { icone: 'pi-user-minus', tono: 'risco' },
    MEMBRO_REMOVIDO: { icone: 'pi-user-minus', tono: 'risco' },
    TAREFA_REABERTA: { icone: 'pi-replay', tono: 'alerta' },
    PROJETO_ESTADO_ALTERADO: { icone: 'pi-flag', tono: 'partiu' },
    PROPRIEDADE_TRANSFERIDA: { icone: 'pi-key', tono: 'ok' },
  };
  return mapa[tipo] ?? { icone: 'pi-bell', tono: 'partiu' };
}

export const ROTULO_TIPO_NOTIFICACAO: Record<string, string> = {
  TAREFA_ATRIBUIDA: 'Tarefa atribuída',
  TAREFA_ATRIBUIDA_REMOVIDA: 'Atribuição removida',
  TAREFA_PRAZO_ALTERADO: 'Prazo alterado',
  TAREFA_PRIORIDADE_ALTERADA: 'Prioridade alterada',
  TAREFA_CONCLUIDA: 'Tarefa concluída',
  COMENTARIO_NOVO: 'Novo comentário',
  CONVITE_RECEBIDO: 'Convite recebido',
  CONVITE_ACEITO: 'Convite aceito',
  CONVITE_RECUSADO: 'Convite recusado',
  MEMBRO_REMOVIDO: 'Membro removido',
  TAREFA_REABERTA: 'Tarefa reaberta',
  PROJETO_ESTADO_ALTERADO: 'Estado do projeto',
  PROPRIEDADE_TRANSFERIDA: 'Propriedade transferida',
};
