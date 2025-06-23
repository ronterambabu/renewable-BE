package com.zn;

import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**") // Apply to all endpoints
                .allowedOriginPatterns(
                    "http://localhost:*", 
                    "https://localhost:*",
                    "https://*.vercel.app",
                    "http://*.vercel.app"
                ) // Allow HTTP and HTTPS for both local and Vercel
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS") // Allowed HTTP methods
                .allowedHeaders("*") // Allow all headers including Authorization
                .allowCredentials(true) // Allow credentials (cookies, authorization headers, etc.)
                .exposedHeaders("Set-Cookie"); // Expose Set-Cookie header for frontend
    }
}

