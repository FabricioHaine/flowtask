/** Lista de projetos com busca, filtro de estado, ordenação e criação. */

import { ChangeDetectionStrategy, Component, OnInit, inject, signal } from '@angular/core';
import { FormsModule } from '@angular/forms';
import { Router, RouterLink } from '@angular/router';

import { ApiServico } from '../core/api';
import { AuthStore } from '../core/auth.store';
import { EstadoProjeto, RespostaProjeto, ESTADOS_PROJETO } from '../core/models';
import { ServicoToast, mensagemDeErro } from '../core/toast.service';
import {
  ROTULO_ESTADO, ROTULO_PAPEL, classeEstado, formatarDataLocal, iniciais,
} from '../core/utilidades';

@Component({
  selector: 'app-tela-projetos',
  changeDetection: ChangeDetectionStrategy.OnPush,
  imports: [FormsModule, RouterLink],
  template: `
    <div class="pagina">
      <div class="cabeca-pagina">
        <h1>Projetos</h1>
        <button class="btn" type="button" (click)="abrirCriacao()">＋ Novo projeto</button>
      </div>

      <div class="linha-filtros card">
        <input class="input busca" placeholder="Buscar por nome ou descrição…" [(ngModel)]="busca"
               name="busca" (ngModelChange)="recarregar()">
        <select class="input estado" [(ngModel)]="estado" name="estado" (ngModelChange)="recarregar()">
          <option [ngValue]="null">Todos os estados</option>
          @for (e of estados; track e) {
            <option [ngValue]="e">{{ ROTULO_ESTADO_[e] }}</option>
          }
        </select>
        <select class="input orden" [(ngModel)]="ordenacao" name="ordenacao" (ngModelChange)="recarregar()">
          <option value="">Mais recentes</option>
          <option value="NOME">Nome (A→Z)</option>
          <option value="PRAZO">Prazo</option>
        </select>
      </div>

      @if (carregando()) {
        <p class="mensagem-info">Carregando projetos…</p>
      } @else if (projetos().length === 0) {
        <div class="vazio card">
          <h3>Nenhum projeto por aqui</h3>
          <p class="mensagem-info">Crie um projeto novo para começar um quadro kanban.</p>
        </div>
      } @else {
        <div class="grade-projetos">
          @for (p of projetos(); track p.id) {
            <a class="card projeto" [routerLink]="['/projetos', p.id]">
              <div class="topo">
                <h3>{{ p.nome }}</h3>
                <span class="badge" [class]="classeEstado_(p.estado)">{{ ROTULO_ESTADO_[p.estado] }}</span>
              </div>
              @if (p.descricao) {
                <p class="desc">{{ p.descricao }}</p>
              }
              <div class="progresso" title="{{ p.progressoPct ?? 0 }}%">
                <span [style.width.%]="p.progressoPct ?? 0"></span>
              </div>
              <div class="meta">
                <span>{{ p.tarefasConcluidas ?? 0 }}/{{ p.totalTarefas ?? 0 }} tarefas · {{ p.progressoPct ?? 0 }}%</span>
                <span class="badge" [class]="papelClasse(p.papelUsuario)">{{ ROTULO_PAPEL_[p.papelUsuario] }}</span>
              </div>
              <div class="rodape-projeto">
                <div class="pessoas">
                  @for (m of p.membros.slice(0, 5); track m.id) {
                    <span class="avatar" [title]="m.nome">{{ iniciais_(m.nome) }}</span>
                  }
                  @if (p.membros.length > 5) {
                    <span class="mais">+{{ p.membros.length - 5 }}</span>
                  }
                </div>
                @if (p.prazoFinal) {
                  <small class="mensagem-info">Prazo: {{ formato_data(p.prazoFinal) }}</small>
                }
              </div>
            </a>
          }
        </div>
      }
    </div>

    @if (modalAberto()) {
      <div class="sombra" (click)="fecharModal()">
        <div class="modal card" (click)="$event.stopPropagation()">
          <h2>Novo projeto</h2>
          <label class="campo">
            Nome
            <input class="input" [(ngModel)]="novoNome" name="novoNome" maxlength="140"
                   placeholder="Ex.: Lançamento do app">
          </label>
          <label class="campo">
            Descrição
            <textarea class="input" [(ngModel)]="novaDescricao" name="novaDescricao" rows="3" maxlength="1000"
                      placeholder="Contexto do projeto (opcional)"></textarea>
          </label>
          <div class="par">
            <label class="campo">
              Início
              <input class="input" type="date" [(ngModel)]="novoInicio" name="novoInicio">
            </label>
            <label class="campo">
              Prazo final
              <input class="input" type="date" [(ngModel)]="novoPrazo" name="novoPrazo">
            </label>
          </div>
          @if (erro()) {
            <p class="mensagem-erro">{{ erro() }}</p>
          }
          <div class="acoes">
            <button class="btn ghost" type="button" (click)="fecharModal()">Cancelar</button>
            <button class="btn" type="button" (click)="criar()" [disabled]="salvando()">Criar projeto</button>
          </div>
        </div>
      </div>
    }
  `,
})
export class TelaProjetos implements OnInit {
  private readonly api = inject(ApiServico);
  private readonly despachador = inject(Router);
  private readonly toast = inject(ServicoToast);

  readonly projetos = signal<RespostaProjeto[]>([]);
  readonly carregando = signal(true);
  readonly modalAberto = signal(false);
  readonly salvando = signal(false);
  readonly erro = signal<string | null>(null);

  readonly estados: EstadoProjeto[] = ESTADOS_PROJETO;
  estado: EstadoProjeto | null = null;
  busca = '';
  ordenacao = '';

  novoNome = '';
  novaDescricao = '';
  novoInicio = '';
  novoPrazo = '';

  ROTULO_ESTADO_ = ROTULO_ESTADO;
  ROTULO_PAPEL_ = ROTULO_PAPEL;

  ngOnInit(): void {
    this.recarregar();
  }

  recarregar(): void {
    this.carregando.set(true);
    this.api.projetos({
      busca: this.busca.trim() || undefined,
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
        this.toast.erro(mensagemDeErro(erro));
      },
    });
  }

  abrirCriacao(): void {
    this.novoNome = '';
    this.novaDescricao = '';
    this.novoInicio = '';
    this.novoPrazo = '';
    this.erro.set(null);
    this.modalAberto.set(true);
  }

  fecharModal(): void {
    this.modalAberto.set(false);
  }

  criar(): void {
    const nome = this.novoNome.trim();
    if (nome.length < 3) {
      this.erro.set('O nome do projeto deve ter de 3 a 140 caracteres.');
      return;
    }
    this.salvando.set(true);
    this.api.criarProjeto({
      nome,
      descricao: this.novaDescricao.trim() || null,
      dataInicio: this.novoInicio || null,
      prazoFinal: this.novoPrazo || null,
    }).subscribe({
      next: projeto => {
        this.salvando.set(false);
        this.modalAberto.set(false);
        this.toast.sucesso('Projeto criado.');
        void this.despachador.navigate(['/projetos', projeto.id]);
      },
      error: erro => {
        this.salvando.set(false);
        this.erro.set(mensagemDeErro(erro));
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
