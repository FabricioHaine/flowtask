package com.flowtask.config;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.flowtask.seguranca.FiltroJwt;
import com.flowtask.seguranca.PontoEntradaNaoAutenticado;

/**
 * Configuração de segurança: API sem estado, autenticação JWT em todas as
 * rotas de /api, CORS restrito às origens configuradas e produtos públicos
 * apenas para Swagger e autenticação.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(PropriedadesApp.class)
public class ConfiguracaoSeguranca {

    private final FiltroJwt filtroJwt;
    private final PontoEntradaNaoAutenticado pontoEntrada;
    private final PropriedadesApp propriedades;

    public ConfiguracaoSeguranca(FiltroJwt filtroJwt, PontoEntradaNaoAutenticado pontoEntrada,
                                 PropriedadesApp propriedades) {
        this.filtroJwt = filtroJwt;
        this.pontoEntrada = pontoEntrada;
        this.propriedades = propriedades;
    }

    @Bean
    public PasswordEncoder codificadorSenhas() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    public SecurityFilterChain cadeiaFiltros(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable()) // API sem estado autenticada por cabeçalho Bearer
                .cors(cors -> cors.configurationSource(configuracaoCors()))
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .exceptionHandling(ex -> ex.authenticationEntryPoint(pontoEntrada))
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(HttpMethod.OPTIONS, "/**").permitAll()
                        .requestMatchers("/api/auth/**").permitAll()
                        .requestMatchers("/swagger-ui/**", "/swagger-ui.html").permitAll()
                        .requestMatchers("/v3/api-docs/**", "/api-docs/**", "/actuator/health").permitAll()
                        .anyRequest().authenticated())
                .addFilterBefore(filtroJwt, org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    @Bean
    public CorsConfigurationSource configuracaoCors() {
        CorsConfiguration config = new CorsConfiguration();
        List<String> origens = new ArrayList<>(Arrays.asList(
                propriedades.getSeguranca().getCors().getOrigens().split(",")));
        config.setAllowedOrigins(origens);
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        config.setAllowedHeaders(List.of("*"));
        config.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource fonte = new UrlBasedCorsConfigurationSource();
        fonte.registerCorsConfiguration("/**", config);
        return fonte;
    }
}
