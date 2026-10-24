/** Raiz da aplicação: outlet de rotas + host dos toasts globais. */

import { ChangeDetectionStrategy, Component, inject } from '@angular/core';
import { RouterOutlet } from '@angular/router';

import { ServicoToast } from './core/toast.service';

@Component({
  selector: 'app-root',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [RouterOutlet],
  template: `
    <router-outlet />
    <div class="toasts" aria-live="polite">
      @for (t of toast.toasts(); track t.id) {
        <div class="toast"
             [class.sucesso]="t.tipo === 'sucesso'"
             [class.erro]="t.tipo === 'erro'"
             [class.aviso]="t.tipo === 'aviso'">
          <span class="txt">{{ t.texto }}</span>
          <button class="fechar" type="button" (click)="toast.fechar(t.id)" aria-label="Fechar aviso">✕</button>
        </div>
      }
    </div>
  `,
})
export class AppComponent {
  readonly toast = inject(ServicoToast);
}
