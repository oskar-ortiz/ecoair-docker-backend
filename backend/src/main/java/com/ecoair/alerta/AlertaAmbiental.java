package com.ecoair.alerta;

import jakarta.persistence.*;

@Entity
public class AlertaAmbiental {
  @Id @GeneratedValue(strategy = GenerationType.IDENTITY) private Long id;
  private String estacion; private String tipo; private Double valor; private String severidad; private String fecha;
  protected AlertaAmbiental() {}
  public AlertaAmbiental(Long id, String estacion, String tipo, Double valor, String severidad, String fecha) { this.id=id; this.estacion=estacion; this.tipo=tipo; this.valor=valor; this.severidad=severidad; this.fecha=fecha; }
  public Long getId(){return id;} public String getEstacion(){return estacion;} public String getTipo(){return tipo;} public Double getValor(){return valor;} public String getSeveridad(){return severidad;} public String getFecha(){return fecha;}
  public void setId(Long id){this.id=id;} public void setEstacion(String v){estacion=v;} public void setTipo(String v){tipo=v;} public void setValor(Double v){valor=v;} public void setSeveridad(String v){severidad=v;} public void setFecha(String v){fecha=v;}
}
