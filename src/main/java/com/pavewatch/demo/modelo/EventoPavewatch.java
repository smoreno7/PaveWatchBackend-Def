package com.pavewatch.demo.modelo;

import jakarta.persistence.*;
import org.locationtech.jts.geom.Point;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "eventos_pavewatch")
public class EventoPavewatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(columnDefinition = "geography(Point,4326)")
    private Point ubicacion;

    private BigDecimal severidad;

    @Column(name = "fecha_deteccion", insertable = false, updatable = false)
    private LocalDateTime fechaDeteccion;

    @Column(name = "reportado_por")
    private String reportadoPor;

    @Column(name = "url_foto")
    private String urlFoto;

    private Boolean verificado = false; // Por defecto nace como falso hasta que el algoritmo lo confirme

    // --- NUEVOS CAMPOS PARA EL ALGORITMO Y LA IA DE OPENCV ---

    @Column(name = "contador_confirmaciones")
    private Integer contadorConfirmaciones = 1; // Nace con 1 (el usuario que lo descubrió)

    @Column(name = "clasificacion_ia")
    private String clasificacionIa = "SIN_ANALIZAR"; // Puede ser: LEVE, MODERADO, CRATER

    @Column(name = "origen_deteccion")
    private String origenDeteccion = "SENSOR_IMU"; // SENSOR_IMU, CAMARA_IA, HIBRIDO

    // --- GETTERS Y SETTERS ---

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Point getUbicacion() { return ubicacion; }
    public void setUbicacion(Point ubicacion) { this.ubicacion = ubicacion; }

    public BigDecimal getSeveridad() { return severidad; }
    public void setSeveridad(BigDecimal severidad) { this.severidad = severidad; }

    public LocalDateTime getFechaDeteccion() { return fechaDeteccion; }
    public void setFechaDeteccion(LocalDateTime fechaDeteccion) { this.fechaDeteccion = fechaDeteccion; }

    public String getReportadoPor() { return reportadoPor; }
    public void setReportadoPor(String reportadoPor) { this.reportadoPor = reportadoPor; }

    public String getUrlFoto() { return urlFoto; }
    public void setUrlFoto(String urlFoto) { this.urlFoto = urlFoto; }

    public Boolean getVerificado() { return verificado; }
    public void setVerificado(Boolean verificado) { this.verificado = verificado; }

    // Getters y Setters de los nuevos campos
    public Integer getContadorConfirmaciones() { return contadorConfirmaciones; }
    public void setContadorConfirmaciones(Integer contadorConfirmaciones) { this.contadorConfirmaciones = contadorConfirmaciones; }

    public String getClasificacionIa() { return clasificacionIa; }
    public void setClasificacionIa(String clasificacionIa) { this.clasificacionIa = clasificacionIa; }

    public String getOrigenDeteccion() { return origenDeteccion; }
    public void setOrigenDeteccion(String origenDeteccion) { this.origenDeteccion = origenDeteccion; }
}