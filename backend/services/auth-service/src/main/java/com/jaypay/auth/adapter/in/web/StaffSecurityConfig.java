package com.jaypay.auth.adapter.in.web;

import com.jaypay.auth.domain.StaffRole;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.tomcat.util.buf.EncodedSolidusHandling;
import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.boot.tomcat.TomcatConnectorCustomizer;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import static com.jaypay.auth.domain.StaffRole.ADMIN;
import static com.jaypay.auth.domain.StaffRole.OPERATOR;
import static com.jaypay.auth.domain.StaffRole.VIEWER;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;

@Configuration
class StaffSecurityConfig {

    private static final String CONSOLE = "/authorize/api";
    private static final BearerTokenAuthenticationEntryPoint BEARER = new BearerTokenAuthenticationEntryPoint();

    @Bean
    SecurityFilterChain staffRequests(HttpSecurity http) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(EndpointRequest.toAnyEndpoint().excludingLinks()).permitAll()
                        .requestMatchers(GET, CONSOLE + "/membership/memberships/{membershipId}").hasRole(VIEWER.name())
                        .requestMatchers(GET, CONSOLE + "/membership/memberships/address/{address}").hasRole(VIEWER.name())
                        .requestMatchers(PUT, CONSOLE + "/membership/memberships/{membershipId}").hasRole(OPERATOR.name())
                        .requestMatchers(POST, CONSOLE + "/money-aggregation/money/aggregation/get-money-sum-by-address").hasRole(VIEWER.name())
                        .requestMatchers(GET, CONSOLE + "/money-query/money/query/get-money-sum-by-address/{address}").hasRole(VIEWER.name())
                        .requestMatchers(GET, CONSOLE + "/payment/payment/normal-status").hasRole(VIEWER.name())
                        .requestMatchers(GET, CONSOLE + "/event-flow/flows").hasRole(VIEWER.name())
                        .requestMatchers(GET, CONSOLE + "/event-flow/flows/{flowType}/{flowId}").hasRole(VIEWER.name())
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(StaffSecurityConfig::staffAuthentication))
                        .authenticationEntryPoint(StaffSecurityConfig::unauthenticated)
                        .accessDeniedHandler(forbidden()))
                .exceptionHandling(exceptions -> exceptions
                        .authenticationEntryPoint(StaffSecurityConfig::unauthenticated)
                        .accessDeniedHandler(forbidden()))
                .build();
    }

    @Bean
    static RoleHierarchy staffRoleHierarchy() {
        return RoleHierarchyImpl.withDefaultRolePrefix()
                .role(ADMIN.name()).implies(OPERATOR.name())
                .role(OPERATOR.name()).implies(VIEWER.name())
                .build();
    }

    @Bean
    RequestRejectedHandler forbidAmbiguousPaths() {
        return new HttpStatusRequestRejectedHandler(HttpServletResponse.SC_FORBIDDEN);
    }

    @Bean
    TomcatConnectorCustomizer leaveEncodedSlashesToTheFirewall() {
        return connector -> {
            connector.setEncodedSolidusHandling(EncodedSolidusHandling.PASS_THROUGH.getValue());
            connector.setEncodedReverseSolidusHandling(EncodedSolidusHandling.PASS_THROUGH.getValue());
        };
    }

    private static AbstractAuthenticationToken staffAuthentication(Jwt jwt) {
        Collection<?> realmRoles = jwt.getClaimAsMap("realm_access") instanceof Map<String, Object> access
                && access.get("roles") instanceof Collection<?> roles ? roles : List.of();
        List<GrantedAuthority> authorities = Arrays.stream(StaffRole.values())
                .filter(role -> realmRoles.contains(role.name().toLowerCase(Locale.ROOT)))
                .<GrantedAuthority>map(role -> new SimpleGrantedAuthority("ROLE_" + role.name()))
                .toList();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getClaimAsString("preferred_username"));
    }

    private static void unauthenticated(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) throws IOException {
        BEARER.commence(request, response, exception);
        reply(response, "UNAUTHENTICATED", "Sign in again.");
    }

    private static AccessDeniedHandler forbidden() {
        return (request, response, exception) -> {
            response.setStatus(HttpServletResponse.SC_FORBIDDEN);
            reply(response, "FORBIDDEN", "Your role does not allow this.");
        };
    }

    private static void reply(HttpServletResponse response, String error, String message) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write("{\"error\":\"" + error + "\",\"message\":\"" + message + "\"}");
    }
}
