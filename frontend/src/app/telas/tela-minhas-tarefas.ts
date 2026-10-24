/** Tarefas atribuídas ao usuário em todos os projetos: central de trabalho
 * com filtros locais, prazos destacados e contagem de comentários. */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiServico } from '../core/api';
import { Prioridade, Tarefa } from '../core/models';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';
import { ROTULO_PRIORIDADE, classePrioridade, diasRestantes, formatarDataLocal } from '../core/utilidades';

@Component({
  selector: 'app-tela-minhas-tarefas',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div class="cabeca-pagina">
        <div>
          <h1 class="titulo-pagina">Minhas tarefas</h1>
          <p class="descricao-pagina">Tudo que está na bandeja das suas mãos, em todos os projetos.</p>
        </div>
        <div class="acoes-cab">
          <button class="btn ghost" type="button" (click)="carregar()">
            <i class="pi pi-refresh"></i> Atualizar
          </button>
        </div>
      </div>

      <div class="barra-filtros">
        <div class="busca">
          <i class="pi pi-search"></i>
          <input class="input" placeholder="Filtrar por título…" [(ngModel)]="filtroTexto"
                 name="filtroTexto" (ngModelChange)="remontar()">
        </div>
        <select class="input seletor" [(ngModel)]="filtroPrioridade" name="filtroPrioridade"
                (ngModelChange)="remontar()" aria-label="Filtrar por prioridade">
          <option [ngValue]="null">Todas as prioridades</option>
          @for (p of prioridades; track p) {
            <option [ngValue]="p">{{ ROTULO_PRIORIDADE_[p] }}</option>
          }
        </select>
        <label class="apenas-um">
          <input type="checkbox" [(ngModel)]="ocultarConcluidas" name="ocultarConcluidas"
                 (ngModelChange)="remontar()">
          Ocultar concluídas
        </label>
        <span class="esp"></span>
        @if (visiveis().length !== tarefas().length) {
          <span class="texto-diminuto">{{ visiveis().length }} de {{ tarefas().length }} tarefa(s)</span>
        }
      </div>

      @if (carregando()) {
        <div class="grade-tarefas-minhas" aria-hidden="true">
          @for (f of [1, 2, 3, 4, 5, 6]; track f) {
            <div class="tarefa-minha card">
              <div class="sk sk-linha gorda" style="width: 60%;"></div>
              <div class="sk sk-linha" style="width: 40%;"></div>
              <div class="sk sk-linha" style="width: 80%;"></div>
            </div>
          }
        </div>
      } @else if (erroCarregamento()) {
        <div class="vazio">
          <span class="ic-vazio"><i class="pi pi-exclamation-triangle"></i></span>
          <h3>Não foi possível carregar as tarefas</h3>
          <p>{{ erroCarregamento() }}</p>
          <div class="acoes-vazio">
            <button class="btn soft" type="button" (click)="carregar()">
              <i class="pi pi-refresh"></i> Tentar de novo
            </button>
          </div>
        </div>
      } @else if (tarefas().length === 0) {
        <div class="vazio">
          <span class="ic-vazio"><i class="pi pi-check-square"></i></span>
          <h3>Sua bandeja está vazia</h3>
          <p>Quando alguém atribuir uma tarefa à sua conta — ou você assumir uma — ela aparece aqui.</p>
          <div class="acoes-vazio">
            <a class="btn soft" routerLink="/projetos">
              <i class="pi pi-objects-column"></i> Ir para os projetos
            </a>
          </div>
        </div>
      } @else if (visiveis().length === 0) {
        <div class="vazio">
          <span class="ic-vazio"><i class="pi pi-filter-slash"></i></span>
          <h3>Nada com esses filtros</h3>
          <p>Ajuste a busca, prioridade ou opção de concluídas.</p>
          <div class="acoes-vazio">
            <button class="btn ghost" type="button" (click)="limparFiltros()">
              <i class="pi pi-filter-slash"></i> Limpar filtros
            </button>
          </div>
        </div>
      } @else {
        <div class="grade-tarefas-minhas">
          @for (t of visiveis(); track t.id) {
            <a class="tarefa-minha card" [routerLink]="['/projetos', t.projetoId]"
               [queryParams]="{ tarefa: t.id }">
              <div class="topo">
                <b>{{ t.titulo }}</b>
                @if (t.concluida) {
                  <span class="badge sucesso"><i class="pi pi-check"></i></span>
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
                <span class="de-onde" [title]="t.projetoNome + ' · ' + t.colunaNome">
                  <i class="pi pi-objects-column"></i>
                  {{ t.projetoNome }} · {{ t.colunaNome }}
                  @if (t.totalComentarios > 0) {
                    · <i class="pi pi-comment"></i> {{ t.totalComentarios }}
                  }
                </span>
                @if (t.prazo !== null) {
                  <span class="chip-prazo"
                        [class.atrasada]="vencida_(t.prazo) && !t.concluida"
                        [class.hoje]="dias_(t.prazo) === 0"
                        [class.boa]="prazinhoBoa(t.prazo) && !t.concluida">
                    <i class="pi pi-clock"></i> {{ formatar_(t.prazo) }}
                  </span>
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
  private readonly toast = inject(ServicoToast);

  readonly tarefas = signal<Tarefa[]>([]);
  readonly visiveis = signal<Tarefa[]>([]);
  readonly carregando = signal(true);
  readonly erroCarregamento = signal<string | null>(null);

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
    this.erroCarregamento.set(null);
    this.api.minhasTarefas().subscribe({
      next: lista => {
        this.tarefas.set(lista);
        this.carregando.set(false);
        this.remontar();
      },
      error: erro => {
        this.tarefas.set([]);
        this.carregando.set(false);
        this.erroCarregamento.set(mensagemDeErro(erro));
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  limparFiltros(): void {
    this.filtroTexto = '';
    this.filtroPrioridade = null;
    this.ocultarConcluidas = false;
    this.remontar();
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

  /** Prazo nos próximos 3 dias — ainda vira o chip de "boa". */
  prazinhoBoa(prazo: string): boolean {
    const dias = diasRestantes(prazo);
    return dias !== null && dias > 0 && dias <= 3;
  }

  classePrioridade_(p: Prioridade): string {
    return classePrioridade(p);
  }

  formatar_(iso: string | null): string {
    return formatarDataLocal(iso);
  }
}
