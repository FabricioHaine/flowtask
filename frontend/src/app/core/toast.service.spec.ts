import { ServicoToast } from './toast.service';

describe('ServicoToast', () => {
  let servico: ServicoToast;

  beforeEach(() => {
    servico = new ServicoToast();
  });

  it('adiciona toasts com tipos distintos', () => {
    servico.sucesso('Tudo certo');
    servico.erro('Deu ruim');
    servico.aviso('Atenção');

    const lista = servico.toasts();
    expect(lista.length).toBe(3);
    expect(lista.map(t => t.tipo)).toEqual(['sucesso', 'erro', 'aviso']);
    expect(lista[0].texto).toBe('Tudo certo');
  });

  it('fechar remove somente o toast daquele id', () => {
    servico.sucesso('primeiro');
    servico.sucesso('segundo');

    const primeiro = servico.toasts()[0];
    servico.fechar(primeiro.id);

    const restante = servico.toasts();
    expect(restante.length).toBe(1);
    expect(restante[0].texto).toBe('segundo');
  });

  it('remove automaticamente o toast após o prazo', (done) => {
    jasmine.clock().install();
    servico.sucesso('efêmero');

    expect(servico.toasts().length).toBe(1);
    jasmine.clock().tick(5000);

    expect(servico.toasts().length).toBe(0);
    jasmine.clock().uninstall();
    done();
  });
});
