package com.ecoair.config;

import java.util.Arrays;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class CorsConfig implements WebMvcConfigurer {
  private final String[] allowedOrigins;

  public CorsConfig(@Value("${ecoair.cors.allowed-origins:}") String allowedOrigins) {
    this.allowedOrigins = Arrays.stream(allowedOrigins.split(","))
        .map(String::trim)
        .filter(origin -> !origin.isEmpty())
        .toArray(String[]::new);
    if (Arrays.asList(this.allowedOrigins).contains("*")) {
      throw new IllegalArgumentException("CORS origins must be explicit when credentials are enabled");
    }
  }

  @Override
  public void addCorsMappings(CorsRegistry registry) {
    if (allowedOrigins.length == 0) {
      return;
    }

    registry.addMapping("/api/**")
        .allowedOrigins(allowedOrigins)
        .allowedMethods("GET", "POST", "OPTIONS")
        .allowedHeaders("Content-Type", "Authorization")
        .allowCredentials(true)
        .maxAge(3600);
  }
}
