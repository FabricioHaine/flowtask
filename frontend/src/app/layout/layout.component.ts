/** Estrutura autenticada: sidebar (projetos/painel/minhas tarefas), sino com badge, perfil e sair. */

import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject } from '@angular/core';
import { Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';

import { AuthStore } from '../core/auth.store';
import { ServicoSino } from '../core/sino.service';
import { iniciais } from '../core/utilidades';

@Component({
  selector: 'app-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="shell">
      <aside class="lateral">
        <a class="marca" routerLink="/projetos">
          <span class="logo">Fl</span>
          <span class="marca-txt">Flow<strong>Task</strong></span>
        </a>

        <nav class="menu">
          <a routerLink="/projetos" routerLinkActive="aqui"><span class="ic"><i class="pi pi-objects-column"></i></span> Projetos</a>
          <a routerLink="/painel" routerLinkActive="aqui"><span class="ic"><i class="pi pi-chart-bar"></i></span> Painel</a>
          <a routerLink="/minhas-tarefas" routerLinkActive="aqui"><span class="ic"><i class="pi pi-check-square"></i></span> Minhas tarefas</a>
        </nav>

        <div class="pie">
          <a routerLink="/notificacoes" routerLinkActive="aqui" class="sino" title="Notificações">
            <span class="ic"><i class="pi pi-bell"></i></span> Notificações
            @if (sino.naoLidas() > 0) {
              <span class="vponto">{{ sino.naoLidas() }}</span>
            }
          </a>

          @if (conta(); as usuario) {
            <div class="perfil">
              <span class="avatar" [title]="usuario.email">{{ iniciaisDe(usuario.nome) }}</span>
              <span class="quem">
                <b class="name">{{ usuario.nome }}</b>
                <small>{{ usuario.email }}</small>
              </span>
              <button class="btn ghost sm" type="button" (click)="sair()" title="Encerrar sessão">Sair</button>
            </div>
          }
        </div>
      </aside>

      <main class="conteudo">
        <router-outlet />
      </main>
    </div>
  `,
})
export class LayoutComponent implements OnInit, OnDestroy {
  private readonly despachador = inject(Router);
  private readonly store = inject(AuthStore);
  readonly sino = inject(ServicoSino);
  private temporizador?: ReturnType<typeof setInterval>;

  readonly conta = () => this.store.usuario();

  ngOnInit(): void {
    this.sino.atualizar();
    this.temporizador = setInterval(() => this.sino.atualizar(), 30_000);
  }

  ngOnDestroy(): void {
    if (this.temporizador !== undefined) {
      clearInterval(this.temporizador);
    }
  }

  iniciaisDe(nome: string | null): string {
    return iniciais(nome);
  }

  sair(): void {
    this.store.sair();
    void this.despachador.navigate(['/entrar']);
  }
}
