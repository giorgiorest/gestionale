package it.gestionale.api.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

class RoleAuthoritiesConverterTest {

    private final RoleAuthoritiesConverter converter = new RoleAuthoritiesConverter("roles");

    private static Jwt token(List<String> roles) {
        return Jwt.withTokenValue("t").header("alg", "none").subject("u").issuedAt(Instant.now())
                .claim("roles", roles).build();
    }

    @Test
    void mapsKnownRolesToRoleAndPermissionAuthorities() {
        var authorities = converter.convert(token(List.of("viewer", "default-roles-gestionale", "offline_access")));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_viewer", "customers:read", "resources:read", "commesse:read",
                        "resource-assignments:read", "work-activities:read", "daily-reports:read",
                        "work-entries:read");
    }

    @Test
    void adminHasEveryPermission() {
        var authorities = converter.convert(token(List.of("admin")));

        assertThat(authorities).extracting(GrantedAuthority::getAuthority)
                .contains("ROLE_admin", "customers:read", "customers:write", "assistant:use", "users:manage");
    }

    @Test
    void tokenWithoutRolesHasNoAuthorities() {
        Jwt jwt = Jwt.withTokenValue("t").header("alg", "none").subject("u").build();
        assertThat(converter.convert(jwt)).isEmpty();
    }
}
