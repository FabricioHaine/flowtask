/**
 * Estrutura autenticada: sidebar recolhível (ícones em telas grandes, drawer no
 * móvel), topbar com migalhas e sino, e menu de perfil no rodapé da navegação.
 */

import { ChangeDetectionStrategy, Component, HostListener, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { NavigationEnd, Router, RouterLink, RouterLinkActive, RouterOutlet } from '@angular/router';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { filter, Subject } from 'rxjs';
import { takeUntil } from 'rxjs/operators';

import { AuthStore } from '../core/auth.store';
import { ServicoSino } from '../core/sino.service';
import { ServicoToast } from '../core/toast.service';
import { iniciais } from '../core/utilidades';

interface ItemMenu {
  rota: string;
  rotulo: string;
  icone: string;
}

const CHAVE_LATERAL = 'flowtask.lateral';

@Component({
  selector: 'app-layout',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet, RouterLink, RouterLinkActive],
  template: `
    <div class="ft-shell">
      <aside class="ft-sidebar"
             [class.recolhida]="lateralRecolhida()"
             [class.aberta]="menuMovel()"
             aria-label="Navegação principal">
        <a class="ft-marca" routerLink="/projetos" (click)="fecharMenuMovel()">
          <span class="logo-fluxo" aria-hidden="true">
            <svg width="20" height="20" viewBox="0 0 24 24" fill="none">
              <path d="M4 7.5C7.5 4.5 10.5 4.5 14 7c3.2 2.3 6.4 2.1 6.4 2.1" stroke="#0B1120" stroke-width="2.6" stroke-linecap="round"/>
              <path d="M4 13c3.5-3 6.5-3 10-.5 3.2 2.3 6.4 2.1 6.4 2.1" stroke="#0B1120" stroke-width="2.6" stroke-linecap="round" opacity="0.72"/>
              <path d="M4 18.5c3.5-3 6.5-3 10-.5 1.6 1.2 3.2 1.8 4.6 2" stroke="#0B1120" stroke-width="2.6" stroke-linecap="round" opacity="0.45"/>
            </svg>
          </span>
          <span class="marca-txt">Flow<strong>Task</strong></span>
        </a>

        <nav class="ft-menu" aria-label="Espaço de trabalho">
          <span class="secao-label">Trabalho</span>
          @for (item of itensTrabalho; track item.rota) {
            <a class="ft-item" [routerLink]="item.rota" routerLinkActive="aqui"
               [title]="lateralRecolhida() ? item.rotulo : null"
               (click)="fecharMenuMovel()">
              <i class="pi {{ item.icone }}"></i><span class="rotulo">{{ item.rotulo }}</span>
            </a>
          }
        </nav>

        <nav class="ft-menu" aria-label="Pessoal">
          <span class="secao-label">Você</span>
          <a class="ft-item" routerLink="/notificacoes" routerLinkActive="aqui"
             [title]="lateralRecolhida() ? 'Notificações' : null"
             (click)="fecharMenuMovel()">
            <i class="pi pi-bell"></i><span class="rotulo">Notificações</span>
            @if (sino.naoLidas() > 0) {
              <span class="ft-sino-qtd">{{ sino.naoLidas() }}</span>
            }
          </a>
        </nav>

        <div class="ft-pie">
          <button class="ft-perfil" type="button"
                  (click)="alternarMenuPerfil($event, 'lateral')"
                  [attr.aria-expanded]="menuPerfil() === 'lateral'">
            <span class="avatar">{{ iniciais(auth.usuario()?.nome) }}</span>
            <span class="quem">
              <span class="name">{{ auth.usuario()?.nome }}</span>
              <small>{{ auth.usuario()?.email }}</small>
            </span>
            @if (menuPerfil() !== 'lateral') {
              <i class="pi pi-angle-up setinha" aria-hidden="true"></i>
            } @else {
              <i class="pi pi-angle-down setinha" aria-hidden="true"></i>
            }
          </button>

          @if (menuPerfil() === 'lateral') {
            <div class="menu-ctx ft-menu-perfil" (click)="$event.stopPropagation()">
              <div class="item-menu apenas-info">
                <i class="pi pi-user"></i>
                <span class="rotulo">
                  {{ auth.usuario()?.nome }}
                  <small class="bloco-info">{{ auth.usuario()?.email }}</small>
                </span>
              </div>
              <div class="feixe-menu"></div>
              <button class="item-menu riscado" type="button" (click)="sair()">
                <i class="pi pi-sign-out"></i><span class="rotulo">Encerrar sessão</span>
              </button>
            </div>
          }

          <button class="btn ghost ic sm recolher-btn" type="button"
                  (click)="alternarRecolhida()"
                  [attr.aria-label]="lateralRecolhida() ? 'Expandir navegação' : 'Recolher navegação'"
                  [title]="lateralRecolhida() ? 'Expandir' : 'Recolher'">
            <i class="pi" [class.pi-angle-double-right]="lateralRecolhida()" [class.pi-angle-double-left]="!lateralRecolhida()"></i>
          </button>
        </div>
      </aside>

      @if (menuMovel()) {
        <div class="ft-dos-laterais" (click)="fecharMenuMovel()" aria-hidden="true"></div>
      }

      <div class="ft-conteudo">
        <header class="ft-mainbar">
          <button class="btn ghost ic ft-volta-menu" type="button"
                  (click)="alternarMenuMovel()" aria-label="Abrir navegação">
            <i class="pi pi-bars"></i>
          </button>

          <nav class="ft-migalhas" aria-label="Localização atual">
            @for (m of migalhas(); track m.rotulo; let ultima = $last) {
              @if (m.rota && !ultima) {
                <a [routerLink]="m.rota">{{ m.rotulo }}</a><i class="pi pi-angle-right"></i>
              } @else {
                <span class="migalha-atual" [class.migalha-raiz]="!ultima">{{ m.rotulo }}</span>
                @if (!ultima) {<i class="pi pi-angle-right"></i>}
              }
            }
          </nav>

          <div class="ft-topbar-acoes">
            <a class="btn ghost ic ft-sino-btn" routerLink="/notificacoes"
               title="Notificações" aria-label="Notificações">
              <i class="pi pi-bell"></i>
              @if (sino.naoLidas() > 0) {
                <span class="ft-sino-qtd">{{ sino.naoLidas() }}</span>
              }
            </a>

            <button class="ft-perfil mini" type="button"
                    (click)="alternarMenuPerfil($event, 'topo')"
                    [attr.aria-expanded]="menuPerfil() === 'topo'"
                    title="Conta">
              <span class="avatar sm">{{ iniciais(auth.usuario()?.nome) }}</span>
            </button>

            @if (menuPerfil() === 'topo') {
              <div class="menu-ctx menu-topo" (click)="$event.stopPropagation()">
                <div class="item-menu apenas-info">
                  <i class="pi pi-user"></i>
                  <span class="rotulo">
                    {{ auth.usuario()?.nome }}
                    <small class="bloco-info">{{ auth.usuario()?.email }}</small>
                  </span>
                </div>
                <div class="feixe-menu"></div>
                <button class="item-menu riscado" type="button" (click)="sair()">
                  <i class="pi pi-sign-out"></i><span class="rotulo">Encerrar sessão</span>
                </button>
              </div>
            }
          </div>
        </header>

        <router-outlet />
      </div>
    </div>
  `,
  styles: `
    .apenas-info { cursor: default;
      &:hover { background: transparent; color: var(--ft-text-2); i.pi { color: var(--ft-text-3); } } }
    .bloco-info { display: block; color: var(--ft-text-3); font-size: 11px; font-weight: 450; }
    .ft-perfil.mini { width: 36px; height: 36px; padding: 0; justify-content: center;
      border-color: var(--ft-border-soft); background: var(--ft-elev-1); }
    .recolher-btn { margin-left: auto; color: var(--ft-text-3); }
    .menu-topo { top: calc(100% + 6px); right: 0; }
  `,
})
export class LayoutComponent implements OnInit, OnDestroy {  private readonly router = inject(Router);
  readonly auth = inject(AuthStore);
  readonly sino = inject(ServicoSino);
  private readonly toast = inject(ServicoToast);

  readonly itensTrabalho: ItemMenu[] = [
    { rota: '/projetos', rotulo: 'Projetos', icone: 'pi-objects-column' },
    { rota: '/painel', rotulo: 'Painel', icone: 'pi-chart-line' },
    { rota: '/minhas-tarefas', rotulo: 'Minhas tarefas', icone: 'pi-check-square' },
  ];

  readonly lateralRecolhida = signal(false);
  readonly menuMovel = signal(false);
  readonly menuPerfil = signal<'lateral' | 'topo' | null>(null);
  private readonly rotaAtual = signal(this.router.url);
  private readonly parar = new Subject<void>();

  constructor() {
    this.router.events
      .pipe(filter((e): e is NavigationEnd => e instanceof NavigationEnd), takeUntil(this.parar))
      .subscribe(() => this.rotaAtual.set(this.router.url));
  }

  readonly migalhas = (): { rotulo: string; rota: string | null }[] => {
    const url = this.rotaAtual();
    if (url.startsWith('/projetos/') && url.endsWith('/equipe')) {
      return [{ rotulo: 'Projetos', rota: '/projetos' }, { rotulo: 'Projeto & equipe', rota: null }];
    }
    if (url.startsWith('/projetos/')) {
      return [{ rotulo: 'Projetos', rota: '/projetos' }, { rotulo: 'Quadro', rota: null }];
    }
    const mapa: Record<string, string> = {
      '/painel': 'Painel',
      '/minhas-tarefas': 'Minhas tarefas',
      '/notificacoes': 'Notificações',
    };
    return [{ rotulo: mapa[url] ?? 'Projetos', rota: null }];
  };

  ngOnInit(): void {
    this.lateralRecolhida.set(this.lateralFoiRecolhida());
    this.sino.atualizar();
    const poll = setInterval(() => this.sino.atualizar(), 60_000);
    this.parar.subscribe(() => clearInterval(poll));
  }

  ngOnDestroy(): void {
    this.parar.next();
    this.parar.complete();
  }

  iniciais(nome: string | null | undefined): string {
    return iniciais(nome);
  }

  alternarRecolhida(): void {
    const proximo = !this.lateralRecolhida();
    this.lateralRecolhida.set(proximo);
    try {
      if (proximo) {
        localStorage.setItem(CHAVE_LATERAL, '1');
      } else {
        localStorage.removeItem(CHAVE_LATERAL);
      }
    } catch {
      // armazenamento indisponível: só o estado em memória
    }
  }

  private lateralFoiRecolhida(): boolean {
    try {
      return localStorage.getItem(CHAVE_LATERAL) === '1';
    } catch {
      return false;
    }
  }

  alternarMenuMovel(): void {
    this.menuMovel.update(aberto => !aberto);
    if (this.menuMovel()) {
      this.menuPerfil.set(null);
    }
  }

  fecharMenuMovel(): void {
    this.menuMovel.set(false);
  }

  alternarMenuPerfil(evento: MouseEvent, origem: 'lateral' | 'topo'): void {
    evento.stopPropagation();
    this.menuPerfil.update(atual => (atual === origem ? null : origem));
  }

  sair(): void {
    this.menuPerfil.set(null);
    this.auth.sair();
    void this.router.navigate(['/entrar']);
    this.toast.info('Sessão encerrada.');
  }

  @HostListener('document:click')
  fecharMenus(): void {
    this.menuPerfil.set(null);
  }

  @HostListener('document:keydown.escape')
  noEscape(): void {
    this.menuPerfil.set(null);
    this.fecharMenuMovel();
  }
}
