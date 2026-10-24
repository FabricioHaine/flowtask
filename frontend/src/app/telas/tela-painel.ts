/** Painel do usuário: indicadores, gráficos CSS e atividade recente. */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiServico } from '../core/api';
import { ResumoUsuario } from '../core/models';
import { ROTULO_PRIORIDADE, formatarDataHora, formatarDataLocal, iniciais } from '../core/utilidades';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';

@Component({
  selector: 'app-tela-painel',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div class="cabeca-pagina">
        <h1>Painel</h1>
        <label class="campo horizontal">
          Período
          <select class="input pequeno" [(ngModel)]="periodo" name="periodo" (ngModelChange)="carregar()">
            <option [ngValue]="7">Últimos 7 dias</option>
            <option [ngValue]="30">Últimos 30 dias</option>
            <option [ngValue]="90">Últimos 90 dias</option>
          </select>
        </label>
      </div>

      @if (carregando()) {
        <p class="mensagem-info">Calculando indicadores…</p>
      } @else if (resumo(); as r) {
        <div class="linha-indicadores">
          <div class="card indicador">
            <small>Projetos ativos</small>
            <b>{{ r.projetosAtivos }}</b>
          </div>
          <div class="card indicador">
            <small>Tarefas abertas</small>
            <b>{{ r.tarefasAbertas }}</b>
          </div>
          <div class="card indicador">
            <small>Em andamento</small>
            <b>{{ r.tarefasEmAndamento }}</b>
          </div>
          <div class="card indicador">
            <small>Atrasadas</small>
            <b class="vermelho">{{ r.tarefasAtrasadas }}</b>
          </div>
          <div class="card indicador">
            <small>Concluídas no período</small>
            <b class="verde">{{ r.tarefasConcluidasPeriodo }}</b>
          </div>
        </div>

        <div class="grade-painel">
          <section class="partPainel card">
            <h2>Por coluna</h2>
            @if (barrasColuna().length === 0) {
              <p class="mensagem-info">Sem dados.</p>
            }
            @for (c of barrasColuna(); track c.nome) {
              <div class="barra-qtd">
                <span class="nome">{{ c.nome }}</span>
                <div class="trilho"><i [style.width.%]="c.pct"></i></div>
                <span class="qtd">{{ c.quantidade }}</span>
              </div>
            }

            <h2>Por prioridade</h2>
            @if (barrasPrioridade().length === 0) {
              <p class="mensagem-info">Sem dados.</p>
            }
            @for (p of barrasPrioridade(); track p.nome) {
              <div class="barra-qtd">
                <span class="nome">{{ p.nome }}</span>
                <div class="trilho"><i [style.width.%]="p.pct"></i></div>
                <span class="qtd">{{ p.valor }}</span>
              </div>
            }
          </section>

          <section class="partPainel card">
            <h2>Carga por responsável</h2>
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
                  <span class="badge media">{{ c.atribuidas }} abertas</span>
                  @if (c.atrasadas > 0) {
                    <span class="badge perigo">{{ c.atrasadas }} atrasadas</span>
                  }
                  @if (c.concluidas > 0) {
                    <span class="badge sucesso">{{ c.concluidas }} ✓</span>
                  }
                </span>
              </div>
            }

            <h2 style="margin-top:14px">Projetos próximos do prazo</h2>
            @if (r.projetosProximosDoPrazo.length === 0) {
              <p class="mensagem-info">Sem prazos nos próximos 15 dias.</p>
            }
            @for (p of r.projetosProximosDoPrazo; track p.id) {
              <div class="linha-prazo">
                <a [routerLink]="['/projetos', p.id]">{{ p.nome }}</a>
                @if (p.prazoFinal !== null) {
                  <span class="fim-linha">
                    <small>{{ dataLocal_(p.prazoFinal) }}</small>
                    @if (p.progressoPct !== null) {
                      <span class="progresso mini"><span [style.width.%]="p.progressoPct"></span></span>
                    }
                  </span>
                }
              </div>
            }
          </section>

          <section class="partPainel card span-2">
            <h2>Atividade recente</h2>
            @if (r.atividadeRecente.length === 0) {
              <p class="mensagem-info">Nada registrado no período.</p>
            }
            @for (a of fatiaAtividade(); track a.id) {
              <p class="linha-atividade">
                <small>
                  {{ datahora_(a.criadoEm) }} · <b>{{ a.usuario?.nome ?? 'Sistema' }}</b> — {{ a.resumo }}
                  @if (a.entidade === 'projeto') {
                    <a [routerLink]="['/projetos', a.entidadeId]">ver projeto</a>
                  }
                </small>
              </p>
            }
          </section>
        </div>
      }
    </div>
  `,
})
export class TelaPainel implements OnInit {
  private readonly api = inject(ApiServico);
  private readonly toast = inject(ServicoToast);

  readonly resumo = signal<ResumoUsuario | null>(null);
  readonly carregando = signal(true);

  periodo: number | null = 30;

  ngOnInit(): void {
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.resumoDashboard(this.periodo === null ? 30 : this.periodo).subscribe({
      next: r => {
        this.resumo.set(r);
        this.carregando.set(false);
      },
      error: erro => {
        this.carregando.set(false);
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  /** Primeiras atividades para a listagem compacta. */
  fatiaAtividade() {
    return this.resumo()?.atividadeRecente.slice(0, 12) ?? [];
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
