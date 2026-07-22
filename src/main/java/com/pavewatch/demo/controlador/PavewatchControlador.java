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
@CrossOrigin(origins = "*")
public class PavewatchControlador {

    @Autowired
    private EventoPavewatchRepositorio repository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @GetMapping
    public List<EventoPavewatch> getAllPavewatchs() {
        return repository.findAll();
    }

    @GetMapping("/mapa-calor")
    public List<EventoPavewatch> getMapaCalor() {
        return repository.obtenerBachesParaMapaCalor();
    }

    @PostMapping("/registrar")
    public ResponseEntity<EventoPavewatch> registrarOConfirmarBache(@RequestBody EventoPavewatch nuevoEvento) {

        if (nuevoEvento.getUbicacion() == null) {
            return ResponseEntity.badRequest().build();
        }

        // --- RADAR ESPACIAL AMPLIADO A 20 METROS ---
        Optional<EventoPavewatch> bacheExistenteOpt = repository.buscarBacheCercano(nuevoEvento.getUbicacion(), 20.0);

        if (bacheExistenteOpt.isPresent()) {
            EventoPavewatch bacheExistente = bacheExistenteOpt.get();

            int nuevasConfirmaciones = bacheExistente.getContadorConfirmaciones() + 1;
            bacheExistente.setContadorConfirmaciones(nuevasConfirmaciones);

            if (nuevoEvento.getSeveridad() != null && bacheExistente.getSeveridad() != null) {
                BigDecimal severidadPromedio = bacheExistente.getSeveridad()
                        .add(nuevoEvento.getSeveridad())
                        .divide(new BigDecimal(2), 2, RoundingMode.HALF_UP);
                bacheExistente.setSeveridad(severidadPromedio);
            }

            if (nuevoEvento.getUrlFoto() != null && !nuevoEvento.getUrlFoto().isEmpty()) {
                bacheExistente.setUrlFoto(nuevoEvento.getUrlFoto());
                bacheExistente.setClasificacionIa(nuevoEvento.getClasificacionIa());
                bacheExistente.setOrigenDeteccion("HIBRIDO");
            }

            if (nuevasConfirmaciones >= 3 ||
                    ("CRATER".equalsIgnoreCase(bacheExistente.getClasificacionIa()) ||
                            "MODERADO".equalsIgnoreCase(bacheExistente.getClasificacionIa()))) {
                bacheExistente.setVerificado(true);
            }

            return ResponseEntity.ok(repository.save(bacheExistente));

        } else {
            nuevoEvento.setContadorConfirmaciones(1);

            if (nuevoEvento.getUrlFoto() != null && !nuevoEvento.getUrlFoto().isEmpty() &&
                    !"SIN_ANALIZAR".equalsIgnoreCase(nuevoEvento.getClasificacionIa())) {
                nuevoEvento.setVerificado(true);
            } else {
                nuevoEvento.setVerificado(false);
            }

            return ResponseEntity.ok(repository.save(nuevoEvento));
        }
    }

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

    @GetMapping("/estadisticas/salud-por-distrito")
    public ResponseEntity<List<Map<String, Object>>> getSaludVialPorDistrito() {
        List<Object[]> resultados = repository.obtenerSaludVialPorDistrito();
        List<Map<String, Object>> reporteDistrital = new java.util.ArrayList<>();

        for (Object[] fila : resultados) {
            Map<String, Object> distritoData = new HashMap<>();
            distritoData.put("distrito", fila[0]);
            distritoData.put("total_alertas", fila[1]);
            distritoData.put("baches_confirmados", fila[2]);
            distritoData.put("baches_pendientes", fila[3]);
            distritoData.put("salud_vial_porcentaje", fila[4]);

            double salud = ((Number) fila[4]).doubleValue();
            if (salud >= 80.0) {
                distritoData.put("estado_general", "ÓPTIMO");
            } else if (salud >= 50.0) {
                distritoData.put("estado_general", "REGULAR - REQUIERE MANTENIMIENTO");
            } else {
                distritoData.put("estado_general", "CRÍTICO - EMERGENCIA VIAL");
            }

            reporteDistrital.add(distritoData);
        }

        return ResponseEntity.ok(reporteDistrital);
    }

    @GetMapping("/mapa")
    public List<EventoPavewatch> obtenerDatosParaMapa() {
        return repository.findAll();
    }

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