import {
  nivelPapel, podeComentar, podeConvidar, podeEditarTarefa, podeExcluirTarefa, podeGerenciarMembros,
  podeGerenciarQuadro, podeGerenciarTarefas, temAoMenos,
} from './permissoes';

describe('Permissões (funções puras)', () => {
  it('hierarquia de papéis: OWNER (3) > MANAGER (2) > MEMBER (1) > anônimo (0)', () => {
    expect(nivelPapel('OWNER')).toBe(3);
    expect(nivelPapel('MANAGER')).toBe(2);
    expect(nivelPapel('MEMBER')).toBe(1);
    expect(nivelPapel(null)).toBe(0);
    expect(nivelPapel(undefined)).toBe(0);
  });

  it('temAoMenos: MEMBER não atinge MANAGER; OWNER atinge qualquer nível', () => {
    expect(temAoMenos('MEMBER', 'MANAGER')).toBeFalse();
    expect(temAoMenos('MANAGER', 'MANAGER')).toBeTrue();
    expect(temAoMenos('OWNER', 'MANAGER')).toBeTrue();
    expect(temAoMenos(null, 'MEMBER')).toBeFalse();
  });

  it('criar/editar tarefas: MEMBER não pode, MANAGER e OWNER podem', () => {
    expect(podeGerenciarTarefas('MEMBER')).toBeFalse();
    expect(podeGerenciarTarefas('MANAGER')).toBeTrue();
    expect(podeGerenciarTarefas('OWNER')).toBeTrue();
  });

  it('editar textos da tarefa: permitido para o próprio responsável mesmo sendo MEMBER', () => {
    const eu = 7;
    expect(podeEditarTarefa('MEMBER', eu, eu)).toBeTrue();
    expect(podeEditarTarefa('MEMBER', eu, 99)).toBeFalse();
    expect(podeEditarTarefa('MANAGER', eu, 99)).toBeTrue();
    expect(podeEditarTarefa('OWNER', eu, 99)).toBeTrue();
    expect(podeEditarTarefa(null, eu, eu)).toBeTrue();
  });

  it('colunas e membros: ação exclusiva do OWNER', () => {
    expect(podeGerenciarQuadro('OWNER')).toBeTrue();
    expect(podeGerenciarQuadro('MANAGER')).toBeFalse();
    expect(podeGerenciarQuadro('MEMBER')).toBeFalse();
    expect(podeGerenciarMembros('OWNER')).toBeTrue();
    expect(podeGerenciarMembros('MANAGER')).toBeFalse();
  });

  it('convites: MANAGER e OWNER convidam; MEMBER não', () => {
    expect(podeConvidar('OWNER')).toBeTrue();
    expect(podeConvidar('MANAGER')).toBeTrue();
    expect(podeConvidar('MEMBER')).toBeFalse();
    expect(podeConvidar(null)).toBeFalse();
  });

  it('excluir tarefa é do OWNER; comentar é de qualquer integrante', () => {
    expect(podeExcluirTarefa('OWNER')).toBeTrue();
    expect(podeExcluirTarefa('MANAGER')).toBeFalse();
    expect(podeComentar('MEMBER')).toBeTrue();
    expect(podeComentar(null)).toBeFalse();
  });
});
