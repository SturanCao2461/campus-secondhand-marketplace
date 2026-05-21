package nz.ac.waikato.campusmarketplace.config;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class OpenApiConfig {

    @Bean
    public OpenAPI campusMarketplaceOpenApi() {
        final String securitySchemeName = "cookieAuth";
        return new OpenAPI()
                .info(new Info()
                        .title("Campus Secondhand Marketplace API")
                        .description("REST API for the University of Waikato campus secondhand marketplace. " +
                                "Authentication uses an HttpOnly JWT cookie issued by /api/auth/login. " +
                                "Most endpoints under /api/** require an authenticated session; public endpoints " +
                                "are explicitly listed in SecurityConfig.")
                        .version("0.0.1-SNAPSHOT")
                        .contact(new Contact().name("Josh").email("Caoshutong01@gmail.com"))
                        .license(new License().name("MIT").url("https://opensource.org/licenses/MIT")))
                .addSecurityItem(new SecurityRequirement().addList(securitySchemeName))
                .components(new Components().addSecuritySchemes(securitySchemeName,
                        new SecurityScheme()
                                .type(SecurityScheme.Type.APIKEY)
                                .in(SecurityScheme.In.COOKIE)
                                .name("token")
                                .description("HttpOnly JWT cookie issued by /api/auth/login")));
    }
}
