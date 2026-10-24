package com.flowtask.integracao;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Teste de integração do fluxo principal (H2 em memória + Flyway + MockMvc):
 * cadastro/login, projeto, convite, permissões por papel, quadro, comentário,
 * etiquetas, dashboard, notificações, histórico e estados do projeto.
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FluxoPrincipalTest {

    private static final String SENHA = "senha1234";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private ObjectMapper mapper;

    // ------------------------------------------------------------- utilidades

    private MvcResult chama(String token, MockHttpServletRequestBuilder builder) throws Exception {
        if (token != null) {
            builder.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        return mvc.perform(builder.accept(MediaType.APPLICATION_JSON)).andReturn();
    }

    private MvcResult postJson(String token, String url, String corpo) throws Exception {
        return chama(token, post(url).contentType(MediaType.APPLICATION_JSON)
                .content(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    private MvcResult putJson(String token, String url, String corpo) throws Exception {
        return chama(token, put(url).contentType(MediaType.APPLICATION_JSON)
                .content(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    private MvcResult patchJson(String token, String url, String corpo) throws Exception {
        return chama(token, patch(url).contentType(MediaType.APPLICATION_JSON)
                .content(corpo.getBytes(StandardCharsets.UTF_8)));
    }

    private MvcResult deleteEm(String token, String url) throws Exception {
        return chama(token, delete(url));
    }

    private JsonNode json(MvcResult resultado) throws Exception {
        return mapper.readTree(resultado.getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String registrarELogar(String nome, String email) throws Exception {
        MvcResult registro = postJson(null, "/api/auth/register", """
                {"nome":"%s","email":"%s","senha":"%s"}
                """.formatted(nome, email, SENHA));
        assertEquals(201, registro.getResponse().getStatus(), "cadastro deveria criar a conta");

        MvcResult login = postJson(null, "/api/auth/login", """
                {"email":"%s","senha":"%s"}
                """.formatted(email, SENHA));
        assertEquals(200, login.getResponse().getStatus(), "login deveria autenticar");
        return json(login).get("token").asText();
    }

    // ------------------------------------------------------------------ testes

    /** Fluxo ponta a ponta com dois usuários, cobrindo permissões e colaboração. */
    @Test
    @Order(1)
    void fluxoCompletoDeColaboracao() throws Exception {
        String sufixo = UUID.randomUUID().toString().substring(0, 8);
        String emailA = "ana-" + sufixo + "@teste.dev";
        String emailB = "bruno-" + sufixo + "@teste.dev";

        // 1. Contas: cadastro, login e me ---------------------------------
        String tokenA = registrarELogar("Ana Souza", emailA);
        String tokenB = registrarELogar("Bruno Lima", emailB);

        // e-mail duplicado → 409; login com senha errada → 401; senha fraca → 400
        assertEquals(409, postJson(null, "/api/auth/register", """
                {"nome":"Outra Ana","email":"%s","senha":"%s"}
                """.formatted(emailA, SENHA)).getResponse().getStatus());
        assertEquals(401, postJson(null, "/api/auth/login", """
                {"email":"%s","senha":"errada999"}
                """.formatted(emailA)).getResponse().getStatus());
        assertEquals(400, postJson(null, "/api/auth/register", """
                {"nome":"Fraca","email":"fraca-%s@teste.dev","senha":"123"}
                """.formatted(sufixo)).getResponse().getStatus());

        JsonNode euB = json(chama(tokenB, get("/api/auth/me")));
        long idB = euB.get("id").asLong();
        assertEquals(emailB, euB.get("email").asText());

        // 2. Projeto criado pela dona Ana ----------------------------------
        MvcResult criacaoProjeto = postJson(tokenA, "/api/projects", """
                {"nome":"Portal interno","descricao":"Backend do portal","prazoFinal":"2027-01-31"}
                """);
        assertEquals(201, criacaoProjeto.getResponse().getStatus());
        JsonNode projeto = json(criacaoProjeto);
        long projetoId = projeto.get("id").asLong();
        assertEquals("OWNER", projeto.get("papelUsuario").asText());

        // 3. Isolamento: sem vínculo = 404; anônimo = 401 -------------------
        assertEquals(404, chama(tokenB, get("/api/projects/" + projetoId)).getResponse().getStatus());
        assertEquals(404, chama(tokenB, get("/api/projects/" + projetoId + "/board")).getResponse().getStatus());
        assertEquals(401, chama(null, get("/api/projects/" + projetoId)).getResponse().getStatus());

        // 4. Quadro padrão com 4 colunas -----------------------------------
        JsonNode quadro = json(chama(tokenA, get("/api/projects/" + projetoId + "/board")));
        Map<String, JsonNode> colunasPorNome = new HashMap<>();
        for (JsonNode c : quadro.get("colunas")) {
            colunasPorNome.put(c.get("nome").asText(), c);
        }
        assertEquals(4, colunasPorNome.size());
        assertTrue(colunasPorNome.get("CONCLUÍDO").get("colunaConclusao").asBoolean());
        long colunaAFazer = colunasPorNome.get("A FAZER").get("id").asLong();
        long colunaAndamento = colunasPorNome.get("EM ANDAMENTO").get("id").asLong();
        long colunaRevisao = colunasPorNome.get("EM REVISÃO").get("id").asLong();
        long colunaConcluido = colunasPorNome.get("CONCLUÍDO").get("id").asLong();

        // 5. Convite por e-mail e aceite -----------------------------------
        MvcResult criacaoConvite = postJson(tokenA, "/api/projects/" + projetoId + "/invitations", """
                {"email":"%s","funcaoProposta":"MEMBER"}
                """.formatted(emailB));
        assertEquals(201, criacaoConvite.getResponse().getStatus());
        JsonNode convite = json(criacaoConvite);
        long conviteId = convite.get("id").asLong();
        assertEquals("PENDENTE", convite.get("estado").asText());
        assertTrue(convite.get("emailCadastrado").asBoolean());

        JsonNode meusConvites = json(chama(tokenB, get("/api/invitations/me")));
        assertEquals(1, meusConvites.size());
        assertEquals(conviteId, meusConvites.get(0).get("id").asLong());

        JsonNode aceito = json(patchJson(tokenB, "/api/invitations/" + conviteId + "/accept", ""));
        assertEquals("ACEITO", aceito.get("estado").asText());

        // 6. Bruno agora é integrante MEMBER --------------------------------
        JsonNode detalheB = json(chama(tokenB, get("/api/projects/" + projetoId)));
        assertEquals("MEMBER", detalheB.get("papelUsuario").asText());
        assertEquals(2, detalheB.get("membros").size());

        // 7. MEMBER não tem permissões gerenciais ---------------------------
        assertEquals(403, postJson(tokenB, "/api/projects/" + projetoId + "/tasks", """
                {"titulo":"Tarefa proibida","colunaId":%d}
                """.formatted(colunaAFazer)).getResponse().getStatus());
        assertEquals(403, putJson(tokenB, "/api/projects/" + projetoId, """
                {"nome":"Nome alheio","descricao":"x"}
                """.formatted()).getResponse().getStatus());
        assertEquals(403, postJson(tokenB, "/api/projects/" + projetoId + "/columns", """
                {"nome":"COLUNA PROIBIDA"}
                """).getResponse().getStatus());
        assertEquals(403, postJson(tokenB, "/api/projects/" + projetoId + "/labels", """
                {"nome":"Proibida","cor":"#8293FF"}
                """).getResponse().getStatus());
        assertEquals(403, chama(tokenB, get("/api/projects/" + projetoId + "/columns")).getResponse().getStatus());

        // 8. Ana (OWNER) cria tarefa e define Bruno como responsável --------
        MvcResult criacaoTarefa = postJson(tokenA, "/api/projects/" + projetoId + "/tasks", """
                {"titulo":"Configurar pipeline de CI","descricao":"Instalar runners","colunaId":%d,"prioridade":"ALTA"}
                """.formatted(colunaAFazer));
        assertEquals(201, criacaoTarefa.getResponse().getStatus());
        JsonNode tarefa = json(criacaoTarefa);
        long tarefaId = tarefa.get("id").asLong();
        assertEquals("ALTA", tarefa.get("prioridade").asText());
        assertEquals("A FAZER", tarefa.get("colunaNome").asText());

        JsonNode atribuida = json(patchJson(tokenA, "/api/tasks/" + tarefaId + "/assignee",
                "{\"responsavelId\":%d}".formatted(idB)));
        assertEquals(idB, atribuida.get("responsavel").get("id").asLong());

        // 9. O responsável MEMBER pode editar textos e etiquetas ------------
        MvcResult criacaoEtiqueta = postJson(tokenA, "/api/projects/" + projetoId + "/labels", """
                {"nome":"Infra","cor":"#8293FF"}
                """);
        assertEquals(201, criacaoEtiqueta.getResponse().getStatus());
        long etiquetaId = json(criacaoEtiqueta).get("id").asLong();

        JsonNode comEtiquetas = json(patchJson(tokenB, "/api/tasks/" + tarefaId + "/labels",
                "{\"etiquetasIds\":[%d]}".formatted(etiquetaId)));
        assertEquals(1, comEtiquetas.get("etiquetas").size());
        assertEquals("Infra", comEtiquetas.get("etiquetas").get(0).get("nome").asText());

        // 10. Movimentação no quadro (também liberada para MEMBER) ----------
        JsonNode movida = json(patchJson(tokenB, "/api/tasks/" + tarefaId + "/move",
                "{\"colunaId\":%d,\"indice\":0}".formatted(colunaAndamento)));
        assertEquals("EM ANDAMENTO", movida.get("colunaNome").asText());
        assertFalse(movida.get("concluida").asBoolean());

        // 11. Comentário ----------------------------------------------------
        MvcResult criacaoComentario = postJson(tokenB, "/api/tasks/" + tarefaId + "/comments", """
                {"conteudo":"Setup concluído, aguardando revisão."}
                """);
        assertEquals(201, criacaoComentario.getResponse().getStatus());
        JsonNode comentarios = json(chama(tokenA, get("/api/tasks/" + tarefaId + "/comments")));
        assertEquals(1, comentarios.size());
        assertEquals("Bruno Lima", comentarios.get(0).get("autor").get("nome").asText());

        // 12. Exclusão bloqueada por comentários → 409 ----------------------
        assertEquals(409, deleteEm(tokenA, "/api/tasks/" + tarefaId).getResponse().getStatus());

        // 13. Edição de textos pelo responsável -----------------------------
        JsonNode editada = json(putJson(tokenB, "/api/tasks/" + tarefaId, """
                {"titulo":"Configurar pipeline de CI v2","descricao":"Runners instalados"}
                """));
        assertEquals("Configurar pipeline de CI v2", editada.get("titulo").asText());

        // 14. Mover para a coluna de conclusão grava dataConclusao ----------
        JsonNode concluida = json(patchJson(tokenB, "/api/tasks/" + tarefaId + "/move",
                "{\"colunaId\":%d,\"indice\":0}".formatted(colunaConcluido)));
        assertTrue(concluida.get("concluida").asBoolean());
        assertNotNull(concluida.get("dataConclusao"));

        // 15. Minhas tarefas inclui a tarefa assignada ----------------------
        boolean achou = false;
        for (JsonNode t : json(chama(tokenB, get("/api/tasks/me")))) {
            if (t.get("id").asLong() == tarefaId) {
                achou = true;
            }
        }
        assertTrue(achou, "tarefa assignada deveria aparecer em /api/tasks/me");

        // 16. Dashboard ------------------------------------------------------
        JsonNode resumo = json(chama(tokenB, get("/api/dashboard/summary")));
        assertTrue(resumo.get("tarefasConcluidasPeriodo").asLong() >= 1);
        // Projeto recém-criado está PLANEJADO (não ATIVO) neste momento do fluxo:
        assertEquals(0, resumo.get("projetosAtivos").asInt());

        JsonNode resumoProjeto = json(chama(tokenA, get("/api/projects/" + projetoId + "/dashboard")));
        boolean colunaContada = false;
        for (JsonNode contagem : resumoProjeto.get("porColuna")) {
            if ("CONCLUÍDO".equals(contagem.get("nome").asText()) && contagem.get("quantidade").asLong() >= 1) {
                colunaContada = true;
            }
        }
        assertTrue(colunaContada, "a tarefa concluída deveria constar no painel do projeto");

        // 17. Notificações: atribuição gerou aviso ---------------------------
        JsonNode notificacoes = json(chama(tokenB, get("/api/notifications?naoLidas=true")));
        assertTrue(notificacoes.get("total").asLong() >= 1);
        long notificacaoId = notificacoes.get("conteudo").get(0).get("id").asLong();

        // O convite também notifica (CONVITE_RECEBIDO) + a atribuição da tarefa:
        assertTrue(json(chama(tokenB, get("/api/notifications/count"))).get("naoLidas").asInt() >= 2,
                "convite e atribuição deveriam ter gerado notificações");
        JsonNode lida = json(patchJson(tokenB, "/api/notifications/" + notificacaoId + "/read", ""));
        assertTrue(lida.get("lida").asBoolean());
        patchJson(tokenB, "/api/notifications/read-all", "");
        assertEquals(0, json(chama(tokenB, get("/api/notifications/count"))).get("naoLidas").asInt());

        // 18. Histórico de atividades (somente leitura) ----------------------
        JsonNode atividades = json(chama(tokenA, get("/api/projects/" + projetoId + "/activities")));
        assertTrue(atividades.get("total").asLong() >= 4, "histórico deveria registrar projeto, convite, tarefa e movimento");
        assertTrue(json(chama(tokenA, get("/api/tasks/" + tarefaId + "/activities"))).get("total").asLong() >= 2);

        // 19. Segunda tarefa sem comentários pode ser excluída por OWNER ------
        MvcResult criacao2 = postJson(tokenA, "/api/projects/" + projetoId + "/tasks", """
                {"titulo":"Levantar métricas","colunaId":%d}
                """.formatted(colunaRevisao));
        assertEquals(201, criacao2.getResponse().getStatus());
        long tarefa2Id = json(criacao2).get("id").asLong();
        assertEquals(204, deleteEm(tokenA, "/api/tasks/" + tarefa2Id).getResponse().getStatus());
        assertEquals(404, chama(tokenA, get("/api/tasks/" + tarefa2Id)).getResponse().getStatus());

        // 20. Convite para conta inexistente pode ser cancelado --------------
        String emailSemConta = "semconta-" + sufixo + "@teste.dev";
        MvcResult convite2 = postJson(tokenA, "/api/projects/" + projetoId + "/invitations", """
                {"email":"%s","funcaoProposta":"MANAGER"}
                """.formatted(emailSemConta));
        assertEquals(201, convite2.getResponse().getStatus());
        assertFalse(json(convite2).get("emailCadastrado").asBoolean());
        long convite2Id = json(convite2).get("id").asLong();
        assertEquals(204, chama(tokenA, patch("/api/projects/" + projetoId + "/invitations/"
                + convite2Id + "/cancel")).getResponse().getStatus());
        boolean cancelado = false;
        for (JsonNode c : json(chama(tokenA, get("/api/projects/" + projetoId + "/invitations")))) {
            if (c.get("id").asLong() == convite2Id && "CANCELADO".equals(c.get("estado").asText())) {
                cancelado = true;
            }
        }
        assertTrue(cancelado, "convite cancelado deveria constar como CANCELADO");

        // 21. Transferência de propriedade para Bruno -------------------------
        JsonNode membros = json(chama(tokenA, get("/api/projects/" + projetoId + "/members")));
        long membroIdB = 0;
        for (JsonNode m : membros) {
            if (emailB.equals(m.get("usuario").get("email").asText())) {
                membroIdB = m.get("id").asLong();
            }
        }
        assertTrue(membroIdB > 0);

        JsonNode novoDono = json(patchJson(tokenA, "/api/projects/" + projetoId + "/members/"
                + membroIdB + "/role", "{\"funcao\":\"OWNER\"}"));
        assertEquals("OWNER", novoDono.get("funcao").asText());

        JsonNode detalheA = json(chama(tokenA, get("/api/projects/" + projetoId)));
        assertEquals("MANAGER", detalheA.get("papelUsuario").asText());
        assertEquals(emailB, detalheA.get("dono").get("email").asText());

        // ex-OWNER virou MANAGER: criar coluna agora é proibido
        assertEquals(403, postJson(tokenA, "/api/projects/" + projetoId + "/columns", """
                {"nome":"COLUNA DO EX-DONO"}
                """).getResponse().getStatus());

        // 22. Projeto arquivado bloqueia movimentação; restaurado volta a aceitar
        assertEquals(200, patchJson(tokenB, "/api/projects/" + projetoId + "/status",
                "{\"estado\":\"ARQUIVADO\"}").getResponse().getStatus());
        assertEquals(400, patchJson(tokenB, "/api/tasks/" + tarefaId + "/move",
                "{\"colunaId\":%d,\"indice\":0}".formatted(colunaAndamento)).getResponse().getStatus());
        assertEquals(200, patchJson(tokenB, "/api/projects/" + projetoId + "/status",
                "{\"estado\":\"ATIVO\"}").getResponse().getStatus());
        assertTrue(json(chama(tokenB, get("/api/dashboard/summary"))).get("projetosAtivos").asLong() >= 1,
                "projeto ATIVO deveria contar como ativo no dashboard");

        // 23. Quadro final: a tarefa movida está em CONCLUÍDO -----------------
        JsonNode quadroFinal = json(chama(tokenB, get("/api/projects/" + projetoId + "/board")));
        boolean emConcluido = false;
        for (JsonNode c : quadroFinal.get("colunas")) {
            if (c.get("id").asLong() == colunaConcluido && c.get("tarefas").size() == 1) {
                emConcluido = true;
            }
        }
        assertTrue(emConcluido, "tarefa movida deveria estar na coluna de conclusão");
    }

    /** Endpoints protegidos recusam requisições sem token. */
    @Test
    @Order(2)
    void endpointsProtegidosExigemAutenticacao() throws Exception {
        assertEquals(401, chama(null, get("/api/auth/me")).getResponse().getStatus());
        assertEquals(401, chama(null, get("/api/projects")).getResponse().getStatus());
        assertEquals(401, chama(null, get("/api/dashboard/summary")).getResponse().getStatus());
        assertEquals(401, postJson(null, "/api/projects", "{\"nome\":\"Anônimo\"}")
                .getResponse().getStatus());
    }
}
