/** Centro de notificações: lista, marcar como lidas e reagir a convites recebidos. */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { RouterLink } from '@angular/router';

import { ApiServico } from '../core/api';
import { Convite, Notificacao } from '../core/models';
import { ServicoSino } from '../core/sino.service';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';
import { ROTULO_TIPO_NOTIFICACAO, formatarDataHora } from '../core/utilidades';

@Component({
  selector: 'app-tela-notificacoes',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div class="cabeca-pagina">
        <h1>Notificações</h1>
        <button class="btn ghost sm" type="button" (click)="marcarTodas()">Marcar todas como lidas</button>
      </div>

      <section class="card partNotif">
        <h2>Convites recebidos</h2>
        @if (pendentes().length === 0) {
          <p class="mensagem-info">Nenhum convite pendente.</p>
        }
        @for (c of pendentes(); track c.id) {
          <div class="linha-convite">
            <div class="quem-escreve">
              <b>{{ c.projetoNome }}</b>
              <small>
                Convite de {{ ROTULO_PAPEL_CONVITE[c.funcaoProposta] }} enviada por {{ c.criadoPor?.nome ?? '?' }}
                · expira em {{ datahora_(c.expiraEm) }}
              </small>
            </div>
            <span class="fim-linha">
              @if (c.emailCadastrado) {
                <button class="btn sm" type="button" (click)="aceitar(c)">Aceitar</button>
                <button class="btn danger sm" type="button" (click)="recusar(c)">Recusar</button>
              } @else {
                <small class="mensagem-erro">Sua conta precisa ter o e-mail {{ c.email }}.</small>
              }
            </span>
          </div>
        }
      </section>

      <section class="card partNotif">
        <h2>Tudo</h2>
        <div class="linha-filtros sem-borda">
          <label class="campo horizontal apenas-um">
            <input type="checkbox" [(ngModel)]="soNaoLidas" name="soNaoLidas" (ngModelChange)="carregar(0)">
            Somente não lidas
          </label>
        </div>
        @if (carregando()) {
          <p class="mensagem-info">Carregando…</p>
        } @else if (notificacoes().length === 0) {
          <p class="mensagem-info">Nada por aqui.</p>
        }
        @for (n of notificacoes(); track n.id) {
          <div class="linha-notificacao" [class.naolido]="!n.lida">
            <div class="quem-escreve">
              <b>{{ rotulo_(n) }}</b>
              <small>{{ n.mensagem }}</small>
              <small class="quando">{{ datahora_(n.criadoEm) }}</small>
            </div>
            <span class="fim-linha">
              @if (n.tarefaId !== null && n.projetoId !== null) {
                <a class="btn soft sm" [routerLink]="['/projetos', n.projetoId]" [queryParams]="{ tarefa: n.tarefaId }">Ver tarefa</a>
              } @else if (n.projetoId !== null) {
                <a class="btn soft sm" [routerLink]="['/projetos', n.projetoId]">Ver projeto</a>
              }
              @if (!n.lida) {
                <button class="btn ghost sm" type="button" (click)="marcarLida(n)">Lida</button>
              }
            </span>
          </div>
        }
        @if (total() > notificacoes().length) {
          <button class="btn ghost sm" type="button" (click)="mais()">Carregar mais</button>
        }
      </section>
    </div>
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

  ROTULO_PAPEL_CONVITE: Record<string, string> = { OWNER: 'posse', MANAGER: 'função gerente', MEMBER: 'função membro' };

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

  /** Rótulo mais amigável: tipo + comeso de mensagem do backend. */
  rotulo_(n: Notificacao): string {
    return ROTULO_TIPO_NOTIFICACAO[n.tipo] ?? 'Atualização';
  }

  datahora_(iso: string | null): string {
    return formatarDataHora(iso);
  }
}
