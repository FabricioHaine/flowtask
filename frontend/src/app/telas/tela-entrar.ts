/** Tela de entrada: entrar ou criar conta (dupla função, como o backend exige). */

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
    <div class="centro">
      <div class="caixa-entrar card">
        <div class="cabeca">
          <span class="logo">Fl</span>
          <h1>Flow<strong>Task</strong></h1>
          <p class="mensagem-info">Projetos, quadros kanban e equipes em um só lugar.</p>
        </div>

        <div class="abas">
          <button type="button" [class.ativa]="modo() === 'entrar'" (click)="definirModo('entrar')">Entrar</button>
          <button type="button" [class.ativa]="modo() === 'criar'" (click)="definirModo('criar')">Criar conta</button>
        </div>

        @if (modo() === 'criar') {
          <label class="campo">
            Nome completo
            <input class="input" [(ngModel)]="nome" name="nome" autocomplete="name" maxlength="120"
                   placeholder="Como você quer ser chamado" [class.erro]="erroCampo === 'nome'">
          </label>
        }

        <label class="campo">
          E-mail
          <input class="input" [(ngModel)]="email" name="email" autocomplete="email" type="email"
                 placeholder="voce@exemplo.com" [class.erro]="erroCampo === 'email'">
        </label>

        <label class="campo">
          Senha
          <input class="input" [(ngModel)]="senha" name="senha" type="password"
                 [autocomplete]="modo() === 'criar' ? 'new-password' : 'current-password'"
                 placeholder="Mínimo 8 caracteres, com letra e número"
                 [class.erro]="erroCampo === 'senha'">
        </label>

        @if (dica()) {
          <p class="mensagem-erro">{{ dica() }}</p>
        }

        <button class="btn larg1" type="button" (click)="enviar()" [disabled]="processando()">
          @if (processando()) { Enviando… } @else {
            @if (modo() === 'entrar') { Entrar } @else { Criar conta }
          }
        </button>

        @if (modo() === 'criar') {
          <p class="mensagem-info centro-txt">Precisão: 8 a 64 caracteres, com pelo menos 1 letra e 1 número.</p>
        }
      </div>
    </div>
  `,
  styles: [`
    .centro { min-height: 100vh; display: flex; align-items: center; justify-content: center; padding: 24px; }
    .caixa-entrar { width: 380px; padding: 28px; display: flex; flex-direction: column; gap: 14px; }
    .cabeca { display: flex; flex-direction: column; gap: 8px; align-items: center; text-align: center; }
    .abas { display: flex; background: var(--bg-2); border: 1px solid var(--line); border-radius: var(--radius-sm); overflow: hidden; }
    .abas button { flex: 1; background: transparent; border: none; color: var(--muted); font: inherit;
      font-weight: 600; padding: 9px 0; cursor: pointer; }
    .abas button.ativa { background: var(--bg-3); color: var(--primary-soft); }
    .larg1 { width: 100%; justify-content: center; margin-top: 2px; }
    .centro-txt { text-align: center; }
  `],
})
export class TelaEntrar {
  private readonly api = inject(ApiServico);
  private readonly store = inject(AuthStore);
  private readonly despachador = inject(Router);
  private readonly toast = inject(ServicoToast);

  readonly modo = signal<'entrar' | 'criar'>('entrar');
  readonly processando = signal(false);
  readonly dica = signal<string | null>(null);
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
