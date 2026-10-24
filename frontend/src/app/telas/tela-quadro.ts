/** Quadro kanban: colunas com menus, cartões informativos, criação de tarefas
 * e gaveta de detalhes. DnD persiste no backend; prompts nativos foram
 * substituídos por modais locais. */

import { CdkDrag, CdkDragDrop, CdkDropList, CdkDropListGroup } from '@angular/cdk/drag-drop';
import { ChangeDetectionStrategy, Component, HostListener, OnInit, inject, signal } from '@angular/core';
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
  iconeAtividade as iconeDeTipo,
} from '../core/utilidades';
import { podeEditarTarefa, podeExcluirTarefa, podeGerenciarTarefas } from '../core/permissoes';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';

interface Confirmacao {
  titulo: string;
  texto: string;
  botao: string;
  acao: () => void;
}

@Component({
  selector: 'app-tela-quadro',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink, CdkDropList, CdkDropListGroup, CdkDrag],
  template: `
    <div class="pagina quadro-pag">
      <div class="cabeca-pagina cabeca-projeto">
        @if (carregandoProjeto()) {
          <div style="flex: 1;">
            <div class="sk sk-linha gorda" style="width: 240px;"></div>
            <div class="sk sk-linha" style="width: 380px; margin-top: 10px;"></div>
          </div>
        } @else {
          <div style="min-width: 0;">
            <div class="linha-titulo-projeto">
              <h1>@if (projeto(); as p) { {{ p.nome }} } @else { Quadro }</h1>
              @if (projeto(); as p) {
                <span class="badge" [class]="classeEstado_(p.estado)">{{ ROTULO_ESTADO[p.estado] }}</span>
                <span class="badge" [class]="papeClasse(p.papelUsuario)">{{ ROTULO_PAPEL[p.papelUsuario] }}</span>
              }
            </div>
            @if (projeto(); as p) {
              <div class="linha-resumo-projeto">
                <div class="progresso mini-resumo" [title]="(p.progressoPct ?? 0) + '% concluído'">
                  <span [style.width.%]="p.progressoPct ?? 0"></span>
                </div>
                <small>{{ p.tarefasConcluidas ?? 0 }}/{{ p.totalTarefas ?? 0 }} concluídas</small>
                @if (p.prazoFinal) {
                  <small class="chip-prazo">Prazo {{ dataLocal_(p.prazoFinal) }}</small>
                }
              </div>
              @if (p.descricao) {
                <p class="descricao-pagina">{{ p.descricao }}</p>
              }
            }
          </div>
        }
        <div class="acoes-cab">
          <button class="btn ghost ic" type="button" (click)="carregar()"
                  title="Atualizar quadro" aria-label="Atualizar quadro">
            <i class="pi pi-refresh"></i>
          </button>
          <a class="btn ghost" [routerLink]="['/projetos', projetoId, 'equipe']">
            <i class="pi pi-cog"></i> Projeto &amp; equipe
          </a>
        </div>
      </div>

      @if (carregando()) {
        <div class="area-quadro" aria-hidden="true">
          @for (c of [1, 2, 3, 4]; track c) {
            <section class="coluna">
              <div class="sk sk-linha" style="margin: 12px 14px; width: 40%;"></div>
              <div style="padding: 0 10px 10px; display: flex; flex-direction: column; gap: 9px;">
                <div class="sk sk-linha" style="height: 64px; border-radius: 8px;"></div>
                <div class="sk sk-linha" style="height: 64px; border-radius: 8px;"></div>
                <div class="sk sk-linha" style="height: 64px; border-radius: 8px;"></div>
              </div>
            </section>
          }
        </div>
      } @else if (colunas().length === 0) {
        <div class="vazio">
          <span class="ic-vazio"><i class="pi pi-objects-column"></i></span>
          <h3>O quadro está sem colunas</h3>
          @if (podeQuadro()) {
            <p>Crie a primeira coluna para começar a organizar as tarefas.</p>
            <div class="acoes-vazio">
              <button class="btn" type="button" (click)="abrirModalColunaNova()">
                <i class="pi pi-plus"></i> Criar coluna
              </button>
            </div>
          } @else {
            <p>Ainda não há colunas configuradas neste projeto.</p>
          }
        </div>
      } @else {
        <div class="area-quadro" cdkDropListGroup>
          @for (coluna of colunas(); track coluna.id) {
            <section class="coluna">
              <header class="coluna-cab">
                <span class="marca-fim" title="Coluna de conclusão">
                  @if (coluna.colunaConclusao) {<i class="pi pi-check-circle"></i>}
                </span>
                <h3 class="nome-coluna">{{ coluna.nome }}</h3>
                <span class="bub">{{ coluna.tarefas.length }}</span>
                @if (podeQuadro()) {
                  <span class="_ctx">
                    <button class="gv" type="button" (click)="alternarMenuColuna($event, coluna.id)"
                            aria-label="Ações da coluna" title="Ações da coluna">
                      <i class="pi pi-ellipsis-v"></i>
                    </button>
                    @if (menuColunaId() === coluna.id) {
                      <div class="menu-ctx" role="menu" (click)="$event.stopPropagation()">
                        <button class="item-menu" role="menuitem" type="button" (click)="abrirModalColunaRenomear(coluna, $event)">
                          <i class="pi pi-pencil"></i> Renomear
                        </button>
                      </div>
                    }
                  </span>
                }
              </header>

              <div class="corpo-coluna" cdkDropList [cdkDropListData]="coluna" (cdkDropListDropped)="soltar($event)">
                @for (t of coluna.tarefas; track t.id) {
                  <article class="cartao" cdkDrag (click)="abrirGaveta(t.id)" tabindex="0"
                           (keydown.enter)="abrirGaveta(t.id)"
                           [attr.aria-label]="'Abrir tarefa ' + t.titulo">
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
                        <span class="chip-prazo"
                              [class.atrasada]="vencida_(t.prazo) && t.dataConclusao === null"
                              [class.hoje]="dias_(t.prazo) === 0">
                          <i class="pi pi-clock"></i> {{ dataLocal_(t.prazo) }}
                        </span>
                      }
                      @if (t.dataConclusao !== null) {
                        <span class="chip-prazo boa" title="Concluída"><i class="pi pi-check"></i></span>
                      }
                      @if (t.totalComentarios > 0) {
                        <span class="mini-info" title="{{ t.totalComentarios }} comentário(s)">
                          <i class="pi pi-comment"></i> {{ t.totalComentarios }}
                        </span>
                      }
                      @if (t.responsavel) {
                        <span class="avatar micro" [title]="t.responsavel.nome">{{ iniciais_(t.responsavel.nome) }}</span>
                      }
                    </div>
                  </article>
                }
              </div>

              <footer class="pie-coluna">
                @if (podeGestao()) {
                  <button class="btn soft sm largura" type="button" (click)="iniciarCriacao(coluna)">
                    <i class="pi pi-plus"></i> Tarefa
                  </button>
                }
              </footer>
            </section>
          }

          @if (podeQuadro()) {
            <div class="coluna-fim">
              <button class="btn ghost" type="button" (click)="abrirModalColunaNova()">
                <i class="pi pi-plus"></i> Nova coluna
              </button>
            </div>
          }
        </div>
      }
    </div>

    <!-- modal de criação de tarefa -->
    @if (colunaNova(); as alvo) {
      <div class="sombra" (click)="cancelarCriacao()">
        <div class="modal" (click)="$event.stopPropagation()">
          <div class="cabeca-modal">
            <h2>Nova tarefa</h2>
            <button class="btn ghost ic sm" type="button" (click)="cancelarCriacao()" aria-label="Fechar">
              <i class="pi pi-times"></i>
            </button>
          </div>
          <p class="mensagem-info">A tarefa entra na coluna <b style="color: var(--ft-text);">{{ alvo.nome }}</b>.</p>

          <label class="campo">
            Título
            <input class="input" [(ngModel)]="novoTitulo" name="novoTitulo" maxlength="160"
                   [disabled]="salvando()"
                   placeholder="O que precisa ser feito?">
          </label>
          <label class="campo">
            Descrição
            <textarea class="input" [(ngModel)]="novaDescricao" name="novaDescricao" rows="3" maxlength="4000"
                      [disabled]="salvando()"
                      placeholder="Detalhes, links, contexto… (opcional)"></textarea>
          </label>
          <div class="par">
            <label class="campo">
              Prioridade
              <select class="input" [(ngModel)]="novaPrioridade" name="novaPrioridade" [disabled]="salvando()">
                <option [ngValue]="null">Sem prioridade</option>
                @for (p of prioridades; track p) {
                  <option [ngValue]="p">{{ ROTULO_PRIORIDADE[p] }}</option>
                }
              </select>
            </label>
            <label class="campo">
              Prazo
              <input class="input" type="date" [(ngModel)]="novoPrazo" name="novoPrazo" [disabled]="salvando()">
            </label>
          </div>
          @if (podeGestao()) {
            <label class="campo">
              Responsável
              <select class="input" [(ngModel)]="novoResponsavel" name="novoResponsavel" [disabled]="salvando()">
                <option [ngValue]="null">Sem responsável</option>
                @for (m of projeto()?.membros ?? []; track m.id) {
                  <option [ngValue]="m.id">{{ m.nome }}</option>
                }
              </select>
            </label>
          }
          @if (erro()) {
            <p class="mensagem-erro" role="alert"><i class="pi pi-exclamation-circle"></i> {{ erro() }}</p>
          }
          <div class="acoes">
            <button class="btn ghost" type="button" (click)="cancelarCriacao()" [disabled]="salvando()">Cancelar</button>
            <button class="btn" type="button" (click)="criarTarefa()" [disabled]="salvando()"
                    [class.carregando]="salvando()">Criar tarefa</button>
          </div>
        </div>
      </div>
    }

    <!-- modal de coluna (criar / renomear) -->
    @if (modalColunaAberto()) {
      <div class="sombra" (click)="fecharModalColuna()">
        <div class="modal pequeno" (click)="$event.stopPropagation()">
          <div class="cabeca-modal">
            <h2>{{ modoColuna() === 'nova' ? 'Nova coluna' : 'Renomear coluna' }}</h2>
            <button class="btn ghost ic sm" type="button" (click)="fecharModalColuna()" aria-label="Fechar">
              <i class="pi pi-times"></i>
            </button>
          </div>
          <label class="campo">
            Nome da coluna
            <input class="input" [(ngModel)]="nomeColuna" name="nomeColuna" maxlength="80"
                   (keydown.enter)="confirmarColuna()"
                   [disabled]="salvando()"
                   placeholder="Ex.: Em revisão">
          </label>
          <div class="acoes">
            <button class="btn ghost" type="button" (click)="fecharModalColuna()" [disabled]="salvando()">Cancelar</button>
            <button class="btn" type="button" (click)="confirmarColuna()" [disabled]="salvando()"
                    [class.carregando]="salvando()">
              {{ modoColuna() === 'nova' ? 'Criar coluna' : 'Salvar' }}
            </button>
          </div>
        </div>
      </div>
    }

    <!-- confirmação genérica (excluir tarefa / comentário) -->
    @if (confirmacao(); as c) {
      <div class="sombra" (click)="fecharConfirmacao()">
        <div class="modal pequeno" (click)="$event.stopPropagation()">
          <div class="cabeca-modal">
            <h2><i class="pi pi-exclamation-triangle ic-aviso"></i> {{ c.titulo }}</h2>
            <button class="btn ghost ic sm" type="button" (click)="fecharConfirmacao()" aria-label="Fechar">
              <i class="pi pi-times"></i>
            </button>
          </div>
          <p class="mensagem-info">{{ c.texto }}</p>
          <div class="acoes">
            <button class="btn ghost" type="button" (click)="fecharConfirmacao()">Cancelar</button>
            <button class="btn perigo-solido" type="button" (click)="executarConfirmacao()">{{ c.botao }}</button>
          </div>
        </div>
      </div>
    }

    <!-- gaveta de detalhe da tarefa -->
    @if (aberta()) {
      <div class="vao-gaveta" (click)="fecharGaveta()">
        <div class="gaveta" (click)="$event.stopPropagation()">
          @if (tarefa(); as t) {
            <header class="gaveta-cab">
              <input class="gaveta-titulo" [(ngModel)]="tituloEditado" name="tituloEditado" maxlength="160"
                     [disabled]="!podeTextos(t)"
                     [placeholder]="podeTextos(t) ? 'Título da tarefa' : 'Título (somente leitura)'">
              <button class="btn ghost ic sm" type="button" (click)="fecharGaveta()" aria-label="Fechar">
                <i class="pi pi-times"></i>
              </button>
            </header>

            <div class="gaveta-corpo">
              <div class="secao-gaveta">
                <div class="linhas-dados">
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
                  <label class="campo">
                    Responsável
                    <input class="input" [ngModel]="t.responsavel.nome" name="soLeitura"
                           [ngModelOptions]="{standalone: true}" [disabled]="true">
                  </label>
                }
              </div>

              <div class="secao-gaveta">
                <span class="rotulo-secao">Descrição</span>
                <textarea class="input" [(ngModel)]="descricaoEditada" name="descricaoEditada" rows="5" maxlength="4000"
                          [disabled]="!podeTextos(t)"
                          placeholder="Detalhe a tarefa (opcional)"></textarea>
              </div>

              <div class="secao-gaveta">
                <span class="rotulo-secao">Etiquetas</span>
                @if (etiquetas().length === 0) {
                  <p class="mensagem-info">
                    Nenhuma etiqueta no projeto — crie em
                    <a [routerLink]="['/projetos', projetoId, 'equipe']">Projeto &amp; equipe</a>.
                  </p>
                } @else {
                  <div class="linha-etiquetas">
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
                  <button class="btn soft sm" type="button" (click)="salvarEtiquetas(t)">
                    <i class="pi pi-save"></i> Salvar etiquetas
                  </button>
                }
              </div>

              <div class="secao-gaveta">
                <span class="rotulo-secao">Comentários ({{ comentarios().length }})</span>
                @if (comentarios().length === 0) {
                  <p class="mensagem-info">Nenhum comentário ainda.</p>
                }
                @for (c of comentarios(); track c.id) {
                  <div class="comentario">
                    <span class="avatar sm" [title]="c.autor?.nome">{{ iniciais_(c.autor?.nome) }}</span>
                    <div class="corpo-com">
                      <b>{{ c.autor?.nome ?? '?' }}</b>
                      <small> · {{ datahora_(c.criadoEm) }}@if (c.editadoEm) { · editado }</small>
                      @if (editandoCom() === c.id) {
                        <textarea class="input" [(ngModel)]="textoComentario" name="textoComentario" rows="2"
                                  maxlength="2000"></textarea>
                        <div class="acoes" style="justify-content: flex-start;">
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
                        <button class="gv perigo" type="button" (click)="pedirExclusaoComentario(c)">excluir</button>
                      </div>
                    }
                  </div>
                }
                <div class="com-box">
                  <span class="avatar sm">{{ iniciais_(meuNome()) }}</span>
                  <textarea class="input" [(ngModel)]="novoComentario" name="novoComentario" rows="2"
                            maxlength="2000"
                            placeholder="Escreva um comentário… (Enter para comentar)"
                            (keydown.enter)="comentar()"></textarea>
                  <button class="btn ic sm" type="button" (click)="comentar()"
                          [disabled]="novoComentario.trim() === ''"
                          aria-label="Enviar comentário" title="Comentar">
                    <i class="pi pi-send"></i>
                  </button>
                </div>
              </div>

              @if (historico().length > 0) {
                <div class="secao-gaveta">
                  <span class="rotulo-secao">Atividade</span>
                  @for (a of historico(); track a.id) {
                    <div class="linha-atividade">
                      <span class="ativ-ic"><i class="pi {{ iconeAtividade(a.tipo) }}"></i></span>
                      <span class="txt-ativ">
                        <b>{{ a.usuario?.nome ?? '?' }}</b> — {{ a.resumo }}
                      </span>
                      <small class="quando">{{ datahora_(a.criadoEm) }}</small>
                    </div>
                  }
                </div>
              }
            </div>

            <footer class="gaveta-pie">
              @if (podeTextos(t)) {
                <button class="btn" type="button" (click)="salvarTextos(t)" [disabled]="!textoMudou(t)">
                  <i class="pi pi-save"></i> Salvar alterações
                </button>
              }
              @if (t.concluida) {
                <span class="badge sucesso"><i class="pi pi-check"></i> Concluída</span>
              }
              <span class="esp"></span>
              @if (podeTextos(t)) {
                <button class="btn soft sm" type="button" (click)="arquivar(t)">
                  <i class="pi pi-inbox"></i> {{ t.arquivada ? 'Restaurar' : 'Arquivar' }}
                </button>
              }
              @if (podeApagar()) {
                <button class="btn perigo sm" type="button" (click)="pedirExclusaoTarefa(t)">
                  <i class="pi pi-trash"></i> Excluir
                </button>
              }
            </footer>
          } @else {
            <header class="gaveta-cab">
              <div class="sk sk-linha gorda" style="width: 60%;"></div>
              <button class="btn ghost ic sm" type="button" (click)="fecharGaveta()" aria-label="Fechar">
                <i class="pi pi-times"></i>
              </button>
            </header>
            <div class="gaveta-corpo">
              <div class="sk sk-linha" style="width: 50%;"></div>
              <div class="sk sk-linha" style="width: 80%;"></div>
              <div class="sk sk-linha" style="width: 65%;"></div>
            </div>
          }
        </div>
      </div>
    }
  `,
  styles: `
    .cabeca-projeto { margin-bottom: 16px; }
    .linha-titulo-projeto { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .linha-resumo-projeto { display: flex; align-items: center; gap: 12px; margin-top: 9px;
      small { color: var(--ft-text-2); } }
    .mini-resumo { width: 180px; margin-top: 0; }
    .ic-aviso { color: var(--ft-warning); font-size: 15px; margin-right: 7px; }
    .carregando-projeto { display: none; }
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
  readonly carregandoProjeto = signal(true);
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

  // modal de coluna (nova/renomear)
  readonly modalColunaAberto = signal(false);
  readonly modoColuna = signal<'nova' | 'renomear'>('nova');
  private colunaEditando: ColunaQuadro | null = null;
  nomeColuna = '';

  // menu da coluna
  readonly menuColunaId = signal<number | null>(null);

  // confirmação genérica
  readonly confirmacao = signal<Confirmacao | null>(null);

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
  ROTULO_ESTADO: Record<string, string> = {
    PLANEJADO: 'Planejado', ATIVO: 'Ativo', PAUSADO: 'Pausado',
    CONCLUIDO: 'Concluído', ARQUIVADO: 'Arquivado',
  };
  ROTULO_PAPEL: Record<string, string> = { OWNER: 'Proprietário', MANAGER: 'Gerente', MEMBER: 'Membro' };

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
    this.carregandoProjeto.set(true);
    this.api.projeto(this.projetoId).subscribe({
      next: p => {
        this.projeto.set(p);
        this.carregandoProjeto.set(false);
      },
      error: () => {
        /* erros de quadro abaixo são os visíveis */
        this.carregandoProjeto.set(false);
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
    if (this.salvando()) {
      return;
    }
    this.colunaNova.set(null);
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

  // ------------------------------------------------------------ coluna (nova/renomear)

  abrirModalColunaNova(): void {
    if (!this.podeQuadro()) {
      this.toast.aviso('A organização do quadro é exclusiva do Proprietário.');
      return;
    }
    this.modoColuna.set('nova');
    this.colunaEditando = null;
    this.nomeColuna = '';
    this.modalColunaAberto.set(true);
  }

  abrirModalColunaRenomear(coluna: ColunaQuadro, evento: MouseEvent): void {
    evento.preventDefault();
    evento.stopPropagation();
    this.menuColunaId.set(null);
    this.modoColuna.set('renomear');
    this.colunaEditando = coluna;
    this.nomeColuna = coluna.nome;
    this.modalColunaAberto.set(true);
  }

  fecharModalColuna(): void {
    if (this.salvando()) {
      return;
    }
    this.modalColunaAberto.set(false);
  }

  confirmarColuna(): void {
    const nome = this.nomeColuna.trim();
    if (nome.length < 2 || nome.length > 80) {
      this.toast.erro('O nome da coluna deve ter de 2 a 80 caracteres.');
      return;
    }
    this.salvando.set(true);
    if (this.modoColuna() === 'nova') {
      this.api.criarColuna(this.projetoId, nome).subscribe({
        next: () => {
          this.salvando.set(false);
          this.modalColunaAberto.set(false);
          this.toast.sucesso('Coluna criada.');
          this.carregar(true);
        },
        error: erro => {
          this.salvando.set(false);
          this.toast.erro(mensagemDeErro(erro));
        },
      });
    } else if (this.colunaEditando !== null) {
      this.api.atualizarColuna(this.projetoId, this.colunaEditando.id, { nome }).subscribe({
        next: () => {
          this.salvando.set(false);
          this.modalColunaAberto.set(false);
          this.toast.sucesso('Coluna renomeada.');
          this.carregar(true);
        },
        error: erro => {
          this.salvando.set(false);
          this.toast.erro(mensagemDeErro(erro));
        },
      });
    }
  }

  alternarMenuColuna(evento: MouseEvent, id: number): void {
    evento.preventDefault();
    evento.stopPropagation();
    this.menuColunaId.update(atual => (atual === id ? null : id));
  }

  // ------------------------------------------------------------ confirmação genérica

  private pedirConfirmacao(c: Confirmacao): void {
    this.confirmacao.set(c);
  }

  fecharConfirmacao(): void {
    this.confirmacao.set(null);
  }

  executarConfirmacao(): void {
    const c = this.confirmacao();
    this.confirmacao.set(null);
    c?.acao();
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

  pedirExclusaoComentario(c: Comentario): void {
    const t = this.tarefa();
    if (t === null) {
      return;
    }
    this.pedirConfirmacao({
      titulo: 'Excluir comentário',
      texto: 'O comentário será removido permanentemente desta tarefa.',
      botao: 'Excluir comentário',
      acao: () => this.excluirComentario(c.id),
    });
  }

  private excluirComentario(id: number): void {
    const t = this.tarefa();
    if (t === null) {
      return;
    }
    this.api.excluirComentario(id).subscribe({
      next: () => {
        this.toast.sucesso('Comentário excluído.');
        this.refrescarComentarios(t.id);
      },
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

  pedirExclusaoTarefa(t: Tarefa): void {
    this.pedirConfirmacao({
      titulo: 'Excluir tarefa',
      texto: `A tarefa "${t.titulo}"${t.totalComentarios > 0 ? ` (com ${t.totalComentarios} comentário(s))` : ''} será excluída para sempre, junto do histórico relacionado.`,
      botao: 'Excluir tarefa',
      acao: () => this.excluirTarefa(t.id),
    });
  }

  private excluirTarefa(id: number): void {
    this.api.excluirTarefa(id).subscribe({
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

  meuNome(): string | null {
    return this.store.usuario()?.nome ?? null;
  }

  textoMudou(t: Tarefa): boolean {
    return this.tituloEditado.trim() !== t.titulo || this.descricaoEditada.trim() !== (t.descricao ?? '');
  }

  /** Ícone coerente com cada tipo de histórico da tarefa. */
  iconeAtividade(tipo: string): string {
    return iconeDeTipo(tipo);
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

  classeEstado_(estado: string): string {
    return estado.toLowerCase();
  }

  papeClasse(papel: string): string {
    return papel.toLowerCase();
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

  @HostListener('document:click')
  fecharMenus(): void {
    this.menuColunaId.set(null);
  }

  @HostListener('document:keydown.escape')
  noEscape(): void {
    if (this.confirmacao()) {
      this.fecharConfirmacao();
      return;
    }
    if (this.modalColunaAberto()) {
      this.fecharModalColuna();
      return;
    }
    if (this.colunaNova()) {
      this.cancelarCriacao();
      return;
    }
    if (this.aberta()) {
      this.fecharGaveta();
    }
    this.menuColunaId.set(null);
  }
}
