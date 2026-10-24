import { provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { fakeAsync, TestBed, tick } from '@angular/core/testing';
import { provideRouter, Router } from '@angular/router';
import { Component } from '@angular/core';

import { AuthStore } from './auth.store';
import { autenticacaoInterceptor } from './interceptor-authenticacao';

@Component({ template: '' })
class TelaVazia { }

const OUVINTE = { next: () => undefined, error: () => undefined };

describe('Interceptor de autenticação', () => {
  let cliente: HttpClient;
  let http: HttpTestingController;
  let armazem: AuthStore;
  let roteador: Router;

  beforeEach(() => {
    sessionStorage.clear();
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([autenticacaoInterceptor])),
        provideHttpClientTesting(),
        provideRouter([{ path: 'entrar', component: TelaVazia }]),
      ],
    });
    cliente = TestBed.inject(HttpClient);
    http = TestBed.inject(HttpTestingController);
    armazem = TestBed.inject(AuthStore);
    roteador = TestBed.inject(Router);
  });

  it('anexa o cabeçalho Authorization com Bearer quando há sessão', () => {
    armazem.iniciarSessao({
      token: 'sessao-tarde',
      expiraEmSegundos: 60,
      usuario: { id: 2, nome: 'Bruno', email: 'b@f.dev', criadoEm: null },
    });

    cliente.get('/api/projects').subscribe(OUVINTE);

    const pedido = http.expectOne('/api/projects');
    expect(pedido.request.headers.get('Authorization')).toBe('Bearer sessao-tarde');
    http.verify();
  });

  it('sem sessão, não envia cabeçalho de autorização', () => {
    cliente.get('/api/projects').subscribe(OUVINTE);

    const pedido = http.expectOne('/api/projects');
    expect(pedido.request.headers.has('Authorization')).toBeFalse();
    http.verify();
  });

  it('401 em rota protegida encerra a sessão e volta para /entrar', fakeAsync(() => {
    armazem.iniciarSessao({
      token: 'velho',
      expiraEmSegundos: 60,
      usuario: { id: 3, nome: 'Cida', email: 'c@f.dev', criadoEm: null },
    });

    cliente.get('/api/tasks/me').subscribe(OUVINTE);

    const pedido = http.expectOne('/api/tasks/me');
    pedido.flush({ status: 401, mensagem: 'não autenticado' }, { status: 401, statusText: 'Unauthorized' });

    expect(armazem.estaAutenticado()).toBeFalse();
    tick();
    expect(roteador.url).toBe('/entrar');
    http.verify();
  }));

  it('401 em /api/auth/login NÃO encerra a sessão nem navega', fakeAsync(() => {
    cliente.post('/api/auth/login', {}).subscribe(OUVINTE);

    const pedido = http.expectOne('/api/auth/login');
    pedido.flush({ status: 401, mensagem: 'senha errada' }, { status: 401, statusText: 'Unauthorized' });

    tick();
    expect(armazem.estaAutenticado()).toBeFalse();
    expect(roteador.url).toBe('/');
    http.verify();
  }));
});
