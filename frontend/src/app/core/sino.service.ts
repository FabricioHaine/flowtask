/** Sino de notificações: contagem de não lidas compartilhada entre telas. */

import { Injectable, inject, signal } from '@angular/core';

import { ApiServico } from './api';

@Injectable({ providedIn: 'root' })
export class ServicoSino {
  private readonly api = inject(ApiServico);
  readonly naoLidas = signal(0);

  /** Busca a contagem atual; erros são silenciosos (poll de fundo). */
  atualizar(): void {
    this.api.contagemNaoLidas().subscribe({
      next: c => this.naoLidas.set(c.naoLidas),
      error: () => { /* silencioso */ },
    });
  }
}
