package org.carebridge.api.security;

import org.carebridge.api.identity.IdentityStore;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/** Loads accounts by handle. Inactive accounts and accounts without a password are disabled. */
@Service
public class AccountUserDetailsService implements UserDetailsService {

    private final IdentityStore identities;

    public AccountUserDetailsService(IdentityStore identities) {
        this.identities = identities;
    }

    @Override
    public UserDetails loadUserByUsername(String handle) {
        return identities.findByHandle(handle)
                .map(CareBridgeUser::new)
                .orElseThrow(() -> new UsernameNotFoundException("unknown account"));
    }
}
