package com.porcana.domain.auth.command;

import com.porcana.domain.auth.dto.LoginRequest;
import com.porcana.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class LoginCommand {
    private final String email;
    private final String password;
    private final User.AuthProvider provider;

    public static LoginCommand from(LoginRequest request) {
        User.AuthProvider provider = request.provider() != null ?
                User.AuthProvider.valueOf(request.provider()) :
                User.AuthProvider.EMAIL;

        return LoginCommand.builder()
                .email(request.email())
                .password(request.password())
                .provider(provider)
                .build();
    }
}