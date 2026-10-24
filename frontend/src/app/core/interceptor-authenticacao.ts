/**
 * Interceptor HTTP: anexa o token JWT e, ao receber 401 em rotas
 * protegidas, descarta a sessão e volta para a tela de entrada.
 */

import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthStore } from './auth.store';

export const autenticacaoInterceptor: HttpInterceptorFn = (req, next) => {
  const store = inject(AuthStore);
  const roteador = inject(Router);

  const autenticado = store.estaAutenticado();
  const requisicao = autenticado
    ? req.clone({ setHeaders: { Authorization: `Bearer ${store.token()}` } })
    : req;

  return next(requisicao).pipe(
    catchError((erro: HttpErrorResponse) => {
      if (erro.status === 401 && autenticado && !req.url.includes('/api/auth/')) {
        store.sair();
        void roteador.navigate(['/entrar']);
      }
      return throwError(() => erro);
    }),
  );
};
