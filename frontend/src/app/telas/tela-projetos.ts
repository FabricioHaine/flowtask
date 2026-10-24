/** Lista de projetos: busca com debounce, filtros por estado, cards com
 * progresso e menu de ações (editar/excluir com confirmação digitando o nome). */

import { ChangeDetectionStrategy, Component, OnDestroy, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiServico } from '../core/api';
import { AuthStore } from '../core/auth.store';
import { EstadoProjeto, RespostaProjeto, ESTADOS_PROJETO } from '../core/models';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';
import {
  ROTULO_ESTADO, ROTULO_PAPEL, classeEstado, formatarDataLocal, iniciais,
} from '../core/utilidades';
import { podeEditarProjeto, podeExcluirProjeto } from '../core/permissoes';

const ATRASO_BUSCA_MS = 350;

@Component({
  selector: 'app-tela-projetos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div class="cabeca-pagina">
        <div>
          <h1 class="titulo-pagina">Projetos</h1>
          <p class="descricao-pagina">Acompanhe todos os quadros em que você participa.</p>
        </div>
        <div class="acoes-cab">
          <button class="btn ghost ic" type="button" (click)="recarregar()"
                  title="Atualizar lista" aria-label="Atualizar lista">
            <i class="pi pi-refresh"></i>
          </button>
          <button class="btn" type="button" (click)="abrirCriacao()">
            <i class="pi pi-plus"></i> Novo projeto
          </button>
        </div>
      </div>

      <div class="barra-filtros">
        <div class="busca">
          <i class="pi pi-search"></i>
          <input class="input" placeholder="Buscar por nome ou descrição…"
                 [(ngModel)]="buscaFiltro" name="buscaFiltro" (ngModelChange)="buscaMudou($event)">
        </div>
        <div class="chips" role="group" aria-label="Filtrar por estado">
          <button class="chip" type="button" [class.ligado]="estado === null" (click)="escolherEstado(null)">Todos</button>
          @for (e of estados; track e) {
            <button class="chip" type="button" [class.ligado]="estado === e" (click)="escolherEstado(e)">
              {{ ROTULO_ESTADO_[e] }}
            </button>
          }
        </div>
        <span class="esp"></span>
        <select class="input seletor" [(ngModel)]="ordenacao" name="ordenacao" (ngModelChange)="recarregar()"
                aria-label="Ordenar projetos">
          <option value="">Mais recentes</option>
          <option value="NOME">Nome (A→Z)</option>
          <option value="PRAZO">Prazo</option>
        </select>
      </div>

      @if (carregando()) {
        <div class="grade-projetos" aria-hidden="true">
          @for (f of [1, 2, 3, 4, 5, 6]; track f) {
            <div class="card-projeto">
              <div class="sk sk-linha gorda" style="width: 62%"></div>
              <div class="sk sk-linha" style="width: 90%"></div>
              <div class="sk sk-linha" style="width: 46%"></div>
              <div class="sk progresso" style="width: 70%; height: 6px"></div>
            </div>
          }
        </div>
      } @else if (erroCarregamento()) {
        <div class="vazio">
          <span class="ic-vazio"><i class="pi pi-exclamation-triangle"></i></span>
          <h3>Não foi possível carregar os projetos</h3>
          <p>{{ erroCarregamento() }}</p>
          <div class="acoes-vazio">
            <button class="btn soft" type="button" (click)="recarregar()">
              <i class="pi pi-refresh"></i> Tentar de novo
            </button>
          </div>
        </div>
      } @else if (projetos().length === 0 && this.semFiltro()) {
        <div class="vazio">
          <span class="ic-vazio"><i class="pi pi-objects-column"></i></span>
          <h3>Comece pelo primeiro projeto</h3>
          <p>Todo trabalho no FlowTask acontece dentro de um projeto: nele você monta o quadro kanban, convida a equipe e acompanha as tarefas.</p>
          <div class="acoes-vazio">
            <button class="btn" type="button" (click)="abrirCriacao()">
              <i class="pi pi-plus"></i> Criar meu primeiro projeto
            </button>
          </div>
        </div>
      } @else if (projetos().length === 0) {
        <div class="vazio">
          <span class="ic-vazio"><i class="pi pi-filter-slash"></i></span>
          <h3>Nada encontrado com esses filtros</h3>
          <p>Ajuste a busca ou o estado para ver outros projetos.</p>
          <div class="acoes-vazio">
            <button class="btn ghost" type="button" (click)="limparFiltros()">
              <i class="pi pi-filter-slash"></i> Limpar filtros
            </button>
          </div>
        </div>
      } @else {
        <p class="texto-diminuto" style="margin: 0 0 12px;">
          {{ projetos().length }} projeto(s)
        </p>
        <div class="grade-projetos">
          @for (p of projetos(); track p.id) {
            <article class="card-projeto">
              <div class="topo-projeto">
                <h3>
                  <span class="tile-projeto"><i class="pi pi-objects-column"></i></span>
                  <a [routerLink]="['/projetos', p.id]">{{ p.nome }}</a>
                </h3>
                <span class="_ctx">
                  <button class="btn ghost ic sm" type="button"
                          (click)="alternarMenu($event, p.id)"
                          [attr.aria-haspopup]="'menu'"
                          [attr.aria-expanded]="menuAbertoId() === p.id"
                          title="Ações do projeto">
                    <i class="pi pi-ellipsis-v"></i>
                  </button>
                  @if (menuAbertoId() === p.id) {
                    <div class="menu-ctx" role="menu" (click)="$event.stopPropagation()">
                      <a class="item-menu" role="menuitem" [routerLink]="['/projetos', p.id]" (click)="fecharMenu()">
                        <i class="pi pi-external-link"></i> Abrir quadro
                      </a>
                      @if (dono(p)) {
                        <button class="item-menu" role="menuitem" type="button" (click)="abrirEdicao(p, $event)">
                          <i class="pi pi-pencil"></i> Editar projeto
                        </button>
                        <div class="feixe-menu"></div>
                        <button class="item-menu riscado" role="menuitem" type="button" (click)="pedirExclusao(p, $event)">
                          <i class="pi pi-trash"></i> Excluir projeto…
                        </button>
                      }
                    </div>
                  }
                </span>
              </div>

              <div class="linha-chips">
                <span class="badge" [class]="classeEstado_(p.estado)">{{ ROTULO_ESTADO_[p.estado] }}</span>
                <span class="badge" [class]="papelClasse(p.papelUsuario)">{{ ROTULO_PAPEL_[p.papelUsuario] }}</span>
              </div>

              @if (p.descricao) {
                <p class="desc">{{ p.descricao }}</p>
              }

              <div class="linha-progresso">
                <div class="progresso" [title]="(p.progressoPct ?? 0) + '% concluído'">
                  <span [style.width.%]="p.progressoPct ?? 0"></span>
                </div>
                <span class="pct">{{ p.progressoPct ?? 0 }}%</span>
              </div>

              <div class="meta-projeto">
                <small>
                  <i class="pi pi-check-square"></i>
                  {{ p.tarefasConcluidas ?? 0 }}/{{ p.totalTarefas ?? 0 }} tarefas
                </small>
                @if (p.prazoFinal) {
                  <small><i class="pi pi-calendar"></i> {{ formato_data(p.prazoFinal) }}</small>
                }
              </div>

              <div class="rodape-projeto">
                <div class="pessoas">
                  @for (m of p.membros.slice(0, 4); track m.id) {
                    <span class="avatar sm empilha" [title]="m.nome">{{ iniciais_(m.nome) }}</span>
                  }
                </div>
                @if (p.membros.length > 4) {
                  <span class="mais-pessoas">+{{ p.membros.length - 4 }}</span>
                }
              </div>
            </article>
          }
        </div>
      }

      @if (modoForm()) {
        <div class="sombra" (click)="fecharForm()">
          <div class="modal" (click)="$event.stopPropagation()">
            <div class="cabeca-modal">
              <h2>{{ modoForm() === 'criar' ? 'Novo projeto' : 'Editar projeto' }}</h2>
              <button class="btn ghost ic sm" type="button" (click)="fecharForm()" aria-label="Fechar">
                <i class="pi pi-times"></i>
              </button>
            </div>
            <p class="mensagem-info">
              {{ modoForm() === 'criar'
                ? 'Um quadro kanban padrão é criado junto do projeto.'
                : 'Somente o proprietário pode alterar os dados do projeto.' }}
            </p>

            <label class="campo">
              Nome
              <input class="input" [(ngModel)]="formNome" name="formNome" maxlength="140"
                     [disabled]="salvando()"
                     placeholder="Ex.: Lançamento do app">
            </label>
            <label class="campo">
              Descrição
              <textarea class="input" rows="3" [(ngModel)]="formDescricao" name="formDescricao" maxlength="500"
                        [disabled]="salvando()"
                        placeholder="O que este projeto entrega? (opcional)"></textarea>
            </label>
            <div class="par">
              <label class="campo">
                Início
                <input class="input" type="date" [(ngModel)]="formInicio" name="formInicio" [disabled]="salvando()">
              </label>
              <label class="campo">
                Prazo final
                <input class="input" type="date" [(ngModel)]="formPrazo" name="formPrazo" [disabled]="salvando()">
              </label>
            </div>
            @if (erro()) {
              <p class="mensagem-erro" role="alert"><i class="pi pi-exclamation-circle"></i> {{ erro() }}</p>
            }
            <div class="acoes">
              <button class="btn ghost" type="button" (click)="fecharForm()" [disabled]="salvando()">Cancelar</button>
              <button class="btn" type="button" (click)="salvarForm()"
                      [disabled]="salvando()" [class.carregando]="salvando()">
                {{ modoForm() === 'criar' ? 'Criar projeto' : 'Salvar alterações' }}
              </button>
            </div>
          </div>
        </div>
      }

      @if (excluindo()) {
        <div class="sombra" (click)="cancelarExclusao()">
          <div class="modal pequeno" (click)="$event.stopPropagation()">
            <div class="cabeca-modal">
              <h2><i class="pi pi-exclamation-triangle ic-aviso"></i> Excluir projeto</h2>
              <button class="btn ghost ic sm" type="button" (click)="cancelarExclusao()" aria-label="Fechar">
                <i class="pi pi-times"></i>
              </button>
            </div>
            <p class="mensagem-info">
              Esta ação <b style="color: var(--ft-danger);">é permanente</b>. Ao excluir
              <b style="color: var(--ft-text);">{{ excluindo()!.nome }}</b>, também serão removidos:
            </p>
            <ul class="lista-consequencias">
              <li><i class="pi pi-check-square"></i> {{ (excluindo()!.totalTarefas ?? 0) }} tarefa(s) e todos os comentários</li>
              <li><i class="pi pi-objects-column"></i> todas as colunas do quadro</li>
              <li><i class="pi pi-bookmark"></i> etiquetas, convites e histórico de atividades</li>
              <li><i class="pi pi-users"></i> o acesso de todo mundo que participava</li>
            </ul>
            <label class="campo">
              Digite <b style="color: var(--ft-text);">{{ excluindo()!.nome }}</b> para confirmar
              <input class="input" [(ngModel)]="nomeConfirmacao" name="nomeConfirmacao"
                     autocomplete="off" [disabled]="excluindoAgora()"
                     [class.invalido]="nomeConfirmacao.length > 0 && !nomeConfirmado()">
            </label>
            <div class="acoes">
              <button class="btn ghost" type="button" (click)="cancelarExclusao()" [disabled]="excluindoAgora()">
                Cancelar
              </button>
              <button class="btn perigo-solido" type="button" (click)="confirmarExclusao()"
                      [disabled]="!nomeConfirmado() || excluindoAgora()"
                      [class.carregando]="excluindoAgora()">
                <i class="pi pi-trash"></i> Excluir para sempre
              </button>
            </div>
          </div>
        </div>
      }
    </div>
  `,
  styles: `
    .linha-chips { display: flex; gap: 6px; flex-wrap: wrap; }
    .topo-projeto h3 a { color: var(--ft-text); overflow: hidden; text-overflow: ellipsis;
      white-space: nowrap; &:hover { color: var(--ft-primary-2); } }
    .ic-aviso { color: var(--ft-warning); font-size: 15px; margin-right: 8px; }
    ul.lista-consequencias { list-style: none; padding: 0; margin: 0; display: flex; flex-direction: column; gap: 7px;
      font-size: 12.5px; color: var(--ft-text-2);
      i.pi { color: var(--ft-text-3); font-size: 12px; margin-right: 8px; } }
  `,
})
export class TelaProjetos implements OnInit, OnDestroy {
  private readonly api = inject(ApiServico);
  private readonly despachador = inject(Router);
  readonly auth = inject(AuthStore);
  private readonly toast = inject(ServicoToast);

  readonly projetos = signal<RespostaProjeto[]>([]);
  readonly carregando = signal(true);
  readonly salvando = signal(false);
  readonly erro = signal<string | null>(null);
  readonly erroCarregamento = signal<string | null>(null);
  readonly menuAbertoId = signal<number | null>(null);
  readonly modoForm = signal<'criar' | 'editar' | null>(null);
  readonly excluindo = signal<RespostaProjeto | null>(null);
  readonly excluindoAgora = signal(false);

  readonly estados: EstadoProjeto[] = ESTADOS_PROJETO;
  estado: EstadoProjeto | null = null;
  buscaFiltro = '';
  ordenacao = '';

  formNome = '';
  formDescricao = '';
  formInicio = '';
  formPrazo = '';
  private editando: RespostaProjeto | null = null;
  nomeConfirmacao = '';
  nomeConfirmado(): boolean {
    const alvo = this.excluindo()?.nome ?? '';
    return alvo.length > 0 && alvo.trim().toLowerCase() === this.nomeConfirmacao.trim().toLowerCase();
  }

  ROTULO_ESTADO_ = ROTULO_ESTADO;
  ROTULO_PAPEL_ = ROTULO_PAPEL;

  private temporizadorBusca: ReturnType<typeof setTimeout> | null = null;

  ngOnInit(): void {
    this.recarregar();
  }

  ngOnDestroy(): void {
    if (this.temporizadorBusca !== null) {
      clearTimeout(this.temporizadorBusca);
    }
  }

  semFiltro(): boolean {
    return !this.buscaFiltro.trim() && this.estado === null;
  }

  recarregar(): void {
    this.carregando.set(true);
    this.erroCarregamento.set(null);
    this.api.projetos({
      busca: this.buscaFiltro.trim() || undefined,
      estado: this.estado ?? undefined,
      ordenacao: (this.ordenacao || undefined) as 'ATUALIZADO' | 'NOME' | 'PRAZO' | undefined,
    }).subscribe({
      next: lista => {
        this.projetos.set(lista);
        this.carregando.set(false);
      },
      error: erro => {
        this.projetos.set([]);
        this.carregando.set(false);
        this.erroCarregamento.set(mensagemDeErro(erro));
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  buscaMudou(texto: string): void {
    this.buscaFiltro = texto;
    if (this.temporizadorBusca !== null) {
      clearTimeout(this.temporizadorBusca);
    }
    this.temporizadorBusca = setTimeout(() => this.recarregar(), ATRASO_BUSCA_MS);
  }

  escolherEstado(estado: EstadoProjeto | null): void {
    this.estado = estado;
    this.recarregar();
  }

  limparFiltros(): void {
    this.buscaFiltro = '';
    this.estado = null;
    this.ordenacao = '';
    this.recarregar();
  }

  dono(p: RespostaProjeto): boolean {
    return podeExcluirProjeto(p.papelUsuario) || podeEditarProjeto(p.papelUsuario);
  }

  alternarMenu(evento: MouseEvent, id: number): void {
    evento.preventDefault();
    evento.stopPropagation();
    this.menuAbertoId.update(atual => (atual === id ? null : id));
  }

  fecharMenu(): void {
    this.menuAbertoId.set(null);
  }

  abrirCriacao(): void {
    this.editando = null;
    this.formNome = '';
    this.formDescricao = '';
    this.formInicio = '';
    this.formPrazo = '';
    this.erro.set(null);
    this.modoForm.set('criar');
  }

  abrirEdicao(p: RespostaProjeto, evento: MouseEvent): void {
    evento.preventDefault();
    evento.stopPropagation();
    this.fecharMenu();
    this.editando = p;
    this.formNome = p.nome;
    this.formDescricao = p.descricao ?? '';
    this.formInicio = p.dataInicio ?? '';
    this.formPrazo = p.prazoFinal ?? '';
    this.erro.set(null);
    this.modoForm.set('editar');
  }

  fecharForm(): void {
    if (this.salvando()) {
      return;
    }
    this.modoForm.set(null);
  }

  salvarForm(): void {
    const nome = this.formNome.trim();
    if (nome.length < 3) {
      this.erro.set('O nome do projeto deve ter de 3 a 140 caracteres.');
      return;
    }
    const dados = {
      nome,
      descricao: this.formDescricao.trim() || null,
      dataInicio: this.formInicio || null,
      prazoFinal: this.formPrazo || null,
    };
    this.salvando.set(true);
    if (this.modoForm() === 'criar') {
      this.api.criarProjeto(dados).subscribe({
        next: projeto => {
          this.salvando.set(false);
          this.modoForm.set(null);
          this.toast.sucesso('Projeto criado.');
          void this.despachador.navigate(['/projetos', projeto.id]);
        },
        error: erro => this.falharFormulario(erro),
      });
    } else if (this.editando !== null) {
      this.api.atualizarProjeto(this.editando.id, dados).subscribe({
        next: () => {
          this.salvando.set(false);
          this.modoForm.set(null);
          this.toast.sucesso('Projeto atualizado.');
          this.recarregar();
        },
        error: erro => this.falharFormulario(erro),
      });
    }
  }

  private falharFormulario(erro: unknown): void {
    this.salvando.set(false);
    this.erro.set(mensagemDeErro(erro));
    this.toast.erro(mensagemDeErro(erro));
  }

  pedirExclusao(p: RespostaProjeto, evento: MouseEvent): void {
    evento.preventDefault();
    evento.stopPropagation();
    this.fecharMenu();
    this.excluindo.set(p);
    this.nomeConfirmacao = '';
    this.excluindoAgora.set(false);
  }

  cancelarExclusao(): void {
    if (this.excluindoAgora()) {
      return;
    }
    this.excluindo.set(null);
    this.nomeConfirmacao = '';
  }

  confirmarExclusao(): void {
    const alvo = this.excluindo();
    if (alvo === null || !this.nomeConfirmado()) {
      return;
    }
    this.excluindoAgora.set(true);
    this.api.excluirProjeto(alvo.id).subscribe({
      next: () => {
        this.excluindo.set(null);
        this.excluindoAgora.set(false);
        this.toast.sucesso(`Projeto "${alvo.nome}" excluído.`);
        if (this.despachador.url !== '/projetos') {
          void this.despachador.navigate(['/projetos']);
        } else {
          this.recarregar();
        }
      },
      error: erro => {
        this.excluindoAgora.set(false);
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  classeEstado_(estado: EstadoProjeto): string {
    return classeEstado(estado);
  }

  papelClasse(papel: RespostaProjeto['papelUsuario']): string {
    return papel.toLowerCase();
  }

  iniciais_(nome: string | null): string {
    return iniciais(nome);
  }

  formato_data(iso: string | null): string {
    return formatarDataLocal(iso);
  }
}
