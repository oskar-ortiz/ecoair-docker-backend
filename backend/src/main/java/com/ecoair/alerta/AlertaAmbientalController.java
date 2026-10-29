package com.ecoair.alerta;

import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/alertas")
public class AlertaAmbientalController {
  private final AlertaAmbientalRepository repository;
  public AlertaAmbientalController(AlertaAmbientalRepository repository){this.repository=repository;}
  @GetMapping public List<AlertaAmbiental> listar(){return repository.findAll();}
  @PostMapping public AlertaAmbiental crear(@RequestBody AlertaAmbiental alerta){return repository.save(alerta);}
  @GetMapping("/health") public String health(){return "eco-air-backend UP";}
}
