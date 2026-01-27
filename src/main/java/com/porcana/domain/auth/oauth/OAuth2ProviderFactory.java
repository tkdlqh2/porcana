package com.porcana.domain.auth.oauth;

import com.porcana.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Factory for OAuth2 providers
 * Returns appropriate OAuth2Provider based on AuthProvider type
 */
@Component
public class OAuth2ProviderFactory {

    private final List<OAuth2Provider> providers;
    private final Map<String, OAuth2Provider> providerMap;

    public OAuth2ProviderFactory(List<OAuth2Provider> providers) {
        this.providers = providers;
        this.providerMap = providers.stream()
                .collect(Collectors.toMap(
                        OAuth2Provider::getProviderName,
                        Function.identity()
                ));
    }

    /**
     * Get OAuth2Provider for given AuthProvider
     *
     * @param authProvider AuthProvider type (GOOGLE, APPLE)
     * @return OAuth2Provider implementation
     * @throws IllegalArgumentException if provider is not supported
     */
    public OAuth2Provider getProvider(User.AuthProvider authProvider) {
        OAuth2Provider provider = providerMap.get(authProvider.name());

        if (provider == null) {
            throw new IllegalArgumentException("OAuth provider not supported: " + authProvider);
        }

        return provider;
    }
}