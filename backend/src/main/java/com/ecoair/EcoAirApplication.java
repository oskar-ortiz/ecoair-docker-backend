package com.ecoair;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import com.ecoair.alerta.AlertaAmbiental;
import com.ecoair.alerta.AlertaAmbientalRepository;

@SpringBootApplication
public class EcoAirApplication {
  public static void main(String[] args) { SpringApplication.run(EcoAirApplication.class, args); }
  @Bean CommandLineRunner seed(AlertaAmbientalRepository repository) { return args -> {
    if (repository.count() == 0) {
      repository.save(new AlertaAmbiental(null, "Centro", "PM2.5", 18.4, "MODERADA", "2026-10-20T08:00:00Z"));
      repository.save(new AlertaAmbiental(null, "Norte", "NO2", 42.1, "BAJA", "2026-10-20T08:05:00Z"));
    }
  }; }
}
