/** Tarefas atribuídas ao usuário em todos os projetos. */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiServico } from '../core/api';
import { Prioridade, Tarefa } from '../core/models';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';
import { ROTULO_PRIORIDADE, classePrioridade, diasRestantes, formatarDataLocal, iniciais } from '../core/utilidades';

@Component({
  selector: 'app-tela-minhas-tarefas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div class="cabeca-pagina">
        <h1>Minhas tarefas</h1>
        <button class="btn ghost sm" type="button" (click)="carregar()">Atualizar</button>
      </div>

      <div class="linha-filtros card">
        <input class="input busca" placeholder="Filtrar por título…" [(ngModel)]="filtroTexto" name="filtroTexto">
        <select class="input estado" [(ngModel)]="filtroPrioridade" name="filtroPrioridade">
          <option [ngValue]="null">Todas as prioridades</option>
          @for (p of prioridades; track p) {
            <option [ngValue]="p">{{ ROTULO_PRIORIDADE_[p] }}</option>
          }
        </select>
        <label class="campo horizontal apenas-um">
          <input type="checkbox" [(ngModel)]="ocultarConcluidas" name="ocultarConcluidas" (ngModelChange)="remontar()">
          Ocultar concluídas
        </label>
      </div>

      @if (carregando()) {
        <p class="mensagem-info">Carregando tarefas…</p>
      } @else if (visiveis().length === 0) {
        <div class="vazio card">
          <h3>Nenhuma tarefa atribuída a você</h3>
          <p class="mensagem-info">Quando alguém atribuir uma tarefa à sua conta, ela aparece aqui.</p>
        </div>
      } @else {
        <div class="grade-tarefas-minhas">
          @for (t of visiveis(); track t.id) {
            <a class="card tarefa-minha" [routerLink]="['/projetos', t.projetoId]"
               [queryParams]="{ tarefa: t.id }">
              <div class="topo">
                <b>{{ t.titulo }}</b>
                @if (t.concluida) {
                  <span class="badge sucesso">✓</span>
                } @else if (t.prioridade) {
                  <span class="badge" [class]="classePrioridade_(t.prioridade)">{{ ROTULO_PRIORIDADE_[t.prioridade] }}</span>
                }
              </div>
              @if (t.etiquetas.length > 0) {
                <div class="linha-etiquetas">
                  @for (e of t.etiquetas; track e.id) {
                    <span class="pill-etiqueta"><i class="dot" [style.background]="e.cor"></i>{{ e.nome }}</span>
                  }
                </div>
              }
              <div class="meta">
                <small class="mensagem-info">{{ t.projetoNome }} · {{ t.colunaNome }} · 💬 {{ t.totalComentarios }}</small>
                @if (t.prazo !== null) {
                  <small class="prazo" [class.atrasada]="vencida_(t.prazo) && !t.concluida"
                         [class.hoje]="dias_(t.prazo) === 0">
                    {{ formatar_(t.prazo) }}
                  </small>
                }
              </div>
            </a>
          }
        </div>
      }
    </div>
  `,
})
export class TelaMinhasTarefas implements OnInit {
  private readonly api = inject(ApiServico);
  private readonly despachador = inject(Router);
  private readonly toast = inject(ServicoToast);

  readonly tarefas = signal<Tarefa[]>([]);
  readonly visiveis = signal<Tarefa[]>([]);
  readonly carregando = signal(true);

  readonly prioridades: Prioridade[] = ['BAIXA', 'MEDIA', 'ALTA', 'CRITICA'];
  ROTULO_PRIORIDADE_ = ROTULO_PRIORIDADE;

  filtroTexto = '';
  filtroPrioridade: Prioridade | null = null;
  ocultarConcluidas = false;

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.minhasTarefas().subscribe({
      next: lista => {
        this.tarefas.set(lista);
        this.carregando.set(false);
        this.remontar();
      },
      error: erro => {
        this.tarefas.set([]);
        this.carregando.set(false);
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  remontar(): void {
    const texto = this.filtroTexto.trim().toLowerCase();
    const filtradas = this.tarefas().filter(t =>
      (this.filtroPrioridade === null || t.prioridade === this.filtroPrioridade)
      && (!this.ocultarConcluidas || !t.concluida)
      && (texto === '' || t.titulo.toLowerCase().includes(texto)));
    this.visiveis.set(
      filtradas.sort((a, b) => {
        if (a.concluida !== b.concluida) {
          return a.concluida ? 1 : -1;
        }
        const pa = a.prazo ?? '9999-12-31';
        const pb = b.prazo ?? '9999-12-31';
        if (pa !== pb) {
          return pa < pb ? -1 : 1;
        }
        return a.titulo.localeCompare(b.titulo);
      }));
  }

  vencida_(prazo: string): boolean {
    return (diasRestantes(prazo) ?? 0) < 0;
  }

  dias_(prazo: string): number | null {
    return diasRestantes(prazo);
  }

  classePrioridade_(p: Prioridade): string {
    return classePrioridade(p);
  }

  formatar_(iso: string | null): string {
    return formatarDataLocal(iso);
  }
}
