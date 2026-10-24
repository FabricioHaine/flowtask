/** Painel do usuário: saudação, indicadores, distribuição de tarefas e atividade. */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiServico } from '../core/api';
import { AuthStore } from '../core/auth.store';
import { ResumoUsuario } from '../core/models';
import {
  ROTULO_PRIORIDADE, formatarDataHora, formatarDataLocal, iniciais,
  iconeAtividade, tonoAtividade,
} from '../core/utilidades';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';

@Component({
  selector: 'app-tela-painel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div class="cabeca-pagina">
        <div>
          <p class="rotulo-secao">Visão geral</p>
          <h1 class="titulo-pagina">{{ saudacao() }}{{ nome_() ? ', ' + nome_() : '' }}</h1>
              <span class="descricao-pagina" style="margin-top: 2px;">O resumo do seu trabalho nos últimos dias.</span>
        </div>
        <div class="acoes-cab">
          <label class="campo horizontal">
            Período
            <select class="input pequeno" [(ngModel)]="periodo" name="periodo" (ngModelChange)="carregar()"
                    style="width: 170px;">
              <option [ngValue]="7">Últimos 7 dias</option>
              <option [ngValue]="30">Últimos 30 dias</option>
              <option [ngValue]="90">Últimos 90 dias</option>
            </select>
          </label>
          <button class="btn ghost ic" type="button" (click)="carregar()"
                  title="Atualizar" aria-label="Atualizar painel">
            <i class="pi pi-refresh"></i>
          </button>
        </div>
      </div>

      @if (carregando()) {
        <div class="grade-kpi" aria-hidden="true">
          @for (f of [1, 2, 3, 4, 5]; track f) {
            <div class="kpi card">
              <div class="sk sk-linha" style="width: 70%;"></div>
              <div class="sk sk-linha gorda" style="width: 40%;"></div>
            </div>
          }
        </div>
        <div class="grade-dash">
          <div class="painel-card card">
            <div class="sk sk-linha gorda" style="width: 30%;"></div>
            @for (f of [1, 2, 3, 4]; track f) {
              <div class="sk sk-linha" style="width: 90%;"></div>
            }
          </div>
          <div class="painel-card card">
            <div class="sk sk-linha gorda" style="width: 40%;"></div>
            @for (f of [1, 2, 3, 4]; track f) {
              <div class="sk sk-linha" style="width: 85%;"></div>
            }
          </div>
        </div>
      } @else if (erroCarregamento()) {
        <div class="vazio">
          <span class="ic-vazio"><i class="pi pi-exclamation-triangle"></i></span>
          <h3>Não foi possível carregar o painel</h3>
          <p>{{ erroCarregamento() }}</p>
          <div class="acoes-vazio">
            <button class="btn soft" type="button" (click)="carregar()">
              <i class="pi pi-refresh"></i> Tentar de novo
            </button>
          </div>
        </div>
      } @else if (resumo(); as r) {
        <div class="grade-kpi">
          <div class="kpi card tono-azul">
            <div class="kpi-topo"><span class="kpi-ic"><i class="pi pi-objects-column"></i></span><small>Projetos ativos</small></div>
            <span class="kpi-valor">{{ r.projetosAtivos }}</span>
          </div>
          <div class="kpi card tono-azul">
            <div class="kpi-topo"><span class="kpi-ic"><i class="pi pi-list"></i></span><small>Tarefas abertas</small></div>
            <span class="kpi-valor">{{ r.tarefasAbertas }}</span>
          </div>
          <div class="kpi card tono-azul">
            <div class="kpi-topo"><span class="kpi-ic"><i class="pi pi-directions"></i></span><small>Em andamento</small></div>
            <span class="kpi-valor">{{ r.tarefasEmAndamento }}</span>
          </div>
          <div class="kpi card tono-vermelho">
            <div class="kpi-topo"><span class="kpi-ic"><i class="pi pi-exclamation-triangle"></i></span><small>Atrasadas</small></div>
            <span class="kpi-valor">{{ r.tarefasAtrasadas }}</span>
            <span class="kpi-nota">{{ r.tarefasAtrasadas > 0 ? 'merecem atenção hoje' : 'tudo em dia' }}</span>
          </div>
          <div class="kpi card tono-menta">
            <div class="kpi-topo"><span class="kpi-ic"><i class="pi pi-check-circle"></i></span><small>Concluídas no período</small></div>
            <span class="kpi-valor">{{ r.tarefasConcluidasPeriodo }}</span>
          </div>
        </div>

        <div class="grade-dash">
          <section class="painel-card card">
            <div class="cabeca-painel"><i class="pi pi-objects-column"></i><h2>Distribuição das tarefas</h2></div>

            <span class="rotulo-secao">Por coluna</span>
            @if (barrasColuna().length === 0) {
              <p class="mensagem-info">Crie tarefas em um projeto para ver a distribuição.</p>
            }
            @for (c of barrasColuna(); track c.nome) {
              <div class="barra-qtd">
                <span class="nome">{{ c.nome }}</span>
                <div class="trilho"><i [style.width.%]="c.pct"></i></div>
                <span class="qtd">{{ c.quantidade }}</span>
              </div>
            }

            <span class="rotulo-secao" style="margin-top: 6px;">Por prioridade</span>
            @if (barrasPrioridade().length === 0) {
              <p class="mensagem-info">Sem tarefas priorizadas no período.</p>
            }
            @for (p of barrasPrioridade(); track p.nome) {
              <div class="barra-qtd">
                <span class="nome">{{ p.nome }}</span>
                <div class="trilho" [class.ambar]="p.nome === 'Alta'" [class.menta]="p.nome === 'Baixa'"><i [style.width.%]="p.pct"></i></div>
                <span class="qtd">{{ p.valor }}</span>
              </div>
            }
          </section>

          <section class="painel-card card">
            <div class="cabeca-painel"><i class="pi pi-users"></i><h2>Carga por responsável</h2></div>
            @if (r.porResponsavel.length === 0) {
              <p class="mensagem-info">Nenhuma tarefa atribuída neste período.</p>
            }
            @for (c of r.porResponsavel; track c.usuario?.id) {
              <div class="linha-carga">
                <span class="avatar" [title]="c.usuario?.email">{{ iniciais_(c.usuario?.nome) }}</span>
                <div class="quem-carga">
                  <b>{{ c.usuario?.nome ?? 'Sem responsável' }}</b>
                  <small>{{ c.atribuidas }} abertas · {{ c.concluidas }} concluídas · {{ c.atrasadas }} atrasadas</small>
                </div>
                <span class="energia">
                  @if (c.atrasadas > 0) {
                    <span class="badge perigo">{{ c.atrasadas }} atrasadas</span>
                  } @else {
                    <span class="badge sucesso">em dia</span>
                  }
                </span>
              </div>
            }

            <div class="cabeca-painel" style="margin-top: 8px;"><i class="pi pi-calendar"></i><h2>Projetos próximos do prazo</h2></div>
            @if (r.projetosProximosDoPrazo.length === 0) {
              <p class="mensagem-info">Sem prazos nos próximos 15 dias.</p>
            }
            @for (p of r.projetosProximosDoPrazo; track p.id) {
              <div class="linha-prazo">
                <a [routerLink]="['/projetos', p.id]">{{ p.nome }}</a>
                @if (p.prazoFinal !== null) {
                  <span class="fim-linha">
                    <span class="chip-prazo"><i class="pi pi-calendar"></i> {{ dataLocal_(p.prazoFinal) }}</span>
                    @if (p.progressoPct !== null) {
                      <span class="progresso mini" [title]="p.progressoPct + '% concluído'"><span [style.width.%]="p.progressoPct"></span></span>
                    }
                  </span>
                }
              </div>
            }
          </section>

          <section class="painel-card card span-2">
            <div class="cabeca-painel"><i class="pi pi-history"></i><h2>Atividade recente</h2></div>
            @if (r.atividadeRecente.length === 0) {
              <div class="vazio" style="padding: 26px;">
                <span class="ic-vazio"><i class="pi pi-history"></i></span>
                <h3>Nada aconteceu ainda</h3>
                <p>As ações dos integrantes dos seus projetos aparecem aqui, em ordem cronológica.</p>
              </div>
            }
            @for (a of fatiaAtividade(); track a.id) {
              <div class="local-ativ">
                <span class="ativ-ic {{ tonoDe(a.tipo) }}"><i class="pi {{ iconeDe(a.tipo) }}"></i></span>
                <span class="txt-ativ">
                  <b>{{ a.usuario?.nome ?? 'Sistema' }}</b> — {{ a.resumo }}
                  @if (a.entidade === 'projeto') {
                    <a [routerLink]="['/projetos', a.entidadeId]">ver projeto</a>
                  }
                </span>
                <small class="quando">{{ datahora_(a.criadoEm) }}</small>
              </div>
            }
          </section>
        </div>
      }
    </div>
  `,
  styles: `
    .local-ativ { display: flex; gap: 10px; align-items: flex-start; padding: 6px 0;
      .txt-ativ { flex: 1; font-size: 12.5px; color: var(--ft-text-2); line-height: 1.5;
        b { color: var(--ft-text); } a { margin-left: 6px; font-size: 12px; } }
      .quando { color: var(--ft-text-3); font-size: 11px; white-space: nowrap; padding-top: 2px; } }
  `,
})
export class TelaPainel implements OnInit {
  private readonly api = inject(ApiServico);
  private readonly toast = inject(ServicoToast);
  readonly auth = inject(AuthStore);

  readonly resumo = signal<ResumoUsuario | null>(null);
  readonly carregando = signal(true);
  readonly erroCarregamento = signal<string | null>(null);

  periodo: number | null = 30;

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.erroCarregamento.set(null);
    this.api.resumoDashboard(this.periodo === null ? 30 : this.periodo).subscribe({
      next: r => {
        this.resumo.set(r);
        this.carregando.set(false);
      },
      error: erro => {
        this.carregando.set(false);
        this.erroCarregamento.set(mensagemDeErro(erro));
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  saudacao(): string {
    const hora = new Date().getHours();
    if (hora < 12) {
      return 'Bom dia';
    }
    if (hora < 18) {
      return 'Boa tarde';
    }
    return 'Boa noite';
  }

  nome_(): string | null {
    return this.auth.usuario()?.nome ?? null;
  }

  /** Primeiras atividades para a listagem compacta. */
  fatiaAtividade() {
    return this.resumo()?.atividadeRecente.slice(0, 12) ?? [];
  }

  iconeDe(tipo: string): string {
    return iconeAtividade(tipo);
  }

  tonoDe(tipo: string): string {
    return tonoAtividade(tipo);
  }

  rotuloPrioridade(chave: string): string {
    const m = ROTULO_PRIORIDADE as Record<string, string>;
    return chave in m ? m[chave] : chave;
  }

  paresDe(mapa: Record<string, number> | undefined): Array<{ chave: string; valor: number }> {
    return Object.entries(mapa ?? {}).map(([chave, valor]) => ({ chave, valor }));
  }

  /** Barras prontas para o template (sem setas em binding). */
  barrasColuna(): Array<{ nome: string; quantidade: number; pct: number }> {
    const dados = this.resumo()?.porColuna ?? [];
    const maximo = dados.reduce((m, d) => Math.max(m, d.quantidade), 1);
    return dados.map(d => ({ nome: d.nome, quantidade: d.quantidade,
      pct: Math.round((d.quantidade / maximo) * 100) }));
  }

  barrasPrioridade(): Array<{ nome: string; valor: number; pct: number }> {
    const pares = this.paresDe(this.resumo()?.porPrioridade);
    const maximo = pares.reduce((m, p) => Math.max(m, p.valor), 1);
    return pares.map(p => ({ nome: this.rotuloPrioridade(p.chave), valor: p.valor,
      pct: Math.round((p.valor / maximo) * 100) }));
  }

  iniciais_(nome: string | null | undefined): string {
    return iniciais(nome ?? null);
  }

  dataLocal_(iso: string | null): string {
    return formatarDataLocal(iso);
  }

  datahora_(iso: string | null): string {
    return formatarDataHora(iso);
  }
}
