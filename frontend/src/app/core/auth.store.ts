/**
 * Estado de sessão (signals) com persistência em sessionStorage:
 * ao atualizar a página, o token é restaurado automaticamente.
 */

import { Injectable, signal } from '@angular/core';
import { RespostaLogin, UsuarioConta } from './models';

const CHAVE_TOKEN = 'flowtask.token';
const CHAVE_USUARIO = 'flowtask.usuario';

@Injectable({ providedIn: 'root' })
export class AuthStore {
  readonly token = signal<string | null>(null);
  readonly usuario = signal<UsuarioConta | null>(null);

  constructor() {
    this.restaurar();
  }

  estaAutenticado(): boolean {
    return this.token() !== null;
  }

  iniciarSessao(resposta: RespostaLogin): void {
    this.token.set(resposta.token);
    this.usuario.set(resposta.usuario);
    this.persistir();
  }

  sair(): void {
    this.token.set(null);
    this.usuario.set(null);
    try {
      sessionStorage.removeItem(CHAVE_TOKEN);
      sessionStorage.removeItem(CHAVE_USUARIO);
    } catch {
      // armazenamento indisponível: apenas o estado em memória é limpo
    }
  }

  atualizarUsuario(usuario: UsuarioConta): void {
    this.usuario.set(usuario);
    this.persistir();
  }

  /** Recria a sessão a partir do sessionStorage (executado no construtor). */
  restaurar(): void {
    try {
      const token = sessionStorage.getItem(CHAVE_TOKEN);
      const bruto = sessionStorage.getItem(CHAVE_USUARIO);
      if (token && bruto) {
        this.token.set(token);
        this.usuario.set(JSON.parse(bruto) as UsuarioConta);
      }
    } catch {
      // dados corrompidos: sessão descartada
    }
  }

  private persistir(): void {
    try {
      const token = this.token();
      const usuario = this.usuario();
      if (token && usuario) {
        sessionStorage.setItem(CHAVE_TOKEN, token);
        sessionStorage.setItem(CHAVE_USUARIO, JSON.stringify(usuario));
      }
    } catch {
      // armazenamento indisponível: sessão vive apenas na memória
    }
  }
}
