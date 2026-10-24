/** Projeto & equipe: config, integrantes, convites, colunas, etiquetas e histórico. */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
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
  ROTULO_ESTADO, ROTULO_PAPEL, classeEstado, formatarDataHora, iniciais,
} from '../core/utilidades';

@Component({
  selector: 'app-tela-equipe',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div class="cabeca-pagina">
        <div>
          <a class="voltar" [routerLink]="['/projetos', projetoId]">← Voltar ao quadro</a>
          <h1>@if (projeto(); as p) { {{ p.nome }} } @else { Projeto & equipe }</h1>
        </div>
      </div>

      @if (carregando()) {
        <p class="mensagem-info">Carregando…</p>
      } @else {
        <div class="grade-equipe">

          <!-- ===================== dados do projeto ===================== -->
          <section class="partSPA card">
            <h2>Projeto</h2>
            @if (projeto(); as p) {
              <div class="estado-atual">
                <span class="badge" [class]="classeEstado_(p.estado)">{{ ROTULO_ESTADO_[p.estado] }}</span>
              </div>
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
                <div class="acoes">
                  <button class="btn" type="button" (click)="salvarProjeto()" [disabled]="salvando()">Salvar dados</button>
                  <button class="btn ghost" type="button" (click)="salvarEstado()">Aplicar estado</button>
                </div>
              } @else {
                <p class="mensagem-info">Apenas o Proprietário altera os dados do projeto.</p>
              }
            }
          </section>

          <!-- ===================== integrantes ===================== -->
          <section class="partSPA card">
            <h2>Integrantes ({{ membros().length }})</h2>
            @for (m of membros(); track m.id) {
              <div class="linha-membro">
                <span class="avatar" [title]="m.usuario.email">{{ iniciais_(m.usuario.nome) }}</span>
                <div class="quem-escreve">
                  <b>{{ m.usuario.nome }} @if (m.usuario.id === meuId()) { <span class="badge neutro">você</span> }</b>
                  <small>{{ m.usuario.email }}</small>
                </div>
                <span class="fim-linha">
                  @if (!podeMembros()) {
                    <span class="badge" [class]="papelClasse_(m.funcao)">{{ ROTULO_PAPEL_[m.funcao] }}</span>
                  } @else if (m.funcao === 'OWNER') {
                    <span class="badge owner">{{ ROTULO_PAPEL_[m.funcao] }}</span>
                  } @else {
                    <select class="input select-funcao" [ngModel]="m.funcao" (ngModelChange)="mudarFuncao(m, $event)"
                            name="funcao-{{ m.id }}">
                      @for (p of papeisComGente; track p) {
                        <option [ngValue]="p">{{ ROTULO_PAPEL_[p] }}</option>
                      }
                    </select>
                    <button class="btn danger sm" type="button" (click)="removerMembro(m)">Remover</button>
                  }
                </span>
              </div>
            }
            <p class="mensagem-info">
              Definir a função de alguém como Proprietário transfere a posse do projeto.
            </p>
          </section>

          <!-- ===================== convites ===================== -->
          <section class="partSPA card">
            <h2>Convites</h2>
            @if (podeConvidar_(papel())) {
              <div class="novo-convite">
                <input class="input" [(ngModel)]="novoConviteEmail" name="novoConviteEmail" type="email"
                       placeholder="e-mail da pessoa que você quer convidar">
                <select class="input" [(ngModel)]="novoConviteFuncao" name="novoConviteFuncao">
                  <option value="MEMBER">Membro</option>
                  <option value="MANAGER">Gerente</option>
                </select>
                <button class="btn" type="button" (click)="criarConvite()" [disabled]="salvando()">Convidar</button>
              </div>
              @if (convites().length === 0) {
                <p class="mensagem-info">Nenhum convite ainda.</p>
              }
              @for (c of convites(); track c.id) {
                <div class="linha-convite">
                  <div class="quem-escreve">
                    <b>{{ c.email }}</b>
                    <small>{{ contagem_(c) }}</small>
                  </div>
                  <span class="fim-linha">
                    <span class="badge" [class]="classeEstadoConvite_(c)">{{ ROTULO_ESTADO_CONVITE_[c.estado] }}</span>
                    @if (c.estado === 'PENDENTE' && (ehOwner() || c.criadoPor?.id === meuId())) {
                      <button class="btn ghost sm" type="button" (click)="cancelarConvite(c)">Cancelar</button>
                    }
                  </span>
                </div>
              }
              <small class="mensagem-info">
                A pessoa recebe o convite dentro do próprio FlowTask (na tela de Notificações) e precisa de conta
                com o e-mail convidado.
              </small>
            } @else {
              <p class="mensagem-info">Somente Gerente ou Proprietário gerenciam convites.</p>
            }
          </section>

          <!-- ===================== colunas do quadro ===================== -->
          @if (podeQuadro()) {
            <section class="partSPA card">
              <h2>Colunas do quadro</h2>
              <div class="nova-coluna">
                <input class="input" [(ngModel)]="novaColunaNome" name="novaColunaNome" placeholder="Nova coluna…"
                       maxlength="80">
                <button class="btn sm" type="button" (click)="criarColuna()">Criar</button>
              </div>
              @for (c of colunas(); track c.id) {
                <div class="linha-coluna" [class.desligada]="!c.ativa">
                  <span class="ordem-mov">
                    <button class="gv" type="button" (click)="subirColuna(c)" [disabled]="c.ordem === 0">▲</button>
                    <button class="gv" type="button" (click)="descerColuna(c)" [disabled]="c === colunas()[colunas().length - 1]">▼</button>
                  </span>
                  <div class="quem-escreve">
                    <b>{{ c.nome }}</b>
                    <small>
                      {{ c.tarefas.length }} tarefas
                      @if (c.colunaConclusao) { · coluna de conclusão }
                      @if (!c.ativa) { · arquivada }
                    </small>
                  </div>
                  <span class="fim-linha">
                    @if (c.colunaConclusao) {
                      <span class="badge sucesso">✓ conclusão</span>
                    } @else {
                      <button class="btn soft sm" type="button" (click)="marcarConclusao(c)">Marcar como conclusão</button>
                    }
                    <button class="btn ghost sm" type="button" (click)="renomearColuna(c)">Renomear</button>
                    <button class="btn ghost sm" type="button" (click)="arquivarColuna(c)">
                      {{ c.ativa ? 'Arquivar' : 'Restaurar' }}
                    </button>
                    <button class="btn danger sm" type="button" (click)="excluirColuna(c)">Excluir</button>
                  </span>
                </div>
              }
              <small class="mensagem-info">Renomeie para reativar a sinalização de conclusão (uma coluna por projeto).</small>
            </section>
          }

          <!-- ===================== etiquetas ===================== -->
          @if (podeTarefas()) {
            <section class="partSPA card">
              <h2>Etiquetas ({{ etiquetas().length }})</h2>
              <div class="nova-etiqueta">
                <input class="input" [(ngModel)]="novaEtiquetaNome" name="novaEtiquetaNome" placeholder="Nova etiqueta…"
                       maxlength="40">
                <input class="input cor" type="color" [(ngModel)]="novaEtiquetaCor" name="novaEtiquetaCor">
                <button class="btn sm" type="button" (click)="criarEtiqueta()">Criar</button>
              </div>
              @if (etiquetas().length === 0) {
                <p class="mensagem-info">Nenhuma etiqueta.</p>
              }
              @for (e of etiquetas(); track e.id) {
                <div class="linha-etiqueta">
                  <span class="pill-etiqueta"><i class="dot" [style.background]="e.cor"></i>{{ e.nome }}</span>
                  <span class="fim-linha">
                    <button class="btn ghost sm" type="button" (click)="editarEtiqueta(e)">Editar</button>
                    <button class="btn danger sm" type="button" (click)="excluirEtiqueta(e)">Excluir</button>
                  </span>
                </div>
              }
            </section>
          }

          <!-- ===================== histórico ===================== -->
          <section class="partSPA card span-2">
            <h2>Histórico do projeto</h2>
            @if (atividades().length === 0) {
              <p class="mensagem-info">Nenhuma atividade registrada.</p>
            }
            @for (a of atividades(); track a.id) {
              <p class="linha-atividade">
                <small>{{ datahora_(a.criadoEm) }} · <b>{{ a.usuario?.nome ?? 'Sistema' }}</b> — {{ a.resumo }}</small>
              </p>
            }
            @if (totalAtividades() > atividades().length) {
              <button class="btn ghost sm" type="button" (click)="maisAtividades()">Carregar mais</button>
            }
          </section>
        </div>
      }
    </div>
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

  removerMembro(m: RespostaMembro): void {
    if (!window.confirm(`Remover ${m.usuario.nome} do projeto?`)) {
      return;
    }
    this.api.removerMembro(this.projetoId, m.id).subscribe({
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

  cancelarConvite(c: Convite): void {
    if (!window.confirm(`Cancelar o convite para ${c.email}?`)) {
      return;
    }
    this.api.cancelarConvite(this.projetoId, c.id).subscribe({
      next: () => {
        this.toast.sucesso('Convite cancelado.');
        this.convites.set(this.convites().map(x =>
          x.id === c.id ? { ...x, estado: 'CANCELADO' } : x));
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
    this.api.criarColuna(this.projetoId, nome).subscribe({
      next: () => {
        this.novaColunaNome = '';
        this.toast.sucesso('Coluna criada.');
        this.refrescarColunas();
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  renomearColuna(c: ColunaQuadro): void {
    const bruto = window.prompt('Novo nome da coluna:', c.nome);
    if (bruto === null) {
      return;
    }
    const nome = bruto.trim();
    if (nome.length < 2 || nome === c.nome) {
      return;
    }
    this.api.atualizarColuna(this.projetoId, c.id, { nome }).subscribe({
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

  excluirColuna(c: ColunaQuadro): void {
    if (!window.confirm(`Excluir a coluna "${c.nome}"? Ela precisa estar vazia.`)) {
      return;
    }
    this.api.excluirColuna(this.projetoId, c.id).subscribe({
      next: () => {
        this.toast.sucesso('Coluna excluída.');
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

  editarEtiqueta(e: Etiqueta): void {
    const nome = window.prompt('Novo nome da etiqueta:', e.nome);
    if (nome === null) {
      return;
    }
    const cor = window.prompt('Nova cor (#RRGGBB):', e.cor) ?? e.cor;
    const limpa = nome.trim();
    if (limpa.length < 2) {
      return;
    }
    this.api.editarEtiqueta(this.projetoId, e.id, { nome: limpa, cor }).subscribe({
      next: () => {
        this.refrescarEtiquetas();
        this.toast.sucesso('Etiqueta atualizada.');
      },
      error: erro => this.toast.erro(mensagemDeErro(erro)),
    });
  }

  excluirEtiqueta(e: Etiqueta): void {
    if (!window.confirm(`Excluir a etiqueta "${e.nome}"? Ela sai das tarefas onde está ligada.`)) {
      return;
    }
    this.api.excluirEtiqueta(this.projetoId, e.id).subscribe({
      next: () => {
        this.refrescarEtiquetas();
        this.toast.sucesso('Etiqueta excluída.');
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

  // ------------------------------------------------------------ helpers

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
}
