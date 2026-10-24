import { AuthStore } from './auth.store';

const RESPOSTA = {
  token: 'jwt-de-teste',
  expiraEmSegundos: 3600,
  usuario: {
    id: 1,
    nome: 'Ana Duarte',
    email: 'ana@flowtask.dev',
    criadoEm: '2026-10-23T10:00:00',
  },
};

describe('AuthStore', () => {
  let store: AuthStore;

  beforeEach(() => {
    sessionStorage.clear();
    store = new AuthStore();
  });

  it('inicia sem sessão (não autenticado)', () => {
    expect(store.estaAutenticado()).toBeFalse();
    expect(store.token()).toBeNull();
    expect(store.usuario()).toBeNull();
  });

  it('guarda token e usuário ao iniciar a sessão e persiste no sessionStorage', () => {
    store.iniciarSessao(RESPOSTA);

    expect(store.estaAutenticado()).toBeTrue();
    expect(store.token()).toBe('jwt-de-teste');
    expect(store.usuario()!.id).toBe(1);
    expect(store.usuario()!.nome).toBe('Ana Duarte');

    expect(sessionStorage.getItem('flowtask.token')).toBe('jwt-de-teste');
    expect(sessionStorage.getItem('flowtask.usuario')).toContain('ana@flowtask.dev');
  });

  it('restaura a sessão do sessionStorage ao ser criado', () => {
    store.iniciarSessao(RESPOSTA);
    const restaurado = new AuthStore();

    expect(restaurado.estaAutenticado()).toBeTrue();
    expect(restaurado.token()).toBe('jwt-de-teste');
    expect(restaurado.usuario()!.email).toBe('ana@flowtask.dev');
  });

  it('sair limpa o estado e o armazenamento', () => {
    store.iniciarSessao(RESPOSTA);
    store.sair();

    expect(store.estaAutenticado()).toBeFalse();
    expect(tokenNulo());
    expect(store.usuario()).toBeNull();
    expect(sessionStorage.getItem('flowtask.token')).toBeNull();
    expect(sessionStorage.getItem('flowtask.usuario')).toBeNull();

    function tokenNulo() {
      return store.token() === null;
    }
  });

  it('atualizarUsuario substitui o nome da conta mantendo o token', () => {
    store.iniciarSessao(RESPOSTA);
    store.atualizarUsuario({ ...RESPOSTA.usuario, nome: 'Ana Duarte Rios' });

    expect(store.usuario()!.nome).toBe('Ana Duarte Rios');
    expect(store.token()).toBe('jwt-de-teste');
    expect(sessionStorage.getItem('flowtask.usuario')).toContain('Ana Duarte Rios');
  });

  it('restaura com dados corrompidos com usuário nulo em vez de falhar', () => {
    sessionStorage.setItem('flowtask.token', 'x');
    sessionStorage.setItem('flowtask.usuario', 'não é json');
    const recuperado = new AuthStore();

    expect(recuperado.estaAutenticado()).toBeTrue();
    expect(recuperado.usuario()).toBeNull();
  });
});
