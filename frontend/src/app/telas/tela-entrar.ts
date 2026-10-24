/**
 * Tela de entrada: apresentação da marca (hero) ao lado do formulário de
 * entrar/criar conta (dupla função, como o backend exige).
 */

import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router } from '@angular/router';

import { ApiServico } from '../core/api';
import { AuthStore } from '../core/auth.store';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';

const REGEX_SENHA: RegExp = /^(?=.*[A-Za-z])(?=.*\d).{8,64}$/;
const REGEX_EMAIL: RegExp = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

@Component({
  selector: 'app-tela-entrar',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule],
  template: `
    <div class="auth-tela">
      <section class="auth-hero" aria-hidden="true">
        <div class="conteudo-hero">
          <div class="marca-hero">
            <span class="logo-fluxo">
              <svg width="26" height="26" viewBox="0 0 24 24" fill="none">
                <path d="M4 7.5C7.5 4.5 10.5 4.5 14 7c3.2 2.3 6.4 2.1 6.4 2.1" stroke="#0B1120" stroke-width="2.6" stroke-linecap="round"/>
                <path d="M4 13c3.5-3 6.5-3 10-.5 3.2 2.3 6.4 2.1 6.4 2.1" stroke="#0B1120" stroke-width="2.6" stroke-linecap="round" opacity="0.72"/>
                <path d="M4 18.5c3.5-3 6.5-3 10-.5 1.6 1.2 3.2 1.8 4.6 2" stroke="#0B1120" stroke-width="2.6" stroke-linecap="round" opacity="0.45"/>
              </svg>
            </span>
            <span class="nome-fluxo">Flow<strong>Task</strong></span>
          </div>

          <h1 class="display-hero">Organize o trabalho.<br>Deixe o fluxo acontecer.</h1>
          <p class="lede-hero">
            Quadros kanban, prazos e equipes em um só lugar — pensado para grupos
            pequenos que querem acompanhar cada tarefa do início ao fim.
          </p>

          <div class="lista-beneficios">
            <div class="item-benef">
              <span class="ic-benef"><i class="pi pi-objects-column"></i></span>
              <span><b>Quadros kanban vivos</b><small>Arraste tarefas entre colunas e veja o progresso do projeto em tempo real.</small></span>
            </div>
            <div class="item-benef">
              <span class="ic-benef ok-benef"><i class="pi pi-users"></i></span>
              <span><b>Colaboração com papéis claros</b><small>Proprietário, gerente e membro — cada pessoa sabe o que pode fazer.</small></span>
            </div>
            <div class="item-benef">
              <span class="ic-benef ambar-benef"><i class="pi pi-bell"></i></span>
              <span><b>Notificações que importam</b><small>Tarefas atribuídas, prazos alterados e comentários chegam até você.</small></span>
            </div>
          </div>

          <p class="pe-hero">Feito com Angular e Spring Boot.</p>
        </div>
      </section>

      <section class="auth-caixa">
        <div class="auth-card">
          <div class="cabeca-form">
            <h2>{{ modo() === 'criar' ? 'Criar conta' : 'Entrar no FlowTask' }}</h2>
            <p>{{ modo() === 'criar' ? 'Alguns campos e a conta está pronta.' : 'Informe suas credenciais para continuar.' }}</p>
          </div>

          <div class="abas" role="tablist" aria-label="Modo de autenticação">
            <button type="button"
                    [class.ativa]="modo() === 'entrar'"
                    (click)="definirModo('entrar')"
                    role="tab" [attr.aria-selected]="modo() === 'entrar'">Entrar</button>
            <button type="button"
                    [class.ativa]="modo() === 'criar'"
                    (click)="definirModo('criar')"
                    role="tab" [attr.aria-selected]="modo() === 'criar'">Criar conta</button>
          </div>

          @if (modo() === 'criar') {
            <label class="campo">
              Nome
              <input class="input" [(ngModel)]="nome" name="nome" autocomplete="name"
                     [class.invalido]="erroCampo === 'nome'"
                     [disabled]="processando()"
                     placeholder="Como as pessoas vão te ver nos quadros">
            </label>
          }

          <label class="campo">
            E-mail
            <input class="input" [(ngModel)]="email" name="email" autocomplete="email"
                   [class.invalido]="erroCampo === 'email'"
                   [disabled]="processando()"
                   placeholder="voce@empresa.com">
          </label>

          <div class="campo-senha">
            <label class="campo">
              Senha
              <input class="input" [type]="olhinho() ? 'text' : 'password'"
                     [(ngModel)]="senha" name="senha" autocomplete="current-password"
                     [class.invalido]="erroCampo === 'senha'"
                     [disabled]="processando()"
                     placeholder="••••••••">
            </label>
            <button class="btn ghost ic sm alvo-olho" type="button"
                    (click)="olhinho.set(!olhinho())"
                    [attr.aria-label]="olhinho() ? 'Ocultar senha' : 'Mostrar senha'"
                    [title]="olhinho() ? 'Ocultar senha' : 'Mostrar senha'">
              <i class="pi" [class.pi-eye-slash]="olhinho()" [class.pi-eye]="!olhinho()"></i>
            </button>
          </div>

          @if (modo() === 'criar') {
            <p class="mensagem-info">A senha precisa ter de 8 a 64 caracteres, com pelo menos 1 letra e 1 número.</p>
          }

          @if (dica()) {
            <p class="mensagem-erro" role="alert">
              <i class="pi pi-exclamation-circle"></i> {{ dica() }}
            </p>
          }

          <button class="btn lg btn-chefe" type="button" (click)="enviar()"
                  [disabled]="processando()"
                  [class.carregando]="processando()">
            <i class="pi pi-sign-in"></i>
            {{ modo() === 'criar' ? 'Criar minha conta' : 'Entrar' }}
          </button>

          <p class="mensagem-info nota-form">
            @if (modo() === 'criar') {
              Já tem conta?
              <a href="#" (click)="$event.preventDefault(); definirModo('entrar')">Entrar</a>
            } @else {
              Primeira vez aqui?
              <a href="#" (click)="$event.preventDefault(); definirModo('criar')">Criar conta</a>
            }
          </p>
        </div>
      </section>
    </div>
  `,
  styles: `
    .ok-benef { color: var(--ft-success) !important; background: rgba(73, 198, 168, 0.1) !important;
      border-color: rgba(73, 198, 168, 0.22) !important; }
    .ambar-benef { color: var(--ft-warning) !important; background: rgba(230, 187, 115, 0.1) !important;
      border-color: rgba(230, 187, 115, 0.22) !important; }
    .campo-senha { position: relative; }
    .alvo-olho { position: absolute; right: 8px; top: 30px; color: var(--ft-text-3); }
  `,
})
export class TelaEntrar {
  private readonly api = inject(ApiServico);
  private readonly store = inject(AuthStore);
  private readonly despachador = inject(Router);
  private readonly toast = inject(ServicoToast);

  readonly modo = signal<'entrar' | 'criar'>('entrar');
  readonly processando = signal(false);
  readonly dica = signal<string | null>(null);
  readonly olhinho = signal(false);
  erroCampo: string | null = null;

  nome = '';
  email = '';
  senha = '';

  definirModo(modo: 'entrar' | 'criar'): void {
    this.modo.set(modo);
    this.dica.set(null);
    this.erroCampo = null;
  }

  enviar(): void {
    if (!this.validar()) {
      return;
    }
    this.processando.set(true);

    if (this.modo() === 'criar') {
      // registro devolve apenas os dados do usuário: completar com login automático
      this.api.cadastrar({ nome: this.nome.trim(), email: this.email.trim(), senha: this.senha }).subscribe({
        next: () => this.entrarComCredenciais('Conta criada. Bem-vindo ao FlowTask!'),
        error: erro => this.falharFormulario(erro),
      });
    } else {
      this.entrarComCredenciais(null);
    }
  }

  private entrarComCredenciais(mensagem: string | null): void {
    this.api.entrar(this.email.trim(), this.senha).subscribe({
      next: sessao => {
        this.processando.set(false);
        this.store.iniciarSessao(sessao);
        if (mensagem !== null) {
          this.toast.sucesso(mensagem);
        }
        void this.despachador.navigate(['/projetos']);
      },
      error: erro => {
        this.processando.set(false);
        if (mensagem !== null) {
          this.toast.erro(mensagemDeErro(erro));
          this.dica.set('Conta criada, mas não foi possível entrar automaticamente. Tente "Entrar".');
        } else {
          this.dica.set(mensagemDeErro(erro));
        }
      },
    });
  }

  private validar(): boolean {
    this.dica.set(null);
    this.erroCampo = null;
    if (this.modo() === 'criar' && !this.nome.trim()) {
      this.erroCampo = 'nome';
      this.dica.set('Informe seu nome.');
      return false;
    }
    if (!REGEX_EMAIL.test(this.email.trim())) {
      this.erroCampo = 'email';
      this.dica.set('Informe um e-mail válido.');
      return false;
    }
    if (!REGEX_SENHA.test(this.senha)) {
      this.erroCampo = 'senha';
      this.dica.set('A senha deve ter de 8 a 64 caracteres, com pelo menos 1 letra e 1 número.');
      return false;
    }
    return true;
  }

  private falharFormulario(erro: unknown): void {
    this.processando.set(false);
    this.dica.set(mensagemDeErro(erro));
  }
}
