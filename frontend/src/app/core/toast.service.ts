/** Toasts globais (aparecem no canto inferior direito, estilizados em styles.scss). */

import { Injectable, signal } from '@angular/core';

export type TipoToast = 'sucesso' | 'erro' | 'aviso' | 'info';

export interface Toast {
  id: number;
  texto: string;
  tipo: TipoToast;
}

@Injectable({ providedIn: 'root' })
export class ServicoToast {
  private seq = 0;
  readonly toasts = signal<Toast[]>([]);
  private readonly DURACAO_MS = 4500;

  sucesso(texto: string): void {
    this.adicionar('sucesso', texto);
  }

  erro(texto: string): void {
    this.adicionar('erro', texto);
  }

  aviso(texto: string): void {
    this.adicionar('aviso', texto);
  }

  info(texto: string): void {
    this.adicionar('info', texto);
  }

  private adicionar(tipo: TipoToast, texto: string): void {
    const item: Toast = { id: ++this.seq, texto, tipo };
    this.toasts.update(atual => [...atual, item]);
    if (typeof setTimeout === 'function') {
      setTimeout(() => this.fechar(item.id), this.DURACAO_MS);
    }
  }

  fechar(id: number): void {
    this.toasts.update(atual => atual.filter(t => t.id !== id));
  }
}

/**
 * Extrai a mensagem do corpo ErroResposta do backend
 * ({status, mensagem, erros}) para exibir em toast.
 */
export function mensagemDeErro(erro: unknown): string {
  const corpo = (erro as { error?: unknown })?.error;
  if (corpo && typeof corpo === 'object' && 'mensagem' in (corpo as object)) {
    const r = corpo as { mensagem?: unknown; erros?: Record<string, string> | null };
    if (typeof (corpo as object & { mensagem?: unknown }).mensagem === 'string') {
      const msg = (corpo as { mensagem: string }).mensagem;
      const det = r.erros ? Object.values(r.erros).join(' ') : '';
      return det ? `${msg} ${det}` : msg;
    }
  }
  return (erro as { status?: number })?.status === 0
    ? 'Não foi possível falar com o servidor. Verifique se a API está em execução.'
    : 'Ocorreu um erro inesperado.';
}
