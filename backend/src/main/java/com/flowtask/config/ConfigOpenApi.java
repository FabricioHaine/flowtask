package com.flowtask.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;

/**
 * Documentação OpenAPI disponível em /swagger-ui.html.
 */
@Configuration
public class ConfigOpenApi {

    private static final String ESQUEMA_JWT = "autenticacaoJwt";

    @Bean
    public OpenAPI especificacaoOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("FlowTask API")
                        .description("API do FlowTask: gestão de projetos, quadros kanban, tarefas, "
                                + "comentários, histórico de atividades, notificações e indicadores.")
                        .version("1.0.0")
                        .contact(new Contact().name("FlowTask")))
                .addServersItem(new Server().url("http://localhost:8080"))
                .components(new Components().addSecuritySchemes(ESQUEMA_JWT,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.HTTP)
                                .scheme("bearer")
                                .bearerFormat("JWT")))
                .addSecurityItem(new SecurityRequirement().addList(ESQUEMA_JWT));
    }
}
