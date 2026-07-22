package com.pavewatch.demo.repositorio;

import com.pavewatch.demo.modelo.EventoPavewatch;
import org.locationtech.jts.geom.Point;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
// IMPORTACIONES NECESARIAS (Pon esto arriba con los demás import)
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventoPavewatchRepositorio extends JpaRepository<EventoPavewatch, Long> {

    /**
     * ALGORITMO DE CONFIRMACIÓN (Radar Espacial):
     * Busca si ya existe un bache en un radio exacto de metros desde un nuevo punto GPS.
     * ST_DWithin evalúa la distancia en metros porque usamos el tipo 'geography'.
     * ST_Distance lo ordena para traernos el más cercano si hay varios en ese radio.
     */
    @Query(value = "SELECT * FROM eventos_pavewatch e " +
            "WHERE ST_DWithin(e.ubicacion, :punto, :radioMetros) " +
            "ORDER BY ST_Distance(e.ubicacion, :punto) LIMIT 1",
            nativeQuery = true)
    Optional<EventoPavewatch> buscarBacheCercano(@Param("punto") Point punto,
                                                 @Param("radioMetros") double radioMetros);

    /**
     * PARA EL MAPA DE CALOR:
     * Solo traemos los baches que ya fueron verificados o que tienen varias confirmaciones
     * para no ensuciar el mapa con falsos positivos.
     */
    @Query("SELECT e FROM EventoPavewatch e WHERE e.verificado = true OR e.contadorConfirmaciones >= 2")
    List<EventoPavewatch> obtenerBachesParaMapaCalor();

    /**
     * PARA LOS GRÁFICOS DE ANDRÉS (Estadísticas rápidas):
     * Cuenta cuántos baches hay según la clasificación de la inteligencia artificial.
     */
    @Query("SELECT e.clasificacionIa, COUNT(e) FROM EventoPavewatch e GROUP BY e.clasificacionIa")
    List<Object[]> contarBachesPorClasificacionIa();

    /**
     * ÍNDICE DE SALUD VIAL POR DISTRITO (Plus Concurso):
     * Agrupa los reportes por distrito, cuenta los confirmados vs. pendientes
     * y calcula un porcentaje de calidad de pavimento para las autoridades.
     */
    @Query(value = "SELECT e.distrito AS distrito, " +
            "COUNT(*) AS total_alertas, " +
            "SUM(CASE WHEN e.verificado = true THEN 1 ELSE 0 END) AS baches_confirmados, " +
            "SUM(CASE WHEN e.verificado = false THEN 1 ELSE 0 END) AS baches_pendientes, " +
            "ROUND(GREATEST(0.0, 100.0 - (SUM(CASE WHEN e.verificado = true THEN 1 ELSE 0 END) * 3.5) - (SUM(CASE WHEN e.verificado = false THEN 1 ELSE 0 END) * 1.5)), 2) AS indice_salud_vial " +
            "FROM eventos_pavewatch e " +
            "WHERE e.distrito IS NOT NULL " +
            "GROUP BY e.distrito " +
            "ORDER BY indice_salud_vial ASC",
            nativeQuery = true)
    List<Object[]> obtenerSaludVialPorDistrito();

    // CONEXIÓN CON PYTHON: Para verificar el bache desde la IA
    @Modifying
    @Transactional
    @Query("UPDATE EventoPavewatch e SET e.verificado = :estado, e.clasificacionIa = :clasificacion WHERE e.id = :id")
    void actualizarEstadoYClasificacion(@Param("id") Long id, @Param("estado") boolean estado, @Param("clasificacion") String clasificacion);

    // CONEXIÓN CON PYTHON: Para buscar baches que la IA aún no revisa
    List<EventoPavewatch> findByClasificacionIa(String clasificacionIa);
}