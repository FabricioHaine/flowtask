# FlowTask

Plataforma full-stack de gestão de projetos e equipes com quadro **Kanban**, controle de acesso por papéis, comentários, histórico de atividades e notificações.

- **Backend:** Java 21 + Spring Boot 3.5.7 (Web, Security/JWT, JPA, Validation) + PostgreSQL + Flyway
- **Frontend:** Angular 20 (standalone, signals) + PrimeNG (tema customizado) + PrimeIcons
- **Testes:** JUnit 5 + Mockito (backend), Karma + Jasmine em ChromeHeadless (frontend)

O acesso ao quadro é sempre decidido **no backend**: um usuário sem vínculo com o projeto recebe `404`, quem tem vínculo mas papel insuficiente recebe `403`. O histórico grava sempre o **ator autenticado**, nunca dados do cliente.

---

## Funcionalidades

| Área | O que entrega |
| --- | --- |
| Projetos | Criação (OWNER recebe colunas padrão A FAZER / EM ANDAMENTO / EM REVISÃO / CONCLUÍDO), busca, filtro por estado, ordenação, progresso % |
| Quadro Kanban | Colunas (reordenar, renomear, arquivar, excluir se vazia, marcar coluna de conclusão), cartões com `@angular/cdk` drag & drop e rollback otimista |
| Tarefas | Título, descrição, prioridade (BAIXA→CRÍTICA), prazo, responsável, etiquetas, arquivar, movimentar (conclui/reabre pela coluna marcada como conclusão), exclusão trancada quando há comentários |
| Papéis | OWNER (tudo), MANAGER (tarefas, prioridade, prazo, etiquetas, convites), MEMBER (move tarefas, comenta, edita textos das próprias tarefas), permissão extra para o responsável |
| Equipe | Convite por e-mail (exige conta com o mesmo e-mail para aceitar; expira em 7 dias), troca de função, remoção e **transferência de propriedade** |
| Colaboração | Comentários (editar/apagar os próprios; OWNER pode apagar qualquer), etiquetas com cor #RRGGBB, histórico por projeto e por tarefa |
| Notificações | Sino com contagem de não lidas; eventos de atribuição, prazo, prioridade, concluída, convite aceito/recusado, removido, reaberta, estado do projeto, transferência |
| Dashboard | Projetos ativos, tarefas abertas/em andamento/atrasadas/concluídas no período (7/30/90 dias), distribuição por coluna e por prioridade, carga por responsável, prazos próximos (15 dias), atividade recente |
| Concorrência | Movimentação de tarefa com `SELECT … FOR UPDATE` (lock pessimista) + `@Version` → `409` no TratadorExcecoes |

---

## Requisitos

- JDK 24 (o projeto compila para Java 21) ou JDK 21+
- Maven 3.9+ (`./mvnw` não está incluído; use o `mvn` local)
- PostgreSQL 16+ rodando na porta 5432
- Node.js 24 + Chrome (para a suíte Karma)

## Banco de dados (uma vez)

Com um superusuário disponível:

```sql
-- scripts/criar-banco.sql (resumo)
CREATE ROLE flowtask LOGIN PASSWORD 'flowtask';
CREATE DATABASE flowtask OWNER flowtask;
```

O esquema é criado automaticamente pelo **Flyway** (`V1__esquema_flowtask.sql`, `V2__dados_iniciais`) no primeiro boot.

## Backend

```bash
cd backend
# variáveis são opcionais (defaults de desenvolvimento abaixo):
#   DB_URL=jdbc:postgresql://localhost:5432/flowtask  DB_USER=flowtask  DB_PASSWORD=flowtask
#   JWT_SECRET=<base64 64 bytes>  JWT_EXPIRACAO_SEGUNDOS=28800
#   CORS_ORIGINS=http://localhost:4200  EMAIL_HABILITADO=false
mvn spring-boot:run   # http://localhost:8080  (Swagger: /swagger-ui.html)
```

Testes do backend (47 testes, incluindo integração com H2 em modo PostgreSQL+Flyway+MockMvc):

```bash
mvn test
```

## Frontend

```bash
cd frontend
npm install
npm start            # http://localhost:4200 com proxy /api → 8080 (proxy.conf.json)
```

Testes do frontend (20 testes unitários em ChromeHeadless) e build de produção:

```bash
# Windows: aponte o Chrome para o Karma
set CHROME_BIN=C:\Program Files\Google\Chrome\Application\chrome.exe   (PowerShell: $env:CHROME_BIN=...)
npm test -- --watch=false --browsers=ChromeHeadless
npm run build
```

## Smoke E2E manual (8080 + 4200)

1. Inicie backend e frontend como acima.
2. `http://localhost:4200` → "Criar conta" (senha com 8+ caracteres, letra e número).
3. Crie um projeto; a tela do quadro aparece com 4 colunas padrão.
4. "Tokens/Equipe" (`/projetos/:id/equipe`) → convide um e-mail cadastrado e teste aceitar/recusar na tela de Notificações do outro usuário.

## Papéis e rotas principais (resumo)

| Recurso | Rotas |
| --- | --- |
| Auth | `POST /api/auth/register`, `POST /api/auth/login`, `GET/PUT /api/auth/me` |
| Projetos | `GET/POST /api/projects`, `GET/PUT /api/projects/{id}`, `PATCH /api/projects/{id}/status` |
| Quadro | `GET /api/projects/{id}/board`, `GET/POST /api/projects/{id}/columns`, `PUT/PATCH/DELETE …/columns/{id}`, `PATCH …/columns/reorder`, `PATCH …/columns/{id}/archive` |
| Tarefas | `POST/GET /api/projects/{id}/tasks`, `GET /api/tasks/me`, `GET/PUT/DELETE /api/tasks/{id}`, `PATCH …/move`, `…/assignee`, `…/priority`, `…/due-date`, `…/labels`, `…/archive` |
| Colaboração | `GET/POST /api/tasks/{id}/comments`, `PUT/DELETE /api/comments/{id}`, `GET/POST/PUT/DELETE /api/projects/{id}/labels` |
| Convites | `POST/GET /api/projects/{id}/invitations`, `PATCH …/{id}/cancel`, `GET /api/invitations/me`, `PATCH /api/invitations/{id}/accept` ou `/decline` |
| Membros | `GET /api/projects/{id}/members`, `PATCH …/{mid}/role` (função OWNER transfere), `DELETE …/{mid}` |
| Dashboard | `GET /api/dashboard/summary?periodo=30`, `GET /api/projects/{id}/dashboard` |
| Notificações | `GET /api/notifications`, `GET …/count`, `PATCH …/{id}/read`, `PATCH …/read-all` |
| Atividades | `GET /api/projects/{id}/activities`, `GET /api/tasks/{id}/activities` |

## Estrutura

```
backend/
  src/main/java/com/flowtask/
    controle/  (11 controllers REST)
    servico/   (regras de negócio: projeto, quadro, tarefa, membros, convite, etiqueta, comentário, notificação, atividade, dashboard, JWT, autenticação, e-mail)
    dominio/   entidades JPA e enums (PapelProjeto, PrioridadeTarefa, EstadoProjeto…)
    repositorio/ DTO/ excecao/ seguranca/ config/
  src/main/resources/db/migration/  Flyway
frontend/
  src/app/core/    api, models (espelhos dos DTOs), AuthStore (signals+sessionStorage), interceptor JWT, guards, permissões puras, toast, sino
  src/app/telas/   entrar, projetos, quadro (kanban + gaveta), equipe, painel, minhas tarefas, notificações
  src/app/layout/  sidebar + sino + perfil
```

## Decisões de segurança

- Senha com `^(?=.*[A-Za-z])(?=.*\d).{8,64}$` (idêntica no backend e no frontend).
- JWT em sessionStorage (schema de dev); interceptor anexa `Authorization: Bearer` e redireciona para `/entrar` em `401`.
- Permissões replicadas no frontend (`core/permissoes.ts`) apenas para UX — a autorização real vive nos serviços do backend.
- CORS restrito a `http://localhost:4200` por default; SMTP opcional (`EMAIL_HABILITADO=false` → convites entregues dentro do próprio produto).

## Limitações conhecidas

- Sem Docker (requisito do projeto): Dockerfile/compose ficam como próximos passos.
- Procura de token na URL não existe; renovação de sessão expira normalmente (1h por default).
- Upload de arquivos não faz parte do escopo.
- Emails reais exigem SMTP configurado; sem ele, o convite fica visível no produto.
