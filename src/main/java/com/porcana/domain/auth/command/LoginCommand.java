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
        return LoginCommand.builder()
                .email(request.getEmail())
                .password(request.getPassword())
                .provider(User.AuthProvider.valueOf(request.getProvider()))
                .build();
    }
}