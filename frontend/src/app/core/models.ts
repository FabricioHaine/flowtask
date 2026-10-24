/**
 * Espelhos em TypeScript dos DTOs do backend (com.flowtask.dto).
 * Datas chegam como strings ISO-8601.
 */

// ------------------------------------------------------------------ enums

export type Papel = 'OWNER' | 'MANAGER' | 'MEMBER';
export type Prioridade = 'BAIXA' | 'MEDIA' | 'ALTA' | 'CRITICA';
export type EstadoProjeto = 'PLANEJADO' | 'ATIVO' | 'PAUSADO' | 'CONCLUIDO' | 'ARQUIVADO';
export type EstadoConvite = 'PENDENTE' | 'ACEITO' | 'RECUSADO' | 'EXPIRADO' | 'CANCELADO';
export type TipoNotificacao =
  | 'TAREFA_ATRIBUIDA' | 'TAREFA_ATRIBUIDA_REMOVIDA' | 'TAREFA_PRAZO_ALTERADO'
  | 'TAREFA_PRIORIDADE_ALTERADA' | 'TAREFA_CONCLUIDA' | 'COMENTARIO_NOVO'
  | 'CONVITE_RECEBIDO' | 'CONVITE_ACEITO' | 'CONVITE_RECUSADO' | 'MEMBRO_REMOVIDO'
  | 'TAREFA_REABERTA' | 'PROJETO_ESTADO_ALTERADO' | 'PROPRIEDADE_TRANSFERIDA';
export type TipoAtividade =
  | 'PROJETO_CRIADO' | 'PROJETO_ATUALIZADO' | 'PROJETO_ESTADO_ALTERADO' | 'PROPRIEDADE_TRANSFERIDA'
  | 'CONVITE_ENVIADO' | 'CONVITE_ACEITO' | 'CONVITE_RECUSADO' | 'CONVITE_CANCELADO'
  | 'MEMBRO_ADICIONADO' | 'MEMBRO_REMOVIDO' | 'FUNCAO_ALTERADA'
  | 'COLUNA_CRIADA' | 'COLUNA_RENOMEADA' | 'COLUNA_REORDENADA' | 'COLUNA_ARQUIVADA' | 'COLUNA_REMOVIDA'
  | 'TAREFA_CRIADA' | 'TAREFA_ATUALIZADA' | 'TAREFA_ATRIBUIDA' | 'TAREFA_MOVIDA'
  | 'TAREFA_PRIORIDADE_ALTERADA' | 'TAREFA_PRAZO_ALTERADO' | 'TAREFA_ETIQUETAS_ALTERADAS'
  | 'TAREFA_ARQUIVADA' | 'TAREFA_CONCLUIDA' | 'TAREFA_REABERTA' | 'TAREFA_REMOVIDA'
  | 'COMENTARIO_CRIADO' | 'COMENTARIO_EDITADO' | 'COMENTARIO_REMOVIDO';

export const PAPEIS: Papel[] = ['OWNER', 'MANAGER', 'MEMBER'];
export const PRIORIDADES: Prioridade[] = ['BAIXA', 'MEDIA', 'ALTA', 'CRITICA'];
export const ESTADOS_PROJETO: EstadoProjeto[] = ['PLANEJADO', 'ATIVO', 'PAUSADO', 'CONCLUIDO', 'ARQUIVADO'];

// ------------------------------------------------------------------ auth

export interface UsuarioResumo {
  id: number | null;
  nome: string;
  email: string;
}

export interface UsuarioConta extends UsuarioResumo {
  criadoEm: string | null;
}

export interface RespostaLogin {
  token: string;
  expiraEmSegundos: number;
  usuario: UsuarioConta;
}

// ------------------------------------------------------------------ projetos

export interface RespostaProjeto {
  id: number;
  nome: string;
  descricao: string | null;
  estado: EstadoProjeto;
  dataInicio: string | null;   // LocalDate → "yyyy-MM-dd"
  prazoFinal: string | null;
  criadoEm: string | null;
  atualizadoEm: string | null;
  dono: UsuarioResumo | null;
  papelUsuario: Papel;
  totalTarefas: number | null;
  tarefasConcluidas: number | null;
  progressoPct: number | null;
  membros: UsuarioResumo[];
}

export interface RespostaMembro {
  id: number;
  usuario: UsuarioResumo;
  funcao: Papel;
  criadoEm: string | null;
}

export interface DadosProjeto {
  nome: string;
  descricao: string | null;
  dataInicio: string | null;
  prazoFinal: string | null;
}

// ------------------------------------------------------------------ quadro

export interface Etiqueta {
  id: number;
  nome: string;
  cor: string;
}

export interface TarefaQuadro {
  id: number;
  titulo: string;
  prioridade: Prioridade | null;
  prazo: string | null;
  responsavel: UsuarioResumo | null;
  etiquetas: Etiqueta[];
  totalComentarios: number;
  ordem: number;
  dataConclusao: string | null;
}

export interface ColunaQuadro {
  id: number;
  nome: string;
  ordem: number;
  ativa: boolean;
  colunaConclusao: boolean;
  tarefas: TarefaQuadro[];
}

export interface RespostaQuadro {
  projetoId: number;
  colunas: ColunaQuadro[];
}

/** Corpo do PATCH /tasks/{id}/move. */
export interface MovimentoTarefa {
  colunaId: number;
  indice: number;
}

// ------------------------------------------------------------------ tarefas

export interface Tarefa {
  id: number;
  projetoId: number;
  projetoNome: string;
  colunaId: number;
  colunaNome: string;
  titulo: string;
  descricao: string | null;
  prioridade: Prioridade | null;
  criador: UsuarioResumo | null;
  responsavel: UsuarioResumo | null;
  prazo: string | null;
  dataConclusao: string | null;
  ordem: number;
  arquivada: boolean;
  concluida: boolean;
  criadoEm: string | null;
  atualizadoEm: string | null;
  etiquetas: Etiqueta[];
  totalComentarios: number;
}

export interface FiltrosTarefa {
  busca?: string;
  responsavelId?: number;
  prioridade?: Prioridade;
  colunaId?: number;
  arquivadas?: boolean;
}

// ------------------------------------------------------------------ colaboração

export interface Comentario {
  id: number;
  autor: UsuarioResumo | null;
  conteudo: string;
  criadoEm: string | null;
  editadoEm: string | null;
}

export interface Atividade {
  id: number;
  tipo: TipoAtividade;
  entidade: string;
  entidadeId: number | null;
  resumo: string;
  dadosAnteriores: string | null;
  dadosPosteriores: string | null;
  usuario: UsuarioResumo | null;
  criadoEm: string | null;
}

export interface Notificacao {
  id: number;
  tipo: TipoNotificacao;
  mensagem: string;
  lida: boolean;
  projetoId: number | null;
  tarefaId: number | null;
  criadoEm: string | null;
}

export interface ContagemNaoLidas {
  naoLidas: number;
}

export interface DadosEtiqueta {
  nome: string;
  cor: string;
}

// ------------------------------------------------------------------ convites

export interface Convite {
  id: number;
  projetoId: number;
  projetoNome: string;
  email: string;
  funcaoProposta: Papel;
  criadoPor: UsuarioResumo | null;
  criadoEm: string | null;
  expiraEm: string | null;
  estado: EstadoConvite;
  emailCadastrado: boolean;
}

// ------------------------------------------------------------------ dashboard

export interface ContagemColuna {
  nome: string;
  quantidade: number;
}

export interface ProjetoProximoPrazo {
  id: number;
  nome: string;
  prazoFinal: string;
  progressoPct: number | null;
}

export interface TarefaProximaPrazo {
  id: number;
  titulo: string;
  prazo: string;
  responsavel: UsuarioResumo | null;
}

export interface CargaPorResponsavel {
  usuario: UsuarioResumo | null;
  atribuidas: number;
  concluidas: number;
  atrasadas: number;
}

export interface ResumoUsuario {
  projetosAtivos: number;
  tarefasAbertas: number;
  tarefasEmAndamento: number;
  tarefasConcluidasPeriodo: number;
  tarefasAtrasadas: number;
  projetosProximosDoPrazo: ProjetoProximoPrazo[];
  porPrioridade: Record<string, number>;
  porColuna: ContagemColuna[];
  porResponsavel: CargaPorResponsavel[];
  atividadeRecente: Atividade[];
}

export interface ResumoProjeto {
  projetoId: number;
  totalTarefas: number;
  porColuna: ContagemColuna[];
  porPrioridade: Record<string, number>;
  atrasadas: number;
  concluidasPeriodo: number;
  progressoPct: number | null;
  proximasDoPrazo: TarefaProximaPrazo[];
  cargaPorMembro: CargaPorResponsavel[];
  atividadeRecente: Atividade[];
}

// ------------------------------------------------------------------ paginação

export interface Pagina<T> {
  conteudo: T[];
  pagina: number;
  tamanho: number;
  total: number;
}

/** Corpo padrão de erro da API (ErroResposta). */
export interface ErrRes {
  status: number;
  mensagens?: string;
  erros?: Record<string, string>;
}
