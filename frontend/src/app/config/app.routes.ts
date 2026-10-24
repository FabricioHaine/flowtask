/** Mapa de rotas do FlowTask. */

import { Routes } from '@angular/router';

import { authGuard, guestGuard } from '../core/guards';
import { TelaEntrar } from '../telas/tela-entrar';
import { TelaQuadro } from '../telas/tela-quadro';
import { TelaEquipe } from '../telas/tela-equipe';
import { TelaMinhasTarefas } from '../telas/tela-minhas-tarefas';
import { TelaNotificacoes } from '../telas/tela-notificacoes';
import { TelaPainel } from '../telas/tela-painel';
import { TelaProjetos } from '../telas/tela-projetos';
import { LayoutComponent } from '../layout/layout.component';

export const rotas: Routes = [
  { path: 'entrar', component: TelaEntrar, canActivate: [guestGuard] },
  {
    path: '',
    component: LayoutComponent,
    canActivate: [authGuard],
    children: [
      { path: '', pathMatch: 'full', redirectTo: 'projetos' },
      { path: 'projetos', component: TelaProjetos },
      { path: 'projetos/:id', component: TelaQuadro },
      { path: 'projetos/:id/equipe', component: TelaEquipe },
      { path: 'painel', component: TelaPainel },
      { path: 'minhas-tarefas', component: TelaMinhasTarefas },
      { path: 'notificacoes', component: TelaNotificacoes },
    ],
  },
  { path: '**', redirectTo: '' },
];
