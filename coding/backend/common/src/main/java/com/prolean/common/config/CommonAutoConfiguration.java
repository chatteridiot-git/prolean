package com.prolean.common.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.prolean.common.error.GlobalExceptionHandler;
import com.prolean.common.error.ProblemDetailsFactory;
import com.prolean.common.error.ProblemDetailsWriter;
import com.prolean.common.security.InternalKeyFilter;
import com.prolean.common.security.InternalKeyProperties;
import com.prolean.common.security.SecurityProblemSupport;
import com.prolean.common.web.RequestIdFilter;
import java.util.Arrays;
import java.util.List;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.client.RestClientCustomizer;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.AnonymousAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Cross-cutting auto-configuration shared by the four services.
 *
 * <p>The internal API chain is registered with order 1. Each service registers its public JWT chain
 * with order 2, so an internal request can never fall through to browser-token authentication.
 */
@AutoConfiguration
@EnableConfigurationProperties(InternalKeyProperties.class)
public class CommonAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailsFactory problemDetailsFactory() {
        return new ProblemDetailsFactory();
    }

    @Bean
    @ConditionalOnMissingBean
    public ProblemDetailsWriter problemDetailsWriter(ObjectMapper objectMapper) {
        return new ProblemDetailsWriter(objectMapper);
    }

    @Bean
    @ConditionalOnMissingBean
    public GlobalExceptionHandler globalExceptionHandler(ProblemDetailsFactory factory) {
        return new GlobalExceptionHandler(factory);
    }

    @Bean
    @ConditionalOnMissingBean
    public SecurityProblemSupport securityProblemSupport(ProblemDetailsFactory factory, ProblemDetailsWriter writer) {
        return new SecurityProblemSupport(factory, writer);
    }

    @Bean
    @ConditionalOnMissingBean
    public InternalKeyFilter internalKeyFilter(InternalKeyProperties properties, ProblemDetailsFactory factory,
            ProblemDetailsWriter writer) {
        return new InternalKeyFilter(properties, factory, writer);
    }

    @Bean
    public FilterRegistrationBean<RequestIdFilter> requestIdFilterRegistration() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        registration.addUrlPatterns("/*");
        return registration;
    }

    @Bean
    @ConditionalOnMissingBean
    public RestClientCustomizer internalRestClientCustomizer() {
        // Internal service-to-service calls use plain HTTP. A simple HTTP/1.1 request factory
        // avoids h2c upgrade attempts (RST_STREAM with Jetty/WireMock) and the non-daemon
        // selector threads that keep test JVMs alive after the suite has finished.
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(2_000);
        factory.setReadTimeout(3_000);
        return builder -> builder.requestFactory(factory);
    }

    @Bean
    @Order(1)
    public SecurityFilterChain internalApiSecurityFilterChain(HttpSecurity http, InternalKeyFilter internalKeyFilter,
            SecurityProblemSupport problemSupport) throws Exception {
        http.securityMatcher("/internal/**")
                .csrf(AbstractHttpConfigurer::disable)
                .cors(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize.anyRequest().hasRole("INTERNAL"))
                .addFilterBefore(internalKeyFilter, AnonymousAuthenticationFilter.class)
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(problemSupport)
                        .accessDeniedHandler(problemSupport));
        return http.build();
    }

    @Bean
    @ConditionalOnMissingBean(CorsConfigurationSource.class)
    public CorsConfigurationSource corsConfigurationSource(
            @Value("${prolean.cors.allowed-origins:http://localhost:5173}") String allowedOrigins,
            @Value("${prolean.cors.allow-credentials:false}") boolean allowCredentials) {
        List<String> origins = Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .toList();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins);
        configuration.setAllowedMethods(List.of(
                HttpMethod.GET.name(), HttpMethod.POST.name(), HttpMethod.PUT.name(),
                HttpMethod.PATCH.name(), HttpMethod.DELETE.name(), HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(List.of(
                "Authorization", "Content-Type", "X-Request-Id", "X-Internal-Key-Id", "X-Internal-Api-Key"));
        configuration.setExposedHeaders(List.of("X-Request-Id", "Idempotent-Replay",
                "X-Holiday-Coverage-Start", "X-Holiday-Coverage-End"));
        configuration.setAllowCredentials(allowCredentials);
        configuration.setMaxAge(3600L);
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
