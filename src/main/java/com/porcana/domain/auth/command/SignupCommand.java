package com.porcana.domain.auth.command;

import com.porcana.domain.auth.dto.SignupRequest;
import com.porcana.domain.user.entity.User;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SignupCommand {
    private final String email;
    private final String password;
    private final String nickname;
    private final User.AuthProvider provider;

    public static SignupCommand from(SignupRequest request) {
        return SignupCommand.builder()
                .email(request.email())
                .password(request.password())
                .nickname(request.nickname())
                .provider(User.AuthProvider.EMAIL)
                .build();
    }
}