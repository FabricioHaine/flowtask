/** Projeto & equipe: configuração, integrantes, convites, colunas, etiquetas
 * e histórico. Ações destrutivas pedem confirmação em modal (sem prompt nativo). */

import { ChangeDetectionStrategy, Component, HostListener, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { ActivatedRoute, RouterLink } from '@angular/router';

import { ApiServico } from '../core/api';
import { AuthStore } from '../core/auth.store';
import {
  Atividade, ColunaQuadro, Convite, EstadoProjeto, Etiqueta, Papel, RespostaMembro, RespostaProjeto,
  ESTADOS_PROJETO,
} from '../core/models';
import { podeConvidar, podeGerenciarMembros, podeGerenciarQuadro, podeGerenciarTarefas } from '../core/permissoes';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';
import {
  ROTULO_ESTADO, ROTULO_PAPEL, classeEstado, formatarDataHora, iniciais, iconeAtividade, tonoAtividade,
} from '../core/utilidades';

interface Confirmacao {
  titulo: string;
  texto: string;
  botao: string;
  acao: () => void;
}

@Component({
  selector: 'app-tela-equipe',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div class="cabeca-pagina">
        <div>
          <a class="btn ghost sm" [routerLink]="['/projetos', projetoId]">
            <i class="pi pi-angle-left"></i> Voltar ao quadro
          </a>
          <div style="margin-top: 10px;" class="linha-titulo-projeto">
            <h1 class="titulo-pagina">@if (projeto(); as p) { {{ p.nome }} } @else { Projeto & equipe }</h1>
            @if (projeto(); as p) {
              <span class="badge" [class]="classeEstado_(p.estado)">{{ ROTULO_ESTADO_[p.estado] }}</span>
              <span class="badge" [class]="papelClasse_(p.papelUsuario)">{{ ROTULO_PAPEL_[p.papelUsuario] }}</span>
            }
          </div>
        </div>
      </div>

      @if (carregando()) {
        <div class="grade-config" aria-hidden="true">
          <div class="painel-config card">
            <div class="sk sk-linha gorda" style="width: 30%;"></div>
            @for (f of [1, 2, 3]; track f) {<div class="sk sk-linha"></div>}
          </div>
          <div class="painel-config card">
            <div class="sk sk-linha gorda" style="width: 40%;"></div>
            @for (f of [1, 2, 3]; track f) {<div class="sk sk-linha"></div>}
          </div>
        </div>
      } @else {
        <div class="grade-config">

          <!-- ============================== projeto ============================== -->
          <section class="painel-config card">
            <div class="cabeca-painel"><i class="pi pi-cog"></i><h2>Projeto</h2></div>
            @if (projeto(); as p) {
              <label class="campo">
                Nome
                <input class="input" [(ngModel)]="form_nome" name="form_nome" maxlength="140" [disabled]="!ehOwner()">
              </label>
              <label class="campo">
                Descrição
                <textarea class="input" [(ngModel)]="form_descricao" name="form_descricao" rows="3" maxlength="1000"
                          [disabled]="!ehOwner()"></textarea>
              </label>
              <div class="par">
                <label class="campo">
                  Início
                  <input class="input" type="date" [(ngModel)]="form_inicio" name="form_inicio" [disabled]="!ehOwner()">
                </label>
                <label class="campo">
                  Prazo final
                  <input class="input" type="date" [(ngModel)]="form_prazo" name="form_prazo" [disabled]="!ehOwner()">
                </label>
              </div>
              @if (ehOwner()) {
                <label class="campo">
                  Estado
                  <select class="input" [(ngModel)]="form_estado" name="form_estado">
                    @for (e of estados; track e) {
                      <option [ngValue]="e">{{ ROTULO_ESTADO_[e] }}</option>
                    }
                  </select>
                </label>
                <div class="acoes" style="justify-content: flex-start;">
                  <button class="btn" type="button" (click)="salvarProjeto()" [disabled]="salvando()"
                          [class.carregando]="salvando()">Salvar dados</button>
                  <button class="btn soft" type="button" (click)="salvarEstado()"
                          [disabled]="form_estado === p.estado">Aplicar estado</button>
                </div>
              } @else {
                <p class="mensagem-info">Apenas o Proprietário altera os dados do projeto.</p>
              }
            }
          </section>

          <!-- ============================ integrantes ============================ -->
          <section class="painel-config card">
            <div class="cabeca-painel"><i class="pi pi-users"></i><h2>Integrantes ({{ membros().length }})</h2></div>
            @for (m of membros(); track m.id) {
              <div class="linha-item">
                <span class="avatar" [title]="m.usuario.email">{{ iniciais_(m.usuario.nome) }}</span>
                <div class="quem-item">
                  <b>{{ m.usuario.nome }} @if (m.usuario.id === meuId()) { <span class="badge neutro">você</span> }</b>
                  <small>{{ m.usuario.email }}</small>
                </div>
                <span class="fim-linha">
                  @if (!podeMembros()) {
                    <span class="badge" [class]="papelClasse_(m.funcao)">{{ ROTULO_PAPEL_[m.funcao] }}</span>
                  } @else if (m.funcao === 'OWNER') {
                    <span class="badge owner"><i class="pi pi-crown"></i> Proprietário</span>
                  } @else {
                    <select class="input select-funcao" [ngModel]="m.funcao" (ngModelChange)="mudarFuncao(m, $event)"
                            name="funcao-{{ m.id }}" aria-label="Função de {{ m.usuario.nome }}">
                      @for (p of papeisComGente; track p) {
                        <option [ngValue]="p">{{ ROTULO_PAPEL_[p] }}</option>
                      }
                    </select>
                    <button class="btn perigo sm" type="button" (click)="pedirRemoverMembro(m)">
                      <i class="pi pi-user-minus"></i> Remover
                    </button>
                  }
                </span>
              </div>
            }
            <small class="mensagem-info">
              Definir a função de alguém como Proprietário transfere a posse do projeto.
            </small>
          </section>

          <!-- ============================== convites ============================== -->
          <section class="painel-config card">
            <div class="cabeca-painel"><i class="pi pi-envelope"></i><h2>Convites</h2></div>
            @if (podeConvidar_(papel())) {
              <div class="nova-linha">
                <input class="input" [(ngModel)]="novoConviteEmail" name="novoConviteEmail" type="email"
                       placeholder="e-mail da pessoa que você quer convidar" (keydown.enter)="criarConvite()">
                <select class="input" style="width: 130px;" [(ngModel)]="novoConviteFuncao" name="novoConviteFuncao"
                        aria-label="Função proposta">
                  <option value="MEMBER">Membro</option>
                  <option value="MANAGER">Gerente</option>
                </select>
                <button class="btn" type="button" (click)="criarConvite()" [disabled]="salvando()">
                  <i class="pi pi-user-plus"></i> Convidar
                </button>
              </div>
              @if (convites().length === 0) {
                <p class="mensagem-info">Nenhum convite ainda.</p>
              }
              @for (c of convites(); track c.id) {
                <div class="linha-item" [class.desligada]="c.estado !== 'PENDENTE'">
                  <span class="avatar sm"><i class="pi pi-user"></i></span>
                  <div class="quem-item">
                    <b>{{ c.email }}</b>
                    <small>{{ contagem_(c) }}</small>
                  </div>
                  <span class="fim-linha">
                    <span class="badge" [class]="classeEstadoConvite_(c)">{{ ROTULO_ESTADO_CONVITE_[c.estado] }}</span>
                    @if (c.estado === 'PENDENTE' && (ehOwner() || c.criadoPor?.id === meuId())) {
                      <button class="gv perigo" type="button" (click)="pedirCancelarConvite(c)">cancelar</button>
                    }
                  </span>
                </div>
              }
              <small class="mensagem-info">
                A pessoa recebe o convite em <b>Notificações</b> e precisa de conta com o e-mail convidado.
              </small>
            } @else {
              <p class="mensagem-info">Somente Gerente ou Proprietário gerenciam convites.</p>
            }
          </section>

          <!-- ============================ colunas do quadro ============================ -->
          @if (podeQuadro()) {
            <section class="painel-config card">
              <div class="cabeca-painel"><i class="pi pi-objects-column"></i><h2>Colunas do quadro</h2></div>
              <div class="nova-linha">
                <input class="input" [(ngModel)]="novaColunaNome" name="novaColunaNome" placeholder="Nome da nova coluna…"
                       maxlength="80" (keydown.enter)="criarColuna()">
                <button class="btn sm" type="button" (click)="criarColuna()"><i class="pi pi-plus"></i> Criar</button>
              </div>
              @for (c of colunas(); track c.id) {
                <div class="linha-item" [class.desligada]="!c.ativa">
                  <span class="ordem-mov">
                    <button class="gv" type="button" (click)="subirColuna(c)" [disabled]="c.ordem === 0"
                            aria-label="Subir coluna" title="Subir"><i class="pi pi-angle-up"></i></button>
                    <button class="gv" type="button" (click)="descerColuna(c)"
                            [disabled]="c === colunas()[colunas().length - 1]"
                            aria-label="Descer coluna" title="Descer"><i class="pi pi-angle-down"></i></button>
                  </span>
                  <div class="quem-item">
                    <b>
                      {{ c.nome }}
                      @if (!c.ativa) {<span class="badge neutro">arquivada</span>}
                    </b>
                    <small>{{ c.tarefas.length }} tarefa(s)</small>
                  </div>
                  <span class="fim-linha">
                    @if (c.colunaConclusao) {
                      <span class="badge sucesso"><i class="pi pi-check"></i> conclusão</span>
                    } @else {
                      <button class="gv" type="button" (click)="marcarConclusao(c)" title="Marcar como conclusão">
                        conclusão
                      </button>
                    }
                    <button class="gv" type="button" (click)="abrirRenomearColuna(c)">renomear</button>
                    <button class="gv" type="button" (click)="arquivarColuna(c)">
                      {{ c.ativa ? 'arquivar' : 'restaurar' }}
                    </button>
                    <button class="gv perigo" type="button" (click)="pedirExcluirColuna(c)">excluir</button>
                  </span>
                </div>
              }
              <small class="mensagem-info">Use "conclusão" para sinalizar a coluna onde as tarefas terminam (uma por projeto).</small>
            </section>
          }

          <!-- ============================== etiquetas ============================== -->
          @if (podeTarefas()) {
            <section class="painel-config card">
              <div class="cabeca-painel"><i class="pi pi-bookmark"></i><h2>Etiquetas ({{ etiquetas().length }})</h2></div>
              <div class="nova-linha">
                <input class="input" [(ngModel)]="novaEtiquetaNome" name="novaEtiquetaNome" placeholder="Nome da etiqueta…"
                       maxlength="40" (keydown.enter)="criarEtiqueta()">
                <input class="input cor" type="color" [(ngModel)]="novaEtiquetaCor" name="novaEtiquetaCor"
                       aria-label="Cor da etiqueta">
                <button class="btn sm" type="button" (click)="criarEtiqueta()"><i class="pi pi-plus"></i> Criar</button>
              </div>
              @if (etiquetas().length === 0) {
                <p class="mensagem-info">Nenhuma etiqueta ainda — crie a primeira para agrupar tarefas.</p>
              }
              @for (e of etiquetas(); track e.id) {
                <div class="linha-item">
                  <span class="pill-etiqueta"><i class="dot" [style.background]="e.cor"></i>{{ e.nome }}</span>
                  <span class="esp"></span>
                  <span class="fim-linha">
                    <span class="avatar sm" [style.background]="e.cor" [style.border-color]="e.cor"></span>
                    <button class="gv" type="button" (click)="abrirEditarEtiqueta(e)">editar</button>
                    <button class="gv perigo" type="button" (click)="pedirExcluirEtiqueta(e)">excluir</button>
                  </span>
                </div>
              }
            </section>
          }

          <!-- ============================== histórico ============================== -->
          <section class="painel-config card span-2">
            <div class="cabeca-painel"><i class="pi pi-history"></i><h2>Histórico do projeto</h2></div>
            @if (atividades().length === 0) {
              <div class="vazio" style="padding: 26px;">
                <span class="ic-vazio"><i class="pi pi-history"></i></span>
                <h3>Sem registro de atividades</h3>
                <p>Quando o time mexer no projeto, o histórico aparece aqui.</p>
              </div>
            }
            @for (a of atividades(); track a.id) {
              <div class="linha-item" style="align-items: flex-start;">
                <span class="ativ-ic {{ tono_(a.tipo) }}"><i class="pi {{ icone_(a.tipo) }}"></i></span>
                <span class="txt-ativ" style="flex: 1; font-size: 12.5px; color: var(--ft-text-2); line-height: 1.5;">
                  <b style="color: var(--ft-text);">{{ a.usuario?.nome ?? 'Sistema' }}</b> — {{ a.resumo }}
                </span>
                <small class="quando" style="color: var(--ft-text-3); font-size: 11px; white-space: nowrap;">{{ datahora_(a.criadoEm) }}</small>
              </div>
            }
            @if (totalAtividades() > atividades().length) {
              <button class="btn ghost" type="button" (click)="maisAtividades()">
                <i class="pi pi-angle-down"></i> Carregar mais ({{ totalAtividades() - atividades().length }})
              </button>
            }
          </section>
        </div>
      }

      <!-- confirmação genérica -->
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

      <!-- modal de texto (renomear coluna / editar etiqueta) -->
      @if (modal(); as m) {
        <div class="sombra" (click)="fecharModal()">
          <div class="modal pequeno" (click)="$event.stopPropagation()">
            <div class="cabeca-modal">
              <h2>{{ m.titulo }}</h2>
              <button class="btn ghost ic sm" type="button" (click)="fecharModal()" aria-label="Fechar">
                <i class="pi pi-times"></i>
              </button>
            </div>
            <label class="campo">
              {{ m.campo }}
              <input class="input" [(ngModel)]="valorModal" name="valorModal" (keydown.enter)="salvarModal()"
                     maxlength="40">
            </label>
            @if (m.comCor) {
              <label class="campo">
                Cor
                <input class="input cor" type="color" [(ngModel)]="corModal" name="corModal">
              </label>
            }
            <div class="acoes">
              <button class="btn ghost" type="button" (click)="fecharModal()">Cancelar</button>
              <button class="btn" type="button" (click)="salvarModal()">{{ m.botao }}</button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    .cabeca-painel { display: flex; align-items: center; gap: 8px;
      h2 { flex: 1; } i.pi { color: var(--ft-text-3); font-size: 14px; } }
    .ic-aviso { color: var(--ft-warning); font-size: 15px; margin-right: 7px; }
    .linha-titulo-projeto { display: flex; align-items: center; gap: 10px; flex-wrap: wrap; }
    .quando { flex-shrink: 0; padding-top: 4px; }
    .txt-ativ b { color: var(--ft-text); }
  `,
})
export class TelaEquipe implements OnInit {
  private readonly api = inject(ApiServico);
  private readonly rota = inject(ActivatedRoute);
  private readonly store = inject(AuthStore);
  private readonly toast = inject(ServicoToast);

  readonly projeto = signal<RespostaProjeto | null>(null);
  readonly membros = signal<RespostaMembro[]>([]);
  readonly convites = signal<Convite[]>([]);
  readonly colunas = signal<ColunaQuadro[]>([]);
  readonly etiquetas = signal<Etiqueta[]>([]);
  readonly atividades = signal<Atividade[]>([]);
  readonly carregando = signal(true);
  readonly salvando = signal(false);

  readonly papeisComGente: Papel[] = ['MANAGER', 'MEMBER'];
  readonly estados: EstadoProjeto[] = ESTADOS_PROJETO;
  ROTULO_ESTADO_ = ROTULO_ESTADO;
  ROTULO_PAPEL_ = ROTULO_PAPEL;
  ROTULO_ESTADO_CONVITE_: Record<string, string> = {
    PENDENTE: 'Pendente', ACEITO: 'Aceito', RECUSADO: 'Recusado', EXPIRADO: 'Expirado', CANCELADO: 'Cancelado',
  };

  form_nome = '';
  form_descricao = '';
  form_inicio = '';
  form_prazo = '';
  form_estado: EstadoProjeto = 'PLANEJADO';

  novoConviteEmail = '';
  novoConviteFuncao: Papel = 'MEMBER';

  novaColunaNome = '';
  novaEtiquetaNome = '';
  novaEtiquetaCor = '#8293FF';

  // confirmação destrutiva genérica
  readonly confirmacao = signal<Confirmacao | null>(null);

  // modal de texto (renomear coluna / editar etiqueta)
  readonly modal = signal<{ titulo: string; campo: string; botao: string; comCor: boolean; acao: (valor: string, cor: string) => void } | null>(null);
  valorModal = '';
  corModal = '#8293FF';

  pagAtividades = 0;
  totalAtividades_ = 0;

  projetoId = 0;

  ngOnInit(): void {
    this.projetoId = Number(this.rota.snapshot.paramMap.get('id'));
    this.carregar();
  }

  carregar(): void {
    this.carregando.set(true);
    this.api.projeto(this.projetoId).subscribe({
      next: p => {
        this.projeto.set(p);
        this.form_nome = p.nome;
        this.form_descricao = p.descricao ?? '';
        this.form_inicio = p.dataInicio ?? '';
        this.form_prazo = p.prazoFinal ?? '';
        this.form_estado = p.estado;
      },
      error: erro => {
        this.carregando.set(false);
        this.toast.erro(mensagemDeErro(erro));
      },
    });
    this.api.membros(this.projetoId).subscribe({
      next: ms => {
        this.membros.set(ms);
        this.carregando.set(false);
      },
      error: () => this.carregando.set(false),
    });
    this.api.convitesDoProjeto(this.projetoId).subscribe({
      next: cs => this.convites.set(cs),
      error: () => this.convites.set([]),
    });
    this.api.colunas(this.projetoId).subscribe({
      next: cs => this.colunas.set(cs),
      error: () => this.colunas.set([]),
    });
    this.api.etiquetas(this.projetoId).subscribe({
      next: es => this.etiquetas.set(es),
      error: () => this.etiquetas.set([]),
    });
    this.carregarAtividades(0);
  }

  private carregarAtividades(pagina: number): void {
    this.api.atividadesProjeto(this.projetoId, pagina, 20).subscribe({
      next: pag => {
        this.totalAtividades_ = pag.total;
        this.atividades.set(pagina === 0 ? pag.conteudo : [...this.atividades(), ...pag.conteudo]);
      },
      error: () => {
        this.atividades.set([]);
        this.totalAtividades_ = 0;
      },
    });
  }

  maisAtividades(): void {
    this.pagAtividades += 1;
    this.carregarAtividades(this.pagAtividades);
  }

  // ------------------------------------------------------------ confirmação

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

  // ------------------------------------------------------------ projeto

  ehOwner(): boolean {
    return this.papel() === 'OWNER';
  }

  podeMembros(): boolean {
    return podeGerenciarMembros(this.papel());
  }

  podeQuadro(): boolean {
    return podeGerenciarQuadro(this.papel());
  }

  podeTarefas(): boolean {
    return podeGerenciarTarefas(this.papel());
  }

  podeConvidar_(papel: Papel | null): boolean {
    return podeConvidar(papel);
  }

  papel(): Papel | null {
    return this.projeto()?.papelUsuario ?? null;
  }

  meuId(): number | null {
    return this.store.usuario()?.id ?? null;
  }

  salvarProjeto(): void {
    const nome = this.form_nome.trim();
    if (nome.length < 3) {
      this.toast.erro('O nome do projeto deve ter de 3 a 140 caracteres.');
      return;
    }
    this.salvando.set(true);
    this.api.atualizarProjeto(this.projetoId, {
      nome,
      descricao: this.form_descricao.trim() || null,
      dataInicio: this.form_inicio || null,
      prazoFinal: this.form_prazo || null,
    }).subscribe({
      next: p => {
        this.salvando.set(false);
        this.projeto.set(p);
        this.toast.sucesso('Projeto atualizado.');
      },
      error: erro => {
        this.salvando.set(false);
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  salvarEstado(): void {
    const p = this.projeto();
    if (p === null || this.form_estado === p.estado) {
      return;
    }
    this.api.mudarEstadoProjeto(this.projetoId, this.form_estado).subscribe({
      next: atualizado => {
        this.projeto.set(atualizado);
        this.toast.sucesso(`Estado alterado para ${ROTULO_ESTADO[atualizado.estado]}.`);
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  // ------------------------------------------------------------ membros

  mudarFuncao(m: RespostaMembro, valor: unknown): void {
    const funcao = valor as Papel;
    if (funcao === m.funcao) {
      return;
    }
    this.api.alterarFuncao(this.projetoId, m.id, funcao).subscribe({
      next: () => {
        this.toast.sucesso(funcao === 'OWNER'
          ? `Propriedade transferida para ${m.usuario.nome}.`
          : `Função alterada para ${ROTULO_PAPEL[funcao]}.`);
        this.carregar();
      },
      error: erro => {
        this.toast.erro(mensagemDeErro(erro));
        this.carregar();
      },
    });
  }

  pedirRemoverMembro(m: RespostaMembro): void {
    this.pedirConfirmacao({
      titulo: 'Remover integrante',
      texto: `${m.usuario.nome} perde o acesso a este projeto e às tarefas dele. O histórico das ações dele permanece no projeto.`,
      botao: 'Remover do projeto',
      acao: () => this.removerMembro(m.id),
    });
  }

  private removerMembro(membroId: number): void {
    this.api.removerMembro(this.projetoId, membroId).subscribe({
      next: () => {
        this.toast.sucesso('Integrante removido.');
        this.carregar();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  // ------------------------------------------------------------ convites

  criarConvite(): void {
    const email = this.novoConviteEmail.trim();
    if (email === '') {
      this.toast.erro('Informe o e-mail do convidado.');
      return;
    }
    this.salvando.set(true);
    this.api.criarConvite(this.projetoId, { email, funcaoProposta: this.novoConviteFuncao }).subscribe({
      next: convite => {
        this.salvando.set(false);
        this.novoConviteEmail = '';
        this.convites.set([convite, ...this.convites()]);
        this.toast.sucesso(`Convite enviado para ${convite.email}.`);
      },
      error: erro => {
        this.salvando.set(false);
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  pedirCancelarConvite(c: Convite): void {
    this.pedirConfirmacao({
      titulo: 'Cancelar convite',
      texto: `O convite para ${c.email} deixa de ser válido — a pessoa não consegue mais aceitá-lo.`,
      botao: 'Cancelar convite',
      acao: () => this.cancelarConvite(c.id),
    });
  }

  private cancelarConvite(conviteId: number): void {
    this.api.cancelarConvite(this.projetoId, conviteId).subscribe({
      next: () => {
        this.toast.sucesso('Convite cancelado.');
        this.convites.set(this.convites().map(x =>
          x.id === conviteId ? { ...x, estado: 'CANCELADO' } : x));
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  /** Explicação do estado do convite (data, e-mail cadastrado etc.). */
  contagem_(c: Convite): string {
    const criado = formatarDataHora(c.criadoEm);
    const expira = c.expiraEm ? ` · expira em ${formatarDataHora(c.expiraEm)}` : '';
    const funcao = `convite de ${ROTULO_PAPEL[c.funcaoProposta].toLowerCase()}`;
    const cadastrado = c.emailCadastrado ? '' : ' · e-mail ainda sem conta';
    return `${funcao} · enviado por ${c.criadoPor?.nome ?? '?'} em ${criado}${expira}${cadastrado}`;
  }

  classeEstadoConvite_(c: Convite): string {
    switch (c.estado) {
      case 'ACEITO': return 'sucesso';
      case 'RECUSADO': return 'perigo';
      case 'PENDENTE': return 'owner';
      default: return 'neutro';
    }
  }

  // ------------------------------------------------------------ colunas

  criarColuna(): void {
    const nome = this.novaColunaNome.trim();
    if (nome.length < 2) {
      this.toast.erro('O nome da coluna deve ter de 2 a 80 caracteres.');
      return;
    }
    this.salvando.set(true);
    this.api.criarColuna(this.projetoId, nome).subscribe({
      next: () => {
        this.salvando.set(false);
        this.novaColunaNome = '';
        this.toast.sucesso('Coluna criada.');
        this.refrescarColunas();
      },
      error: erro => {
        this.salvando.set(false);
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  abrirRenomearColuna(c: ColunaQuadro): void {
    this.valorModal = c.nome;
    this.modal.set({
      titulo: 'Renomear coluna',
      campo: 'Novo nome',
      botao: 'Salvar',
      comCor: false,
      acao: valor => this.renomearColuna(c.id, valor),
    });
  }

  private renomearColuna(colunaId: number, nome: string): void {
    const limpa = nome.trim();
    if (limpa.length < 2) {
      this.toast.erro('O nome da coluna deve ter de 2 a 80 caracteres.');
      return;
    }
    this.api.atualizarColuna(this.projetoId, colunaId, { nome: limpa }).subscribe({
      next: () => {
        this.toast.sucesso('Coluna renomeada.');
        this.refrescarColunas();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  marcarConclusao(c: ColunaQuadro): void {
    this.api.atualizarColuna(this.projetoId, c.id, { nome: c.nome, colunaConclusao: true }).subscribe({
      next: () => {
        this.toast.sucesso(`"${c.nome}" é agora a coluna de conclusão.`);
        this.refrescarColunas();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  subirColuna(c: ColunaQuadro): void {
    this.moverColuna(c, -1);
  }

  descerColuna(c: ColunaQuadro): void {
    this.moverColuna(c, 1);
  }

  private moverColuna(c: ColunaQuadro, delta: number): void {
    const ids = this.colunas().map(x => x.id);
    const pos = ids.indexOf(c.id);
    const novo = pos + delta;
    if (pos < 0 || novo < 0 || novo >= ids.length) {
      return;
    }
    ids.splice(pos, 1);
    ids.splice(novo, 0, c.id);
    this.api.reordenarColunas(this.projetoId, ids).subscribe({
      next: () => this.refrescarColunas(),
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  arquivarColuna(c: ColunaQuadro): void {
    this.api.arquivarColuna(this.projetoId, c.id, !c.ativa).subscribe({
      next: () => {
        this.toast.sucesso(c.ativa ? 'Coluna arquivada.' : 'Coluna restaurada.');
        this.refrescarColunas();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  pedirExcluirColuna(c: ColunaQuadro): void {
    const vazia = c.tarefas.length === 0 && c.ativa;
    this.pedirConfirmacao({
      titulo: 'Excluir coluna',
      texto: vazia
        ? `A coluna "${c.nome}" será excluída do quadro. A ação não pode ser desfeita.`
        : `A coluna "${c.nome}" tem ${c.tarefas.length} tarefa(s) — o backend só exclui colunas vazias. ${c.ativa ? 'Mova ou arquive as tarefas primeiro.' : 'Restaure a coluna e esvazie-a antes.'}`,
      botao: vazia ? 'Excluir coluna' : 'Tentar mesmo assim',
      acao: () => this.excluirColuna(c.id, c.nome),
    });
  }

  private excluirColuna(colunaId: number, nome: string): void {
    this.api.excluirColuna(this.projetoId, colunaId).subscribe({
      next: () => {
        this.toast.sucesso(`Coluna "${nome}" excluída.`);
        this.refrescarColunas();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  private refrescarColunas(): void {
    this.api.colunas(this.projetoId).subscribe({
      next: cs => this.colunas.set(cs),
      error: () => { /* mantém as atuais */ },
    });
  }

  // ------------------------------------------------------------ etiquetas

  criarEtiqueta(): void {
    const nome = this.novaEtiquetaNome.trim();
    if (nome.length < 2) {
      this.toast.erro('O nome da etiqueta deve ter de 2 a 40 caracteres.');
      return;
    }
    this.api.criarEtiqueta(this.projetoId, { nome, cor: this.novaEtiquetaCor }).subscribe({
      next: () => {
        this.novaEtiquetaNome = '';
        this.refrescarEtiquetas();
        this.toast.sucesso('Etiqueta criada.');
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  abrirEditarEtiqueta(e: Etiqueta): void {
    this.valorModal = e.nome;
    this.corModal = e.cor;
    this.modal.set({
      titulo: 'Editar etiqueta',
      campo: 'Nome',
      botao: 'Salvar etiqueta',
      comCor: true,
      acao: (valor, cor) => this.salvarEdicaoEtiqueta(e.id, valor, cor),
    });
  }

  private salvarEdicaoEtiqueta(etiquetaId: number, nome: string, cor: string): void {
    const limpa = nome.trim();
    if (limpa.length < 2) {
      this.toast.erro('O nome da etiqueta deve ter de 2 a 40 caracteres.');
      return;
    }
    this.api.editarEtiqueta(this.projetoId, etiquetaId, { nome: limpa, cor }).subscribe({
      next: () => {
        this.toast.sucesso('Etiqueta atualizada.');
        this.refrescarEtiquetas();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  pedirExcluirEtiqueta(e: Etiqueta): void {
    this.pedirConfirmacao({
      titulo: 'Excluir etiqueta',
      texto: `"${e.nome}" será apagada e sai automaticamente das tarefas onde está ligada.`,
      botao: 'Excluir etiqueta',
      acao: () => this.excluirEtiqueta(e.id, e.nome),
    });
  }

  private excluirEtiqueta(etiquetaId: number, nome: string): void {
    this.api.excluirEtiqueta(this.projetoId, etiquetaId).subscribe({
      next: () => {
        this.toast.sucesso(`Etiqueta "${nome}" excluída.`);
        this.refrescarEtiquetas();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  private refrescarEtiquetas(): void {
    this.api.etiquetas(this.projetoId).subscribe({
      next: es => this.etiquetas.set(es),
      error: () => { /* mantém as atuais */ },
    });
  }

  // ------------------------------------------------------------ modal de texto

  fecharModal(): void {
    this.modal.set(null);
  }

  salvarModal(): void {
    const m = this.modal();
    if (m === null) {
      return;
    }
    this.modal.set(null);
    m.acao(this.valorModal, this.corModal);
  }

  // ------------------------------------------------------------ helpers

  icone_(tipo: string): string {
    return iconeAtividade(tipo);
  }

  tono_(tipo: string): string {
    return tonoAtividade(tipo);
  }

  totalAtividades(): number {
    return this.totalAtividades_;
  }

  classeEstado_(estado: EstadoProjeto): string {
    return classeEstado(estado);
  }

  papelClasse_(papel: Papel): string {
    return papel.toLowerCase();
  }

  iniciais_(nome: string | null): string {
    return iniciais(nome);
  }

  datahora_(iso: string | null): string {
    return formatarDataHora(iso);
  }

  @HostListener('document:keydown.escape')
  noEscape(): void {
    if (this.confirmacao()) {
      this.fecharConfirmacao();
      return;
    }
    if (this.modal()) {
      this.fecharModal();
    }
  }
}
