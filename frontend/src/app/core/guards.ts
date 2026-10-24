/** Guards funcionais de rota (autenticado / somente convidado não autenticado). */

import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

import { AuthStore } from './auth.store';

export const authGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  const roteador = inject(Router);
  return store.estaAutenticado() ? true : roteador.createUrlTree(['/entrar']);
};

export const guestGuard: CanActivateFn = () => {
  const store = inject(AuthStore);
  const roteador = inject(Router);
  return store.estaAutenticado() ? roteador.createUrlTree(['/']) : true;
};
