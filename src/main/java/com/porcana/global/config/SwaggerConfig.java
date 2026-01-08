package com.porcana.global.config;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.Components;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class SwaggerConfig {

    @Bean
    public OpenAPI openAPI() {
        String jwt = "JWT";
        Components components = new Components()
                .addSecuritySchemes(jwt, new SecurityScheme()
                        .name(jwt)
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                );

        return new OpenAPI()
                .info(new Info()
                        .title("Porcana API")
                        .description("Portfolio + Arena: 투기장처럼 종목을 선택하여 포트폴리오를 만들고 수익률을 추적하는 서비스")
                        .version("v1.0.0")
                        .license(new License()
                                .name("CC BY-NC 4.0")
                                .url("https://creativecommons.org/licenses/by-nc/4.0/")))
                .components(components);
    }
}