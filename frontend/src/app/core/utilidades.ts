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
