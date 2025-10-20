package config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.reactive.EnableWebFluxSecurity;
import org.springframework.security.config.web.server.ServerHttpSecurity;
import org.springframework.security.web.server.SecurityWebFilterChain;

@Configuration
@EnableWebFluxSecurity
public class SecurityConfig {

    @Bean
    public SecurityWebFilterChain springSecurityFilterChain(ServerHttpSecurity serverHttpSecurity) {
        serverHttpSecurity
                .csrf(ServerHttpSecurity.CsrfSpec::disable) //Tắt CSRF (phòng chống cross-site request forgery)
                .authorizeExchange(exchange ->
                        //để các microservice khác (product-service, order-service...)
                        // có thể tự do giao tiếp và đăng ký với Eureka Server thông qua Gateway
                        exchange.pathMatchers("/eureka/**")
                                .permitAll()
                                //Bắt tất cả các yêu cầu còn lại không khớp với quy tắc nào ở trên
                                .anyExchange()
                                //Bắt buộc yêu cầu phải được xác thực
                                .authenticated())
                //tìm một JSON Web Token (JWT) trong header Authorization của yêu cầu
                //tìm đến thuộc tính spring.security.oauth2.resourceserver.jwt.issuer-uri
                //trong file application.properties
                .oauth2ResourceServer(spec -> spec.jwt(Customizer.withDefaults()));
        return serverHttpSecurity.build();
    }
}
