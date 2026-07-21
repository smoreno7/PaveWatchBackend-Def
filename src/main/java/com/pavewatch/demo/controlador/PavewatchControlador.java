package com.pavewatch.demo.controlador;

import com.pavewatch.demo.modelo.EventoPavewatch;
import com.pavewatch.demo.repositorio.EventoPavewatchRepositorio;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

@RestController
@RequestMapping("/api/pavewatchs")
@CrossOrigin(origins = "*") // Permite que la app en Flutter/React y el servidor se comuniquen sin bloqueo CORS
public class PavewatchControlador {

    @Autowired
    private EventoPavewatchRepositorio repository;

    // Fábrica para crear el punto espacial de PostGIS usando el estándar GPS mundial (SRID 4326)
    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // ==========================================
    // 1. ENDPOINTS BÁSICOS Y MAPA DE CALOR
    // ==========================================

    @GetMapping
    public List<EventoPavewatch> getAllPavewatchs() {
        return repository.findAll();
    }

    /**
     * PARA EL MAPA DE CALOR (Frontend):
     * Devuelve solo los baches que tienen alta probabilidad de ser reales
     * para no ensuciar el mapa con falsas alarmas de rompemuelles o baches ya reparados.
     */
    @GetMapping("/mapa-calor")
    public List<EventoPavewatch> getMapaCalor() {
        return repository.obtenerBachesParaMapaCalor();
    }

    // ==========================================
    // 2. EL ALGORITMO DE CONFIRMACIÓN (NUEVO REGISTRO)
    // ==========================================

    /**
     * RECIBE LA TELEMETRÍA DE LA APP O EL SCRIPT DE OPENCV:
     * Aquí ejecutamos el algoritmo de confirmación espacial.
     */
    @PostMapping("/registrar")
    public ResponseEntity<EventoPavewatch> registrarOConfirmarBache(@RequestBody EventoPavewatch nuevoEvento) {

        // 1. Validamos que tengamos la ubicación del evento
        if (nuevoEvento.getUbicacion() == null) {
            return ResponseEntity.badRequest().build();
        }

        // 2. RADAR ESPACIAL: Buscamos si ya existe un bache a menos de 8 metros a la redonda
        Optional<EventoPavewatch> bacheExistenteOpt = repository.buscarBacheCercano(nuevoEvento.getUbicacion(), 4.0);

        if (bacheExistenteOpt.isPresent()) {
            // ---> CASO A: YA EXISTÍA UN BACHE CERCA (ALGORITMO DE FUSIÓN) <---
            EventoPavewatch bacheExistente = bacheExistenteOpt.get();

            // Le sumamos +1 a las confirmaciones por cercanía
            int nuevasConfirmaciones = bacheExistente.getContadorConfirmaciones() + 1;
            bacheExistente.setContadorConfirmaciones(nuevasConfirmaciones);

            // Actualizamos la severidad haciendo un promedio dinámico
            if (nuevoEvento.getSeveridad() != null && bacheExistente.getSeveridad() != null) {
                BigDecimal severidadPromedio = bacheExistente.getSeveridad()
                        .add(nuevoEvento.getSeveridad())
                        .divide(new BigDecimal(2), 2, RoundingMode.HALF_UP);
                bacheExistente.setSeveridad(severidadPromedio);
            }

            // Si el nuevo aporte trae foto de OpenCV o viene de la IA, lo fusionamos
            if (nuevoEvento.getUrlFoto() != null && !nuevoEvento.getUrlFoto().isEmpty()) {
                bacheExistente.setUrlFoto(nuevoEvento.getUrlFoto());
                bacheExistente.setClasificacionIa(nuevoEvento.getClasificacionIa());
                bacheExistente.setOrigenDeteccion("HIBRIDO"); // Confirmado por sensor + cámara
            }

            // REGLA DE VERIFICACIÓN AUTOMÁTICA:
            // Si 3 carros diferentes ya pasaron y vibraron ahí, O si OpenCV lo clasificó como CRATER/MODERADO -> ¡Confirmado!
            if (nuevasConfirmaciones >= 3 ||
                    ("CRATER".equalsIgnoreCase(bacheExistente.getClasificacionIa()) ||
                            "MODERADO".equalsIgnoreCase(bacheExistente.getClasificacionIa()))) {
                bacheExistente.setVerificado(true);
            }

            return ResponseEntity.ok(repository.save(bacheExistente));

        } else {
            // ---> CASO B: ES UN BACHE TOTALMENTE NUEVO <---
            nuevoEvento.setContadorConfirmaciones(1);

            // Si nació de un reporte visual o ya trae foto de la IA, lo podemos verificar de inmediato
            if (nuevoEvento.getUrlFoto() != null && !nuevoEvento.getUrlFoto().isEmpty() &&
                    !"SIN_ANALIZAR".equalsIgnoreCase(nuevoEvento.getClasificacionIa())) {
                nuevoEvento.setVerificado(true);
            } else {
                nuevoEvento.setVerificado(false); // Nace como "sospechoso" en espera de que pasen más carros
            }

            return ResponseEntity.ok(repository.save(nuevoEvento));
        }
    }

    // ==========================================
    // 3. ESTADÍSTICAS PARA LOS GRÁFICOS DE ANDRÉS
    // ==========================================

    /**
     * ENDPOINT DE ESTADÍSTICAS GENERALES:
     * Alimenta los gráficos del frontend con contadores en tiempo real.
     */
    @GetMapping("/estadisticas/resumen")
    public ResponseEntity<Map<String, Object>> getResumenEstadisticas() {
        List<EventoPavewatch> todos = repository.findAll();

        long totalReportados = todos.size();
        long verificados = todos.stream().filter(e -> Boolean.TRUE.equals(e.getVerificado())).count();
        long pendientes = totalReportados - verificados;

        Map<String, Object> stats = new HashMap<>();
        stats.put("total_alertas", totalReportados);
        stats.put("baches_confirmados", verificados);
        stats.put("en_revision", pendientes);
        stats.put("efectividad_porcentaje", totalReportados > 0 ? (verificados * 100.0) / totalReportados : 0);

        return ResponseEntity.ok(stats);
    }

    /**
     * GRÁFICO DE CLASIFICACIÓN VISUAL (OPENCV):
     * Devuelve la cantidad de baches divididos por Leve, Moderado y Cráter.
     */
    @GetMapping("/estadisticas/por-clasificacion-ia")
    public ResponseEntity<Map<String, Long>> getEstadisticasPorIa() {
        List<Object[]> resultados = repository.contarBachesPorClasificacionIa();
        Map<String, Long> estadisticasIa = new HashMap<>();

        for (Object[] fila : resultados) {
            String clasificacion = (String) fila[0];
            Long contador = (Long) fila[1];
            estadisticasIa.put(clasificacion != null ? clasificacion : "SIN_ANALIZAR", contador);
        }

        return ResponseEntity.ok(estadisticasIa);
    }
}