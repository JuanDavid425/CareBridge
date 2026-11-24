package org.carebridge.api.security;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.carebridge.api.identity.Account;
import org.carebridge.api.identity.Role;
import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * The signed-in principal kept in the HTTP session: the account id, its role authority
 * ({@code ROLE_REVIEWER}) and its permission authorities ({@code proposal.review}).
 * Unit memberships are not cached here; they are read fresh when access is checked.
 */
public final class CareBridgeUser implements UserDetails, CredentialsContainer {

    private static final long serialVersionUID = 1L;

    private final UUID id;
    private final String handle;
    private final Role role;
    private final boolean enabled;
    private final List<GrantedAuthority> authorities;
    private String passwordHash;

    public CareBridgeUser(Account account) {
        this.id = account.id();
        this.handle = account.handle();
        this.role = account.role();
        this.enabled = account.canSignIn();
        this.passwordHash = account.passwordHash();
        List<GrantedAuthority> granted = new ArrayList<>();
        granted.add(new SimpleGrantedAuthority(account.role().authority()));
        account.permissions().stream().sorted().map(SimpleGrantedAuthority::new).forEach(granted::add);
        this.authorities = List.copyOf(granted);
    }

    public UUID id() {
        return id;
    }

    public Role role() {
        return role;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public String getUsername() {
        return handle;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
