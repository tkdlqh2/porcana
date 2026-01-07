package com.porcana.domain.auth.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class LoginRequest {
    private String provider; // GOOGLE|KAKAO|EMAIL
    private String code;
    private String email;
    private String password;
}