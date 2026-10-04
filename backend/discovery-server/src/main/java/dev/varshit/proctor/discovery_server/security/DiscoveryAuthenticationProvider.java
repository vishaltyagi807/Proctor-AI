package dev.varshit.proctor.discovery_server.security;

import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.AuthorityUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

public class DiscoveryAuthenticationProvider implements AuthenticationProvider {

    static final String ROLE = "DISCOVERY_CLIENT";

    private final byte[] usernameDigest;
    private final byte[] passwordDigest;

    public DiscoveryAuthenticationProvider(DiscoveryCredentials credentials) {
        this.usernameDigest = digest(credentials.username());
        this.passwordDigest = digest(credentials.password());
    }

    @Override
    public Authentication authenticate(Authentication authentication) {
        String username = authentication.getName();
        Object credentials = authentication.getCredentials();
        boolean usernameMatches = MessageDigest.isEqual(digest(username), usernameDigest);
        boolean passwordMatches = credentials != null
                && MessageDigest.isEqual(digest(credentials.toString()), passwordDigest);
        if (!(usernameMatches & passwordMatches)) {
            throw new BadCredentialsException("Bad credentials");
        }
        return UsernamePasswordAuthenticationToken.authenticated(username, null,
                AuthorityUtils.createAuthorityList("ROLE_" + ROLE));
    }

    @Override
    public boolean supports(Class<?> authentication) {
        return UsernamePasswordAuthenticationToken.class.isAssignableFrom(authentication);
    }

    private static byte[] digest(String value) {
        try {
            return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
