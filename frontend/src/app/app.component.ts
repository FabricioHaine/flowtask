/** Raiz da aplicação: outlet de rotas + host dos toasts globais (com ícones). */

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
             [class.aviso]="t.tipo === 'aviso'"
             [class.info]="t.tipo === 'info'">
          <i class="pi"
             [class.pi-check-circle]="t.tipo === 'sucesso'"
             [class.pi-times-circle]="t.tipo === 'erro'"
             [class.pi-exclamation-triangle]="t.tipo === 'aviso'"
             [class.pi-info-circle]="t.tipo === 'info'"
             aria-hidden="true"></i>
          <span class="txt">{{ t.texto }}</span>
          <button class="fechar" type="button" (click)="toast.fechar(t.id)" aria-label="Fechar aviso">
            <i class="pi pi-times"></i>
          </button>
        </div>
      }
    </div>
  `,
})
export class AppComponent {
  readonly toast = inject(ServicoToast);
}
