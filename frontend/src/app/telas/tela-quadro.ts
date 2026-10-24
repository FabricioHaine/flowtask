/** Quadro kanban: colunas, cartões arrastáveis, criação de tarefas e gaveta de detalhes. */

import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiServico } from '../core/api';
import { AuthStore } from '../core/auth.store';
import {
  Atividade, ColunaQuadro, Comentario, Etiqueta, Prioridade, RespostaProjeto, Tarefa,
  PRIORIDADES,
} from '../core/models';
import {
  ROTULO_PRIORIDADE, classePrioridade, diasRestantes, formatarDataHora, iniciais,
} from '../core/utilidades';
import { podeEditarTarefa, podeExcluirTarefa, podeGerenciarTarefas } from '../core/permissoes';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';

@Component({
  selector: 'app-tela-quadro',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, CdkDropList, CdkDropListGroup, CdkDrag],
  template: `
    <div class="pagina quadro-pag">
      <div class="cabeca-pagina">
        <div>
          <h1>@if (projeto(); as p) { {{ p.nome }} } @else { Quadro }</h1>
          @if (projeto(); as p) {
            <p class="mensagem-info">
              @if (p.descricao) { {{ p.descricao }} — } {{ p.tarefasConcluidas ?? 0 }}/{{ p.totalTarefas ?? 0 }} concluídas
              @if (p.prazoFinal) { · prazo {{ dataLocal_(p.prazoFinal) }} }
            </p>
          }
        </div>
        <div class="fim-cabeca">
          <a class="btn ghost sm" [routerLink]="['/projetos', projetoId, 'equipe']">Projeto & equipe</a>
          <button class="btn ghost sm" type="button" (click)="carregar()">Atualizar</button>
        </div>
      </div>

      @if (carregando()) {
        <p class="mensagem-info">Carregando quadro…</p>
      } @else {
        <div class="quadro-h" cdkDropListGroup>
          @for (coluna of colunas(); track coluna.id) {
            <section class="coluna card">
              <header class="coluna-cabeca">
                <h3>{{ coluna.nome }}</h3>
                <span class="badge neutro">{{ coluna.tarefas.length }}</span>
                @if (coluna.colunaConclusao) {
                  <span class="badge sucesso" title="Coluna de conclusão">✓</span>
                }
              </header>

              <div class="corpo-coluna" cdkDropList [cdkDropListData]="coluna" (cdkDropListDropped)="soltar($event)">
                @for (t of coluna.tarefas; track t.id) {
                  <article class="cartao" cdkDrag (click)="abrirGaveta(t.id)">
                    <b class="titulo">{{ t.titulo }}</b>
                    @if (t.etiquetas.length > 0) {
                      <div class="linha-etiquetas">
                        @for (e of t.etiquetas; track e.id) {
                          <span class="pill-etiqueta"><i class="dot" [style.background]="e.cor"></i>{{ e.nome }}</span>
                        }
                      </div>
                    }
                    <div class="linha-cartao">
                      @if (t.prioridade) {
                        <span class="badge" [class]="classePrioridade_(t.prioridade)">{{ ROTULO_PRIORIDADE[t.prioridade] }}</span>
                      }
                      @if (t.prazo !== null) {
                        <span class="prazo" [class.atrasada]="vencida_(t.prazo) && t.dataConclusao === null"
                              [class.hoje]="dias_(t.prazo) === 0">⏰ {{ dataLocal_(t.prazo) }}</span>
                      }
                      @if (t.totalComentarios > 0) {
                        <span class="fino">💬 {{ t.totalComentarios }}</span>
                      }
                      @if (t.responsavel) {
                        <span class="avatar micro" [title]="t.responsavel.nome">{{ iniciais_(t.responsavel.nome) }}</span>
                      }
                    </div>
                  </article>
                }
              </div>

              <footer class="pie-coluna">
                <button class="btn soft sm largura" type="button" (click)="iniciarCriacao(coluna)">＋ Tarefa</button>
              </footer>
            </section>
          }

          @if (podeQuadro()) {
            <section class="coluna-fim">
              <button class="btn ghost largura" type="button" (click)="criarColunaPrompt()">＋ Nova coluna</button>
            </section>
          }
        </div>
      }
    </div>

    <!-- modal de criação de tarefa -->
    @if (colunaNova(); as alvo) {
      <div class="sombra" (click)="cancelarCriacao()">
        <div class="modal card" (click)="$event.stopPropagation()">
          <h2>Nova tarefa — {{ alvo.nome }}</h2>
          <label class="campo">
            Título
            <input class="input" [(ngModel)]="novoTitulo" name="novoTitulo" maxlength="160"
                   placeholder="O que precisa ser feito?">
          </label>
          <label class="campo">
            Descrição
            <textarea class="input" [(ngModel)]="novaDescricao" name="novaDescricao" rows="3" maxlength="4000"></textarea>
          </label>
          <div class="par">
            <label class="campo">
              Prioridade
              <select class="input" [(ngModel)]="novaPrioridade" name="novaPrioridade">
                <option [ngValue]="null">Sem prioridade</option>
                @for (p of prioridades; track p) {
                  <option [ngValue]="p">{{ ROTULO_PRIORIDADE[p] }}</option>
                }
              </select>
            </label>
            <label class="campo">
              Prazo
              <input class="input" type="date" [(ngModel)]="novoPrazo" name="novoPrazo">
            </label>
          </div>
          @if (podeGestao()) {
            <label class="campo">
              Responsável
              <select class="input" [(ngModel)]="novoResponsavel" name="novoResponsavel">
                <option [ngValue]="null">Sem responsável</option>
                @for (m of projeto()?.membros ?? []; track m.id) {
                  <option [ngValue]="m.id">{{ m.nome }}</option>
                }
              </select>
            </label>
          }
          @if (erro()) {
            <p class="mensagem-erro">{{ erro() }}</p>
          }
          <div class="acoes">
            <button class="btn ghost" type="button" (click)="cancelarCriacao()">Cancelar</button>
            <button class="btn" type="button" (click)="criarTarefa()" [disabled]="salvando()">Criar tarefa</button>
          </div>
        </div>
      </div>
    }

    <!-- gaveta de detalhe da tarefa -->
    @if (aberta()) {
      <div class="vao-gaveta" (click)="fecharGaveta()">
        <div class="gaveta card" (click)="$event.stopPropagation()">
          @if (tarefa(); as t) {
            <header class="gaveta-cab">
              <input class="gaveta-titulo" [(ngModel)]="tituloEditado" name="tituloEditado" maxlength="160"
                     [disabled]="!podeTextos(t)">
              <button class="btn ghost sm" type="button" (click)="fecharGaveta()" aria-label="Fechar">✕</button>
            </header>

            <div class="gaveta-corpo">
              <label class="campo">
                Descrição
                <textarea class="input" [(ngModel)]="descricaoEditada" name="descricaoEditada" rows="5" maxlength="4000"
                          [disabled]="!podeTextos(t)"
                          placeholder="Detalhe a tarefa (opcional)"></textarea>
              </label>

              <div class="par">
                <label class="campo">
                  Prioridade
                  <select class="input" [(ngModel)]="grafPrioridade" name="grafPrioridade"
                          (ngModelChange)="trocarPrioridade(t, $event)" [disabled]="!podeGestao()">
                    @if (!podeGestao()) {
                      <option [ngValue]="null"></option>
                    } @else {
                      <option [ngValue]="null">Sem prioridade</option>
                    }
                    @for (p of prioridades; track p) {
                      <option [ngValue]="p">{{ ROTULO_PRIORIDADE[p] }}</option>
                    }
                  </select>
                </label>
                <label class="campo">
                  Prazo
                  <input class="input" type="date" [(ngModel)]="grafPrazo" name="grafPrazo"
                         (ngModelChange)="trocarPrazo(t, $event)" [disabled]="!podeGestao()">
                </label>
              </div>

              @if (podeGestao()) {
                <label class="campo">
                  Responsável
                  <select class="input" [(ngModel)]="grafResponsavel" name="grafResponsavel"
                          (ngModelChange)="trocarResponsavel(t, $event)">
                    <option [ngValue]="null">Sem responsável</option>
                    @for (m of projeto()?.membros ?? []; track m.id) {
                      <option [ngValue]="m.id">{{ m.nome }}</option>
                    }
                  </select>
                </label>
              } @else if (t.responsavel) {
                <label class="campo">Responsável<input class="input" [ngModel]="t.responsavel.nome" name="soLeitura" [ngModelOptions]="{standalone: true}" [disabled]="true"></label>
              }

              <label class="campo">
                Etiquetas
                @if (etiquetas().length === 0) {
                  <small class="mensagem-info">Nenhuma etiqueta no projeto — crie em "Projeto & equipe".</small>
                } @else {
                  <div class="grade-etiquetas">
                    @for (e of etiquetas(); track e.id) {
                      <button type="button" class="pill-etiqueta escolhida"
                              [class.acesa]="idsEtiquetas().has(e.id)"
                              (click)="alternarEtiqueta(e)">
                        <i class="dot" [style.background]="e.cor"></i>{{ e.nome }}
                      </button>
                    }
                  </div>
                }
                @if (podeEtiquetas(t) && idsEtiquetasMudou()) {
                  <button class="btn soft sm" type="button" (click)="salvarEtiquetas(t)">Salvar etiquetas</button>
                }
              </label>

              <section class="parte-gaveta">
                <h3>Comentários</h3>
                @if (comentarios().length === 0) {
                  <p class="mensagem-info">Nenhum comentário ainda.</p>
                }
                @for (c of comentarios(); track c.id) {
                  <div class="comentario">
                    <span class="avatar" [title]="c.autor?.nome">{{ iniciais_(c.autor?.nome) }}</span>
                    <div class="corpo-com">
                      <b>{{ c.autor?.nome ?? '?' }}</b>
                      <small> · {{ datahora_(c.criadoEm) }}@if (c.editadoEm) { · editado }</small>
                      @if (editandoCom() === c.id) {
                        <textarea class="input" [(ngModel)]="textoComentario" name="textoComentario" rows="2"></textarea>
                        <div class="acoes">
                          <button class="btn sm" type="button" (click)="salvarComentario(c.id)">Salvar</button>
                          <button class="btn ghost sm" type="button" (click)="cancelarEdicaoCom()">Cancelar</button>
                        </div>
                      } @else {
                        <p>{{ c.conteudo }}</p>
                      }
                    </div>
                    @if (podeGerirComentario(c)) {
                      <div class="acoes-mini">
                        @if (c.autor?.id === meuId()) {
                          <button class="gv" type="button" (click)="iniciarEdicaoCom(c)">editar</button>
                        }
                        <button class="gv" type="button" (click)="excluirComentario(c.id)">excluir</button>
                      </div>
                    }
                  </div>
                }
                <div class="novo-com">
                  <textarea class="input" [(ngModel)]="novoComentario" name="novoComentario" rows="2"
                            placeholder="Escreva um comentário…"></textarea>
                  <button class="btn sm" type="button" (click)="comentar()">Comentar</button>
                </div>
              </section>

              @if (historico().length > 0) {
                <section class="parte-gaveta">
                  <h3>Atividades desta tarefa</h3>
                  @for (a of historico(); track a.id) {
                    <p class="linha-atividade">
                      <small>{{ datahora_(a.criadoEm) }} · <b>{{ a.usuario?.nome ?? '?' }}</b> — {{ a.resumo }}</small>
                    </p>
                  }
                </section>
              }
            </div>

            <footer class="gaveta-pie">
              @if (podeTextos(t)) {
                <button class="btn" type="button" (click)="salvarTextos(t)" [disabled]="!textoMudou(t)">Salvar alterações</button>
              }
              @if (t.concluida) {
                <span class="badge sucesso">Concluída</span>
              }
              <span class="esp"></span>
              @if (podeTextos(t)) {
                <button class="btn soft sm" type="button" (click)="arquivar(t)">
                  {{ t.arquivada ? 'Restaurar' : 'Arquivar' }}
                </button>
              }
              @if (podeApagar()) {
                <button class="btn danger sm" type="button" (click)="excluir(t)">Excluir</button>
              }
            </footer>
          }
        </div>
      </div>
    }
  `,
})
export class TelaQuadro implements OnInit {
  private readonly api = inject(ApiServico);
  private readonly rota = inject(ActivatedRoute);
  private readonly store = inject(AuthStore);
  private readonly toast = inject(ServicoToast);

  readonly projeto = signal<RespostaProjeto | null>(null);
  readonly colunas = signal<ColunaQuadro[]>([]);
  readonly carregando = signal(true);
  readonly salvando = signal(false);
  readonly erro = signal<string | null>(null);

  // gaveta
  readonly aberta = signal(false);
  readonly tarefa = signal<Tarefa | null>(null);
  readonly comentarios = signal<Comentario[]>([]);
  readonly etiquetas = signal<Etiqueta[]>([]);
  readonly historico = signal<Atividade[]>([]);
  readonly editandoCom = signal<number | null>(null);
  private idsEtiquetasSalvos = new Set<number>();

  // modal de criação
  readonly colunaNova = signal<ColunaQuadro | null>(null);

  // formulários
  novoTitulo = '';
  novaDescricao = '';
  novaPrioridade: Prioridade | null = null;
  novoPrazo = '';
  novoResponsavel: number | null = null;

  tituloEditado = '';
  descricaoEditada = '';
  grafPrioridade: Prioridade | null = null;
  grafPrazo = '';
  grafResponsavel: number | null = null;
  textoComentario = '';
  novoComentario = '';

  prioridades: Prioridade[] = PRIORIDADES;
  ROTULO_PRIORIDADE = ROTULO_PRIORIDADE;

  projetoId = 0;

  ngOnInit(): void {
    this.projetoId = Number(this.rota.snapshot.paramMap.get('id'));
    this.carregar();
    const alvo = this.rota.snapshot.queryParamMap.get('tarefa');
    if (alvo !== null && alvo.trim() !== '') {
      const tarefaId = Number(alvo);
      if (!Number.isNaN(tarefaId) && tarefaId > 0) {
        this.abrirGaveta(tarefaId);
      }
    }
  }

  /** Recarrega projeto + quadro + etiquetas. */
  carregar(silencioso = false): void {
    if (!silencioso) {
      this.carregando.set(true);
    }
    this.api.projeto(this.projetoId).subscribe({
      next: p => this.projeto.set(p),
      error: () => {
        /* erros de quadro abaixo são os visíveis */
      },
    });
    this.api.quadro(this.projetoId).subscribe({
      next: q => {
        this.colunas.set(q.colunas);
        this.carregando.set(false);
      },
      error: erro => {
        this.colunas.set([]);
        this.carregando.set(false);
        this.toast.erro(mensagemDeErro(erro));
      },
    });
    this.api.etiquetas(this.projetoId).subscribe({
      next: es => this.etiquetas.set(es),
      error: () => this.etiquetas.set([]),
    });
  }

  // ------------------------------------------------------------ drag & drop

  soltar(acao: CdkDragDrop<ColunaQuadro>): void {
    const origem = acao.previousContainer.data;
    const destino = acao.container.data;
    const tarefa = origem.tarefas[acao.previousIndex];
    if (tarefa === undefined) {
      return;
    }
    if (origem === destino) {
      origem.tarefas.splice(acao.previousIndex, 1);
      origem.tarefas.splice(acao.currentIndex, 0, tarefa);
    } else {
      origem.tarefas.splice(acao.previousIndex, 1);
      destino.tarefas.splice(acao.currentIndex, 0, tarefa);
    }
    this.colunas.set([...this.colunas()]);

    this.api.moverTarefa(tarefa.id, { colunaId: destino.id, indice: acao.currentIndex }).subscribe({
      next: () => this.carregar(true),
      error: () => {
        this.toast.erro('Não foi possível mover a tarefa.');
        this.carregar();
      },
    });
  }

  // ------------------------------------------------------------ criação de tarefa

  iniciarCriacao(coluna: ColunaQuadro): void {
    if (!podeGerenciarTarefas(this.papel())) {
      this.toast.aviso('Somente Gerente ou Proprietário podem criar tarefas.');
      return;
    }
    this.colunaNova.set(coluna);
    this.novoTitulo = '';
    this.novaDescricao = '';
    this.novaPrioridade = null;
    this.novoPrazo = '';
    this.novoResponsavel = null;
    this.erro.set(null);
  }

  cancelarCriacao(): void {
    this.colunaNova.set(null);
  }

  criarColunaPrompt(): void {
    const bruto = window.prompt('Nome da nova coluna (2 a 80 caracteres):');
    if (bruto === null) {
      return;
    }
    const nome = bruto.trim();
    if (nome.length < 2) {
      this.toast.erro('O nome da coluna deve ter de 2 a 80 caracteres.');
      return;
    }
    this.api.criarColuna(this.projetoId, nome).subscribe({
      next: () => {
        this.toast.sucesso('Coluna criada.');
        this.carregar();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  criarTarefa(): void {
    const alvo = this.colunaNova();
    if (alvo === null) {
      return;
    }
    const titulo = this.novoTitulo.trim();
    if (titulo.length < 3) {
      this.erro.set('O título deve ter de 3 a 160 caracteres.');
      return;
    }
    this.salvando.set(true);
    const responsavelId = podeGerenciarTarefas(this.papel()) ? this.novoResponsavel : null;
    this.api.criarTarefa(this.projetoId, {
      titulo,
      descricao: this.novaDescricao.trim() || null,
      colunaId: alvo.id,
      prioridade: this.novaPrioridade ?? undefined,
      responsavelId: responsavelId ?? undefined,
      prazo: this.novoPrazo !== '' ? this.novoPrazo : undefined,
    }).subscribe({
      next: criada => {
        this.salvando.set(false);
        this.colunaNova.set(null);
        this.carregar(true);
        this.toast.sucesso(`Tarefa "${criada.titulo}" criada.`);
      },
      error: erro => {
        this.salvando.set(false);
        this.erro.set(mensagemDeErro(erro));
      },
    });
  }

  // ------------------------------------------------------------ gaveta

  abrirGaveta(tarefaId: number): void {
    this.aberta.set(true);
    this.tarefa.set(null);
    this.api.tarefa(tarefaId).subscribe({
      next: t => {
        this.tarefa.set(t);
        this.incluirDados(t);
      },
      error: erro => {
        this.toast.erro(mensagemDeErro(erro));
        this.aberta.set(false);
      },
    });
  }

  private incluirDados(t: Tarefa): void {
    this.tituloEditado = t.titulo;
    this.descricaoEditada = t.descricao ?? '';
    this.grafPrioridade = t.prioridade;
    this.grafPrazo = t.prazo ?? '';
    this.grafResponsavel = t.responsavel?.id ?? null;
    this.comentarios.set([]);
    this.historico.set([]);
    this.novoComentario = '';
    this.textoComentario = '';
    this.editandoCom.set(null);
    this.idsEtiquetasSalvos = new Set(t.etiquetas.map(e => e.id));

    this.api.comentarios(t.id).subscribe({
      next: cs => this.comentarios.set(cs),
      error: () => this.comentarios.set([]),
    });
    this.api.atividadesTarefa(t.id, 0, 25).subscribe({
      next: pag => this.historico.set(pag.conteudo),
      error: () => this.historico.set([]),
    });
  }

  fecharGaveta(): void {
    this.aberta.set(false);
    this.tarefa.set(null);
  }

  salvarTextos(t: Tarefa): void {
    const titulo = this.tituloEditado.trim();
    if (titulo.length < 3) {
      this.toast.erro('O título deve ter de 3 a 160 caracteres.');
      return;
    }
    this.api.editarTarefa(t.id, { titulo, descricao: this.descricaoEditada.trim() || null }).subscribe({
      next: atualizada => {
        this.tarefa.set(atualizada);
        this.toast.sucesso('Tarefa atualizada.');
        this.carregar(true);
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  trocarPrioridade(t: Tarefa, valor: unknown): void {
    const nova = valor === null || valor === undefined ? null : (valor as Prioridade);
    if (nova === null) {
      // o backend exige que a prioridade exista: volta para o valor atual
      this.grafPrioridade = t.prioridade;
      this.toast.aviso('Escolha sempre uma prioridade (BAIXA, MÉDIA, ALTA ou CRÍTICA).');
      return;
    }
    if (nova === t.prioridade) {
      return;
    }
    this.api.prioridadeTarefa(t.id, nova).subscribe({
      next: atualizada => {
        this.tarefa.set(atualizada);
        this.toast.sucesso('Prioridade alterada.');
        this.carregar(true);
      },
      error: erro => {
        this.toast.erro(mensagemDeErro(erro));
        this.grafPrioridade = t.prioridade;
      },
    });
  }

  trocarPrazo(t: Tarefa, valor: string): void {
    if (valor === (t.prazo ?? '')) {
      return;
    }
    this.api.prazoTarefa(t.id, valor === '' ? null : valor).subscribe({
      next: atualizada => {
        this.tarefa.set(atualizada);
        this.toast.sucesso(atualizada.prazo ? 'Prazo atualizado.' : 'Prazo removido.');
        this.carregar(true);
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  trocarResponsavel(t: Tarefa, valor: unknown): void {
    const id = valor === null || valor === undefined ? null : Number(valor);
    if (id === (t.responsavel?.id ?? null)) {
      return;
    }
    this.api.atribuirTarefa(t.id, id).subscribe({
      next: atualizada => {
        this.tarefa.set(atualizada);
        this.toast.sucesso(atualizada.responsavel
          ? `Atribuída a ${ atualizada.responsavel.nome }.`
          : 'Responsável removido.');
        this.carregar(true);
      },
      error: erro => {
        this.toast.erro(mensagemDeErro(erro));
        this.grafResponsavel = t.responsavel?.id ?? null;
      },
    });
  }

  // ------------------------------------------------------------ etiquetas

  /** IDs de etiquetas ligadas à tarefa atual. */
  idsEtiquetas(): Set<number> {
    return new Set(this.tarefa()?.etiquetas.map(e => e.id) ?? []);
  }

  alternarEtiqueta(e: Etiqueta): void {
    const t = this.tarefa();
    if (t === null) {
      return;
    }
    if (!this.podeEtiquetas(t)) {
      this.toast.aviso('Somente Gerente/Proprietário ou o responsável altera etiquetas.');
      return;
    }
    const ids = this.idsEtiquetas();
    if (ids.has(e.id)) {
      ids.delete(e.id);
    } else {
      ids.add(e.id);
    }
    const selecionadas = this.etiquetas().filter(x => ids.has(x.id));
    this.tarefa.set({ ...t, etiquetas: selecionadas });
  }

  idsEtiquetasMudou(): boolean {
    const t = this.tarefa();
    if (t === null) {
      return false;
    }
    const atuais = new Set(t.etiquetas.map(e => e.id));
    if (atuais.size !== this.idsEtiquetasSalvos.size) {
      return true;
    }
    for (const id of atuais) {
      if (!this.idsEtiquetasSalvos.has(id)) {
        return true;
      }
    }
    return false;
  }

  salvarEtiquetas(t: Tarefa): void {
    this.api.etiquetasTarefa(t.id, t.etiquetas.map(e => e.id)).subscribe({
      next: atualizada => {
        this.tarefa.set(atualizada);
        this.idsEtiquetasSalvos = new Set(atualizada.etiquetas.map(e => e.id));
        this.toast.sucesso('Etiquetas atualizadas.');
        this.carregar(true);
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  // ------------------------------------------------------------ comentários

  comentar(): void {
    const t = this.tarefa();
    if (t === null) {
      return;
    }
    const conteudo = this.novoComentario.trim();
    if (conteudo === '') {
      return;
    }
    this.api.comentar(t.id, conteudo).subscribe({
      next: () => {
        this.novoComentario = '';
        this.refrescarComentarios(t.id);
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  iniciarEdicaoCom(c: Comentario): void {
    this.editandoCom.set(c.id);
    this.textoComentario = c.conteudo;
  }

  cancelarEdicaoCom(): void {
    this.editandoCom.set(null);
  }

  salvarComentario(id: number): void {
    const t = this.tarefa();
    if (t === null) {
      return;
    }
    const conteudo = this.textoComentario.trim();
    if (conteudo === '') {
      return;
    }
    this.api.editarComentario(id, conteudo).subscribe({
      next: () => {
        this.editandoCom.set(null);
        this.refrescarComentarios(t.id);
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  excluirComentario(id: number): void {
    const t = this.tarefa();
    if (t === null || !window.confirm('Excluir este comentário?')) {
      return;
    }
    this.api.excluirComentario(id).subscribe({
      next: () => this.refrescarComentarios(t.id),
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  /** Mantém contagem de comentários do cartão fresca. */
  private refrescarComentarios(tarefaId: number): void {
    this.api.comentarios(tarefaId).subscribe({ next: cs => this.comentarios.set(cs) });
    this.api.tarefa(tarefaId).subscribe({ next: atualizada => this.tarefa.set(atualizada) });
  }

  podeGerirComentario(c: Comentario): boolean {
    return c.autor?.id === this.meuId() || this.papel() === 'OWNER';
  }

  // ------------------------------------------------------------ arquivar/excluir

  arquivar(t: Tarefa): void {
    this.api.arquivarTarefa(t.id, !t.arquivada).subscribe({
      next: () => {
        this.toast.sucesso(t.arquivada ? 'Tarefa restaurada.' : 'Tarefa arquivada.');
        this.fecharGaveta();
        this.carregar();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  excluir(t: Tarefa): void {
    if (!window.confirm(`Excluir a tarefa "${t.titulo}"? A ação não tem desfazer.`)) {
      return;
    }
    this.api.excluirTarefa(t.id).subscribe({
      next: () => {
        this.toast.sucesso('Tarefa excluída.');
        this.fecharGaveta();
        this.carregar();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  // ------------------------------------------------------------ helpers

  papel() {
    return this.projeto()?.papelUsuario ?? null;
  }

  podeQuadro(): boolean {
    return this.papel() === 'OWNER';
  }

  podeGestao(): boolean {
    return podeGerenciarTarefas(this.papel());
  }

  podeApagar(): boolean {
    return podeExcluirTarefa(this.papel());
  }

  podeTextos(t: Tarefa): boolean {
    return podeEditarTarefa(this.papel(), this.meuId(), t.responsavel?.id ?? null);
  }

  podeEtiquetas(t: Tarefa): boolean {
    return podeGerenciarTarefas(this.papel()) || this.meuId() === (t.responsavel?.id ?? null);
  }

  meuId(): number | null {
    return this.store.usuario()?.id ?? null;
  }

  textoMudou(t: Tarefa): boolean {
    return this.tituloEditado.trim() !== t.titulo || this.descricaoEditada.trim() !== (t.descricao ?? '');
  }

  vencida_(prazo: string): boolean {
    return (diasRestantes(prazo) ?? 0) < 0;
  }

  dias_(prazo: string): number | null {
    return diasRestantes(prazo);
  }

  classePrioridade_(p: Prioridade): string {
    return classePrioridade(p);
  }

  iniciais_(nome: string | null | undefined): string {
    return iniciais(nome ?? null);
  }

  dataLocal_(iso: string | null): string {
    return (iso ?? '').split('T')[0].split('-').reverse().join('/');
  }

  datahora_(iso: string | null): string {
    return formatarDataHora(iso);
  }
}
