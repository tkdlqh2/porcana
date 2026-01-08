package com.porcana.domain.user.command;

import com.porcana.domain.user.dto.UpdateUserRequest;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class UpdateUserCommand {
    private final String nickname;

    public static UpdateUserCommand from(UpdateUserRequest request) {
        return UpdateUserCommand.builder()
                .nickname(request.getNickname())
                .build();
    }
}