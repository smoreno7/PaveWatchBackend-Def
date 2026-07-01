package com.pavewatch.demo.modelo;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "eventos_pavewatch") // Le decimos exactamente que tabla mirar
public class EventoPavewatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Aquí usamos la clase Point de la librería JTS para manejar el dato espacial
    @Column(columnDefinition = "geography(Point,4326)")
    private Point ubicacion;

    private BigDecimal severidad;

    // Cambiamos el nombre de la columna en BD y la variable en Java
    @Column(name = "fecha_deteccion", insertable = false, updatable = false)
    private LocalDateTime fechaDeteccion;

    @Column(name = "reportado_por")
    private String reportadoPor;

    @Column(name = "url_foto")
    private String urlFoto;

    private Boolean verificado;

    // Getters y Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Point getUbicacion() {
        return ubicacion;
    }

    public void setUbicacion(Point ubicacion) {
        this.ubicacion = ubicacion;
    }

    public BigDecimal getSeveridad() {
        return severidad;
    }

    public void setSeveridad(BigDecimal severidad) {
        this.severidad = severidad;
    }

    public LocalDateTime getFechaDeteccion() {
        return fechaDeteccion;
    }

    public void setFechaDeteccion(LocalDateTime fechaDeteccion) {
        this.fechaDeteccion = fechaDeteccion;
    }

    public String getReportadoPor() {
        return reportadoPor;
    }

    public void setReportadoPor(String reportadoPor) {
        this.reportadoPor = reportadoPor;
    }

    public String getUrlFoto() {
        return urlFoto;
    }

    public void setUrlFoto(String urlFoto) {
        this.urlFoto = urlFoto;
    }

    public Boolean getVerificado() {
        return verificado;
    }

    public void setVerificado(Boolean verificado) {
        this.verificado = verificado;
    }
}