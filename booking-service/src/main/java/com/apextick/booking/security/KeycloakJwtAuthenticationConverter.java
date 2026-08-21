package com.apextick.booking.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Collectors;

/** Maps Keycloak {@code realm_access.roles} to {@code ROLE_*} authorities; principal name is the {@code sub}. */
public class KeycloakJwtAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        Collection<GrantedAuthority> authorities = realmRoles(jwt).stream()
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role.toUpperCase(Locale.ROOT)))
                .collect(Collectors.toUnmodifiableSet());
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }

    @SuppressWarnings("unchecked")
    private Collection<String> realmRoles(Jwt jwt) {
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> raw) {
            return raw.stream().map(String::valueOf).toList();
        }
        return List.of();
    }
}
