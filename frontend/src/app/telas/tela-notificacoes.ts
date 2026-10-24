/** Centro de notificações: convites em destaque, lista com ícones por tipo,
 * marcar leituras e links diretos para tarefa/projeto. */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiServico } from '../core/api';
import { Convite, Notificacao } from '../core/models';
import { ServicoSino } from '../core/sino.service';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';
import { ROTULO_TIPO_NOTIFICACAO, formatarDataHora, iconeNotificacao } from '../core/utilidades';

@Component({
  selector: 'app-tela-notificacoes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div class="cabeca-pagina">
        <div>
          <h1 class="titulo-pagina">Notificações</h1>
          <p class="descricao-pagina">Convites, atribuições e atualizações que chegam até você.</p>
        </div>
        <div class="acoes-cab">
          <button class="btn ghost" type="button" (click)="marcarTodas()">
            <i class="pi pi-check-double"></i> Marcar todas como lidas
          </button>
        </div>
      </div>

      <section class="secao-notificacoes card">
        <div class="cabeceira-not">
          <i class="pi pi-envelope"></i>
          <h2>Convites pendentes</h2>
          @if (pendentes().length > 0) {
            <span class="badge aviso">{{ pendentes().length }}</span>
          }
        </div>
        @if (pendentes().length === 0) {
          <p class="mensagem-info">Nenhum convite pendente.</p>
        }
        @for (c of pendentes(); track c.id) {
          <div class="linha-convite card-elevado">
            <span class="avatar lg">{{ iniciais_(c.projetoNome) }}</span>
            <div class="quem-escreve">
              <b>{{ c.projetoNome }}</b>
              <small>
                Proposta de {{ ROTULO_PAPEL_CONVITE[c.funcaoProposta] }} enviada por {{ c.criadoPor?.nome ?? '?' }}
                · expira em {{ datahora_(c.expiraEm) }}
              </small>
            </div>
            <span class="fim-linha">
              @if (c.emailCadastrado) {
                <button class="btn sm" type="button" (click)="aceitar(c)">
                  <i class="pi pi-check"></i> Aceitar
                </button>
                <button class="btn perigo sm" type="button" (click)="recusar(c)">
                  <i class="pi pi-times"></i> Recusar
                </button>
              } @else {
                <small class="mensagem-erro">
                  <i class="pi pi-exclamation-circle"></i>
                  Cadastre-se com o e-mail {{ c.email }} para aceitar.
                </small>
              }
            </span>
          </div>
        }
      </section>

      <section class="secao-notificacoes card">
        <div class="cabeceira-not">
          <i class="pi pi-bell"></i>
          <h2>Tudo</h2>
          <span class="esp"></span>
          <label class="apenas-um">
            <input type="checkbox" [(ngModel)]="soNaoLidas" name="soNaoLidas" (ngModelChange)="carregar(0)">
            Somente não lidas
          </label>
        </div>

        @if (carregando()) {
          <div style="display: flex; flex-direction: column; gap: 9px;" aria-hidden="true">
            @for (f of [1, 2, 3, 4]; track f) {
              <div class="sk sk-linha" style="height: 56px; border-radius: 12px;"></div>
            }
          </div>
        } @else if (notificacoes().length === 0) {
          <div class="vazio" style="padding: 30px 18px;">
            <span class="ic-vazio"><i class="pi pi-inbox"></i></span>
            <h3>@if (soNaoLidas) { Nenhuma não lida } @else { Nada por aqui }</h3>
            <p>
              @if (soNaoLidas) {
                Você já leu tudo — que ótimo sinal do seu dia.
              } @else {
                Quando algo importante acontecer nas suas tarefas e projetos, aparece aqui.
              }
            </p>
          </div>
        }
        @for (n of notificacoes(); track n.id) {
          <div class="linha-notificacao" [class.naolido]="!n.lida">
            <span class="notif-ic {{ tonoIc(n.tipo) }}">
              <i class="pi {{ icone_(n.tipo) }}"></i>
            </span>
            <div class="quem-escreve">
              <b>
                {{ rotulo_(n) }}
                @if (!n.lida) { <span class="ponto-naolido" title="Não lida"></span> }
              </b>
              <small>{{ n.mensagem }}</small>
              <small class="quando">{{ datahora_(n.criadoEm) }}</small>
            </div>
            <span class="fim-linha">
              @if (n.tarefaId !== null && n.projetoId !== null) {
                <a class="btn soft sm" [routerLink]="['/projetos', n.projetoId]" [queryParams]="{ tarefa: n.tarefaId }">
                  Ver tarefa
                </a>
              } @else if (n.projetoId !== null) {
                <a class="btn soft sm" [routerLink]="['/projetos', n.projetoId]">Ver projeto</a>
              }
              @if (!n.lida) {
                <button class="btn ghost ic sm" type="button" (click)="marcarLida(n)"
                        aria-label="Marcar como lida" title="Marcar como lida">
                  <i class="pi pi-check"></i>
                </button>
              }
            </span>
          </div>
        }
        @if (total() > notificacoes().length) {
          <button class="btn ghost" type="button" (click)="mais()">
            <i class="pi pi-angle-down"></i> Carregar mais ({{ total() - notificacoes().length }})
          </button>
        }
      </section>
    </div>
  `,
  styles: `
    .linha-convite { display: flex; align-items: center; gap: 12px; padding: 13px 14px;
      border-radius: 12px; }
  `,
})
export class TelaNotificacoes implements OnInit {
  private readonly api = inject(ApiServico);
  private readonly sino = inject(ServicoSino);
  private readonly toast = inject(ServicoToast);

  readonly notificacoes = signal<Notificacao[]>([]);
  readonly pendentes = signal<Convite[]>([]);
  readonly carregando = signal(true);

  soNaoLidas = false;
  pag = 0;
  total_ = 0;

  ROTULO_PAPEL_CONVITE: Record<string, string> = { OWNER: 'a propriedade', MANAGER: 'a função de gerente', MEMBER: 'o papel de membro' };

  ngOnInit(): void {
    this.carregar(0);
    this.pendentesCarregar();
  }

  carregar(pagina: number): void {
    this.pag = pagina;
    this.carregando.set(true);
    this.api.notificacoes({ naoLidas: this.soNaoLidas || undefined, page: pagina, size: 30 }).subscribe({
      next: pag => {
        this.total_ = pag.total;
        this.notificacoes.set(pagina === 0 ? pag.conteudo : [...this.notificacoes(), ...pag.conteudo]);
        this.carregando.set(false);
        this.sino.atualizar();
      },
      error: erro => {
        this.carregando.set(false);
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  mais(): void {
    this.carregar(this.pag + 1);
  }

  total(): number {
    return this.total_;
  }

  marcarLida(n: Notificacao): void {
    this.api.marcarLida(n.id).subscribe({
      next: () => {
        this.notificacoes.set(this.notificacoes().map(x => (x.id === n.id ? { ...x, lida: true } : x)));
        this.sino.atualizar();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  marcarTodas(): void {
    this.api.marcarTodasLidas().subscribe({
      next: res => {
        this.toast.sucesso(`${res.atualizadas} marcadas como lidas.`);
        this.notificacoes.set(this.notificacoes().map(x => ({ ...x, lida: true })));
        this.sino.atualizar();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  pendentesCarregar(): void {
    this.api.meusConvites().subscribe({
      next: cs => this.pendentes.set(cs.filter(c => c.estado === 'PENDENTE')),
      error: () => this.pendentes.set([]),
    });
  }

  aceitar(c: Convite): void {
    this.api.aceitarConvite(c.id).subscribe({
      next: () => {
        this.toast.sucesso(`Você entrou no projeto ${c.projetoNome}.`);
        this.pendentesCarregar();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  recusar(c: Convite): void {
    this.api.recusarConvite(c.id).subscribe({
      next: () => {
        this.toast.aviso('Convite recusado.');
        this.pendentesCarregar();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  icone_(tipo: string): string {
    return iconeNotificacao(tipo).icone;
  }

  tonoIc(tipo: string): string {
    return iconeNotificacao(tipo).tono;
  }

  /** Rótulo mais amigável: tipo + começo da mensagem do backend. */
  rotulo_(n: Notificacao): string {
    return ROTULO_TIPO_NOTIFICACAO[n.tipo] ?? 'Atualização';
  }

  iniciais_(nome: string | null | undefined): string {
    return nome ? nome.trim().slice(0, 2).toUpperCase() : '?';
  }

  datahora_(iso: string | null): string {
    return formatarDataHora(iso);
  }
}
