package com.jaypay.auth.adapter.in.web;

import com.jaypay.auth.application.port.in.StaffAccountsUseCase;
import com.jaypay.auth.domain.StaffAccount;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.MediaType;
import org.springframework.security.access.hierarchicalroles.RoleHierarchy;
import org.springframework.security.access.hierarchicalroles.RoleHierarchyImpl;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.ProviderManager;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.FactorGrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.BearerTokenAuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.firewall.HttpStatusRequestRejectedHandler;
import org.springframework.security.web.firewall.RequestRejectedHandler;

import javax.crypto.spec.SecretKeySpec;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

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
    SecurityFilterChain staffRequests(HttpSecurity http, StaffAccountsUseCase staffAccounts) throws Exception {
        return http
                .csrf(AbstractHttpConfigurer::disable)
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable)
                .logout(AbstractHttpConfigurer::disable)
                .requestCache(AbstractHttpConfigurer::disable)
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(requests -> requests
                        .dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
                        .requestMatchers(GET, "/actuator/health/**", "/actuator/prometheus").permitAll()
                        .requestMatchers(GET, "/v3/api-docs/**", "/swagger-ui.html", "/swagger-ui/**").permitAll()
                        .requestMatchers(POST, "/staff/sign-in").permitAll()
                        .requestMatchers(GET, "/staff/me").hasRole(VIEWER.name())
                        .requestMatchers(PUT, "/staff/me/password").hasRole(VIEWER.name())
                        .requestMatchers(GET, "/staff/accounts").hasRole(ADMIN.name())
                        .requestMatchers(POST, "/staff/accounts").hasRole(ADMIN.name())
                        .requestMatchers(PUT, "/staff/accounts/{username}").hasRole(ADMIN.name())
                        .requestMatchers(GET, CONSOLE + "/membership/memberships/{membershipId}").hasRole(VIEWER.name())
                        .requestMatchers(GET, CONSOLE + "/membership/memberships/address/{address}").hasRole(VIEWER.name())
                        .requestMatchers(PUT, CONSOLE + "/membership/memberships/{membershipId}").hasRole(OPERATOR.name())
                        .requestMatchers(POST, CONSOLE + "/money-aggregation/money/aggregation/get-money-sum-by-address").hasRole(VIEWER.name())
                        .requestMatchers(GET, CONSOLE + "/money-query/money/query/get-money-sum-by-address/{address}").hasRole(VIEWER.name())
                        .requestMatchers(GET, CONSOLE + "/payment/payment/normal-status").hasRole(VIEWER.name())
                        .requestMatchers(POST, CONSOLE + "/payment/payment/finish-settlement").hasRole(OPERATOR.name())
                        .anyRequest().denyAll())
                .oauth2ResourceServer(resourceServer -> resourceServer
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(staffAuthentication(staffAccounts)))
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
    JwtDecoder staffTokenDecoder(@Value("${staff.token.secret}") String secret,
                                 @Value("${staff.token.issuer}") String issuer,
                                 @Value("${staff.token.audience}") String audience) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder
                .withSecretKey(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"))
                .macAlgorithm(MacAlgorithm.HS256)
                .build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<Collection<String>>(JwtClaimNames.AUD, audiences -> audiences != null && audiences.contains(audience))));
        return decoder;
    }

    @Bean
    AuthenticationManager staffSignIn(StaffAccountsUseCase staffAccounts, PasswordEncoder passwordEncoder) {
        DaoAuthenticationProvider provider = new DaoAuthenticationProvider(username -> staffAccounts.find(username)
                .map(StaffSecurityConfig::userDetails)
                .orElseThrow(() -> new UsernameNotFoundException(username)));
        provider.setPasswordEncoder(passwordEncoder);
        return new ProviderManager(provider);
    }

    @Bean
    RequestRejectedHandler forbidAmbiguousPaths() {
        return new HttpStatusRequestRejectedHandler(HttpServletResponse.SC_FORBIDDEN);
    }

    private static UserDetails userDetails(StaffAccount account) {
        return User.withUsername(account.username())
                .password(account.passwordHash())
                .roles(account.role().name())
                .disabled(!account.active())
                .build();
    }

    private static Converter<Jwt, AbstractAuthenticationToken> staffAuthentication(StaffAccountsUseCase staffAccounts) {
        return jwt -> {
            StaffAccount account = staffAccounts.find(jwt.getSubject())
                    .filter(StaffAccount::active)
                    .orElseThrow(() -> new InvalidBearerTokenException("The staff account is missing or inactive."));
            List<GrantedAuthority> authorities = new ArrayList<>();
            authorities.add(new SimpleGrantedAuthority("ROLE_" + account.role().name()));
            Optional.ofNullable(jwt.getClaimAsStringList("factors")).orElse(List.of())
                    .forEach(factor -> authorities.add(FactorGrantedAuthority.withAuthority(factor).issuedAt(jwt.getIssuedAt()).build()));
            return new JwtAuthenticationToken(jwt, authorities, account.username());
        };
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
