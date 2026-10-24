/**
 * Fachada única para todos os recursos da API do FlowTask
 * (espelha os controllers: auth, projects, board, tasks, comments,
 * labels, invitations, members, dashboard, notifications, activities).
 */

import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import {
  Atividade, ColunaQuadro, Comentario, ContagemNaoLidas, Convite, DadosEtiqueta, DadosProjeto,
  EstadoProjeto, Etiqueta, FiltrosTarefa, MovimentoTarefa, Notificacao, Pagina, Papel, Prioridade,
  RespostaLogin, RespostaMembro, RespostaProjeto, RespostaQuadro, ResumoProjeto, ResumoUsuario,
  Tarefa, UsuarioConta,
} from './models';

const BASE = '/api';

function parametros(filtros: Record<string, unknown>): HttpParams {
  let params = new HttpParams();
  for (const [chave, valor] of Object.entries(filtros)) {
    if (valor === undefined || valor === null || valor === '') {
      continue;
    }
    params = params.set(chave, String(valor));
  }
  return params;
}

@Injectable({ providedIn: 'root' })
export class ApiServico {
  private readonly http = inject(HttpClient);

  // ------------------------------------------------------------------ auth

  cadastrar(dados: { nome: string; email: string; senha: string }): Observable<UsuarioConta> {
    return this.http.post<UsuarioConta>(`${BASE}/auth/register`, dados);
  }

  entrar(email: string, senha: string): Observable<RespostaLogin> {
    return this.http.post<RespostaLogin>(`${BASE}/auth/login`, { email, senha });
  }

  perfil(): Observable<UsuarioConta> {
    return this.http.get<UsuarioConta>(`${BASE}/auth/me`);
  }

  atualizarPerfil(nome: string): Observable<UsuarioConta> {
    return this.http.put<UsuarioConta>(`${BASE}/auth/me`, { nome });
  }

  // ------------------------------------------------------------------ projetos

  criarProjeto(dados: DadosProjeto): Observable<RespostaProjeto> {
    return this.http.post<RespostaProjeto>(`${BASE}/projects`, dados);
  }

  projetos(filtros: { busca?: string; estado?: EstadoProjeto; ordenacao?: 'ATUALIZADO' | 'NOME' | 'PRAZO' } = {})
    : Observable<RespostaProjeto[]> {
    return this.http.get<RespostaProjeto[]>(`${BASE}/projects`, { params: parametros({ ...filtros }) });
  }

  projeto(id: number): Observable<RespostaProjeto> {
    return this.http.get<RespostaProjeto>(`${BASE}/projects/${id}`);
  }

  atualizarProjeto(id: number, dados: DadosProjeto): Observable<RespostaProjeto> {
    return this.http.put<RespostaProjeto>(`${BASE}/projects/${id}`, dados);
  }

  mudarEstadoProjeto(id: number, estado: EstadoProjeto): Observable<RespostaProjeto> {
    return this.http.patch<RespostaProjeto>(`${BASE}/projects/${id}/status`, { estado });
  }

  // ------------------------------------------------------------------ quadro / colunas

  quadro(projetoId: number): Observable<RespostaQuadro> {
    return this.http.get<RespostaQuadro>(`${BASE}/projects/${projetoId}/board`);
  }

  colunas(projetoId: number): Observable<ColunaQuadro[]> {
    return this.http.get<ColunaQuadro[]>(`${BASE}/projects/${projetoId}/columns`);
  }

  criarColuna(projetoId: number, nome: string): Observable<ColunaQuadro> {
    return this.http.post<ColunaQuadro>(`${BASE}/projects/${projetoId}/columns`, { nome });
  }

  atualizarColuna(projetoId: number, colunaId: number, dados: { nome?: string; colunaConclusao?: boolean })
    : Observable<ColunaQuadro> {
    return this.http.put<ColunaQuadro>(`${BASE}/projects/${projetoId}/columns/${colunaId}`, dados);
  }

  reordenarColunas(projetoId: number, colunasIds: number[]): Observable<ColunaQuadro[]> {
    return this.http.patch<ColunaQuadro[]>(`${BASE}/projects/${projetoId}/columns/reorder`, { colunasIds });
  }

  arquivarColuna(projetoId: number, colunaId: number, ativa: boolean): Observable<ColunaQuadro> {
    return this.http.patch<ColunaQuadro>(`${BASE}/projects/${projetoId}/columns/${colunaId}/archive`, { ativa });
  }

  excluirColuna(projetoId: number, colunaId: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/projects/${projetoId}/columns/${colunaId}`);
  }

  // ------------------------------------------------------------------ tarefas

  criarTarefa(projetoId: number, dados: {
    titulo: string;
    descricao: string | null;
    colunaId: number;
    prioridade?: Prioridade;
    responsavelId?: number;
    prazo?: string;
    etiquetasIds?: number[];
  }): Observable<Tarefa> {
    return this.http.post<Tarefa>(`${BASE}/projects/${projetoId}/tasks`, dados);
  }

  tarefas(projetoId: number, filtros: FiltrosTarefa = {}): Observable<Tarefa[]> {
    return this.http.get<Tarefa[]>(`${BASE}/projects/${projetoId}/tasks`, { params: parametros({ ...filtros }) });
  }

  minhasTarefas(): Observable<Tarefa[]> {
    return this.http.get<Tarefa[]>(`${BASE}/tasks/me`);
  }

  tarefa(tarefaId: number): Observable<Tarefa> {
    return this.http.get<Tarefa>(`${BASE}/tasks/${tarefaId}`);
  }

  editarTarefa(tarefaId: number, dados: { titulo: string; descricao: string | null }): Observable<Tarefa> {
    return this.http.put<Tarefa>(`${BASE}/tasks/${tarefaId}`, dados);
  }

  moverTarefa(tarefaId: number, movimento: MovimentoTarefa): Observable<Tarefa> {
    return this.http.patch<Tarefa>(`${BASE}/tasks/${tarefaId}/move`, movimento);
  }

  atribuirTarefa(tarefaId: number, responsavelId: number | null): Observable<Tarefa> {
    return this.http.patch<Tarefa>(`${BASE}/tasks/${tarefaId}/assignee`, { responsavelId: responsavelId ?? null });
  }

  prioridadeTarefa(tarefaId: number, prioridade: Prioridade): Observable<Tarefa> {
    return this.http.patch<Tarefa>(`${BASE}/tasks/${tarefaId}/priority`, { prioridade });
  }

  prazoTarefa(tarefaId: number, prazo: string | null): Observable<Tarefa> {
    return this.http.patch<Tarefa>(`${BASE}/tasks/${tarefaId}/due-date`, { prazo: prazo ?? null });
  }

  etiquetasTarefa(tarefaId: number, etiquetasIds: number[]): Observable<Tarefa> {
    return this.http.patch<Tarefa>(`${BASE}/tasks/${tarefaId}/labels`, { etiquetasIds });
  }

  arquivarTarefa(tarefaId: number, arquivada: boolean): Observable<Tarefa> {
    return this.http.patch<Tarefa>(`${BASE}/tasks/${tarefaId}/archive`, { arquivada });
  }

  excluirTarefa(tarefaId: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/tasks/${tarefaId}`);
  }

  // ------------------------------------------------------------------ comentários

  comentarios(tarefaId: number): Observable<Comentario[]> {
    return this.http.get<Comentario[]>(`${BASE}/tasks/${tarefaId}/comments`);
  }

  comentar(tarefaId: number, conteudo: string): Observable<Comentario> {
    return this.http.post<Comentario>(`${BASE}/tasks/${tarefaId}/comments`, { conteudo });
  }

  editarComentario(comentarioId: number, conteudo: string): Observable<Comentario> {
    return this.http.put<Comentario>(`${BASE}/comments/${comentarioId}`, { conteudo });
  }

  excluirComentario(comentarioId: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/comments/${comentarioId}`);
  }

  // ------------------------------------------------------------------ etiquetas

  etiquetas(projetoId: number): Observable<Etiqueta[]> {
    return this.http.get<Etiqueta[]>(`${BASE}/projects/${projetoId}/labels`);
  }

  criarEtiqueta(projetoId: number, dados: DadosEtiqueta): Observable<Etiqueta> {
    return this.http.post<Etiqueta>(`${BASE}/projects/${projetoId}/labels`, dados);
  }

  editarEtiqueta(projetoId: number, etiquetaId: number, dados: DadosEtiqueta): Observable<Etiqueta> {
    return this.http.put<Etiqueta>(`${BASE}/projects/${projetoId}/labels/${etiquetaId}`, dados);
  }

  excluirEtiqueta(projetoId: number, etiquetaId: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/projects/${projetoId}/labels/${etiquetaId}`);
  }

  // ------------------------------------------------------------------ convites

  criarConvite(projetoId: number, dados: { email: string; funcaoProposta: Papel }): Observable<Convite> {
    return this.http.post<Convite>(`${BASE}/projects/${projetoId}/invitations`, dados);
  }

  convitesDoProjeto(projetoId: number): Observable<Convite[]> {
    return this.http.get<Convite[]>(`${BASE}/projects/${projetoId}/invitations`);
  }

  cancelarConvite(projetoId: number, conviteId: number): Observable<void> {
    return this.http.patch<void>(`${BASE}/projects/${projetoId}/invitations/${conviteId}/cancel`, {});
  }

  meusConvites(): Observable<Convite[]> {
    return this.http.get<Convite[]>(`${BASE}/invitations/me`);
  }

  aceitarConvite(conviteId: number): Observable<Convite> {
    return this.http.patch<Convite>(`${BASE}/invitations/${conviteId}/accept`, {});
  }

  recusarConvite(conviteId: number): Observable<Convite> {
    return this.http.patch<Convite>(`${BASE}/invitations/${conviteId}/decline`, {});
  }

  // ------------------------------------------------------------------ membros

  membros(projetoId: number): Observable<RespostaMembro[]> {
    return this.http.get<RespostaMembro[]>(`${BASE}/projects/${projetoId}/members`);
  }

  alterarFuncao(projetoId: number, membroId: number, funcao: Papel): Observable<RespostaMembro> {
    return this.http.patch<RespostaMembro>(`${BASE}/projects/${projetoId}/members/${membroId}/role`, { funcao });
  }

  removerMembro(projetoId: number, membroId: number): Observable<void> {
    return this.http.delete<void>(`${BASE}/projects/${projetoId}/members/${membroId}`);
  }

  transferirPropriedade(projetoId: number, membroId: number): Observable<RespostaMembro> {
    return this.alterarFuncao(projetoId, membroId, 'OWNER');
  }

  // ------------------------------------------------------------------ dashboard

  resumoDashboard(periodo: number): Observable<ResumoUsuario> {
    return this.http.get<ResumoUsuario>(`${BASE}/dashboard/summary`, { params: parametros({ periodo }) });
  }

  resumoProjetoDashboard(projetoId: number, periodo: number): Observable<ResumoProjeto> {
    return this.http.get<ResumoProjeto>(`${BASE}/projects/${projetoId}/dashboard`, { params: parametros({ periodo }) });
  }

  // ------------------------------------------------------------------ notificações

  notificacoes(filtros: { naoLidas?: boolean; page?: number; size?: number } = {}): Observable<Pagina<Notificacao>> {
    return this.http.get<Pagina<Notificacao>>(`${BASE}/notifications`, { params: parametros(filtros) });
  }

  contagemNaoLidas(): Observable<ContagemNaoLidas> {
    return this.http.get<ContagemNaoLidas>(`${BASE}/notifications/count`);
  }

  marcarLida(id: number): Observable<Notificacao> {
    return this.http.patch<Notificacao>(`${BASE}/notifications/${id}/read`, {});
  }

  marcarTodasLidas(): Observable<{ atualizadas: number }> {
    return this.http.patch<{ atualizadas: number }>(`${BASE}/notifications/read-all`, {});
  }

  // ------------------------------------------------------------------ atividades

  atividadesProjeto(projetoId: number, page = 0, size = 20): Observable<Pagina<Atividade>> {
    return this.http.get<Pagina<Atividade>>(`${BASE}/projects/${projetoId}/activities`,
      { params: parametros({ page, size }) });
  }

  atividadesTarefa(tarefaId: number, page = 0, size = 20): Observable<Pagina<Atividade>> {
    return this.http.get<Pagina<Atividade>>(`${BASE}/tasks/${tarefaId}/activities`,
      { params: parametros({ page, size }) });
  }
}
