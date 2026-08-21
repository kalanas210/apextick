package com.apextick.booking.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/** The authenticated caller, projected from the Keycloak access token. */
public record CurrentUser(String sub, String username, String email, String name, Set<String> roles) {

    @SuppressWarnings("unchecked")
    public static CurrentUser from(Jwt jwt) {
        Set<String> roles = Set.of();
        Object realmAccess = jwt.getClaim("realm_access");
        if (realmAccess instanceof Map<?, ?> map && map.get("roles") instanceof Collection<?> raw) {
            roles = raw.stream().map(String::valueOf).collect(Collectors.toUnmodifiableSet());
        }
        return new CurrentUser(
                jwt.getSubject(),
                jwt.getClaimAsString("preferred_username"),
                jwt.getClaimAsString("email"),
                jwt.getClaimAsString("name"),
                roles);
    }

    public boolean isAdmin() {
        return roles.contains("admin");
    }
}
