package it.gestionale.api.auth;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Converte il claim dei ruoli del JWT in autorità Spring:
 * {@code ROLE_<ruolo>} per ogni ruolo noto più il codice di ogni permesso concesso.
 */
public class RoleAuthoritiesConverter implements Converter<Jwt, Collection<GrantedAuthority>> {

    public static final String ROLE_PREFIX = "ROLE_";

    private final String rolesClaim;

    public RoleAuthoritiesConverter(String rolesClaim) {
        this.rolesClaim = rolesClaim;
    }

    @Override
    public Collection<GrantedAuthority> convert(Jwt jwt) {
        Set<GrantedAuthority> authorities = new LinkedHashSet<>();
        for (Role role : roles(jwt)) {
            authorities.add(new SimpleGrantedAuthority(ROLE_PREFIX + role.code()));
            role.permissions().forEach(p -> authorities.add(new SimpleGrantedAuthority(p.code())));
        }
        return authorities;
    }

    /** Ruoli applicativi presenti nel token. */
    public Set<Role> roles(Jwt jwt) {
        List<String> codes = jwt.getClaimAsStringList(rolesClaim);
        Set<Role> roles = new LinkedHashSet<>();
        if (codes != null) {
            codes.forEach(code -> Role.fromCode(code).ifPresent(roles::add));
        }
        return roles;
    }
}
