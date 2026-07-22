package com.pavewatch.demo.config;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.pavewatch.demo.modelo.EventoPavewatch;
import com.pavewatch.demo.repositorio.EventoPavewatchRepositorio;
import org.eclipse.paho.client.mqttv3.MqttConnectOptions;
import org.locationtech.jts.geom.Coordinate;
import org.locationtech.jts.geom.GeometryFactory;
import org.locationtech.jts.geom.Point;
import org.locationtech.jts.geom.PrecisionModel;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.integration.annotation.ServiceActivator;
import org.springframework.integration.channel.DirectChannel;
import org.springframework.integration.core.MessageProducer;
import org.springframework.integration.mqtt.core.DefaultMqttPahoClientFactory;
import org.springframework.integration.mqtt.core.MqttPahoClientFactory;
import org.springframework.integration.mqtt.inbound.MqttPahoMessageDrivenChannelAdapter;
import org.springframework.integration.mqtt.support.DefaultPahoMessageConverter;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHandler;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String urlBroker;

    @Value("${mqtt.topic}")
    private String topico;

    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Autowired
    private EventoPavewatchRepositorio repository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions opciones = new MqttConnectOptions();

        opciones.setServerURIs(new String[] { urlBroker });
        opciones.setUserName(username);
        opciones.setPassword(password.toCharArray());
        opciones.setCleanSession(true);
        opciones.setAutomaticReconnect(true);
        opciones.setConnectionTimeout(30);

        factory.setConnectionOptions(opciones);
        return factory;
    }

    @Bean
    public MessageChannel mqttCanalInput() {
        return new DirectChannel();
    }

    @Bean
    public MessageProducer emisorMensajes() {
        String idCliente = "server_pavewatch_" + System.currentTimeMillis();
        MqttPahoMessageDrivenChannelAdapter adaptador = new MqttPahoMessageDrivenChannelAdapter(idCliente, mqttClientFactory(), topico);
        adaptador.setCompletionTimeout(5000);
        adaptador.setConverter(new DefaultPahoMessageConverter());
        adaptador.setQos(1);
        adaptador.setOutputChannel(mqttCanalInput());
        return adaptador;
    }

    @Bean
    @ServiceActivator(inputChannel = "mqttCanalInput")
    public MessageHandler handler() {
        return message -> {
            String payload = (String) message.getPayload();
            System.out.println("¡Alerta MQTT desde HiveMQ Cloud! Datos: " + payload);

            try {
                ObjectMapper mapeador = new ObjectMapper();
                JsonNode jsonNode = mapeador.readTree(payload);

                // ==========================================================
                // LÓGICA DE IA (PYTHON / YOLO)
                // ==========================================================
                if (jsonNode.has("area_pixeles")) {
                    String severidadIa = jsonNode.get("severidad").asText().toUpperCase().replace("Á", "A");

                    // Coordenadas temporales hasta que Python mande GPS
                    double latitud = -12.016335;
                    double longitud = -77.049658;
                    Point ubicacion = geometryFactory.createPoint(new Coordinate(longitud, latitud));

                    Optional<EventoPavewatch> bacheExistenteOpt = repository.buscarBacheCercano(ubicacion, 20.0);

                    if (bacheExistenteOpt.isPresent()) {
                        EventoPavewatch bache = bacheExistenteOpt.get();
                        bache.setClasificacionIa(severidadIa);
                        bache.setVerificado(true);
                        bache.setOrigenDeteccion("HIBRIDO");
                        repository.save(bache);
                        System.out.println("⭐ ¡Bache existente confirmado por IA! Clasificación: " + severidadIa);
                    } else {
                        EventoPavewatch nuevoBache = new EventoPavewatch();
                        nuevoBache.setUbicacion(ubicacion);
                        nuevoBache.setSeveridad(BigDecimal.ZERO);
                        nuevoBache.setReportadoPor("SISTEMA_IA");
                        nuevoBache.setVerificado(true);
                        nuevoBache.setContadorConfirmaciones(1);
                        nuevoBache.setOrigenDeteccion("CAMARA_IA");
                        nuevoBache.setClasificacionIa(severidadIa);
                        nuevoBache.setDistrito("San Martín de Porres");
                        repository.save(nuevoBache);
                        System.out.println("-> Nuevo bache IA registrado en BD. Clasificación: " + severidadIa);
                    }
                    return;
                }

                // ==========================================================
                // LÓGICA DE LA APP ANDROID (El código de tu amigo + nuestro fix)
                // ==========================================================
                double latitud = jsonNode.get("latitud").asDouble();
                double longitud = jsonNode.get("longitud").asDouble();
                double severidad = jsonNode.get("severidad").asDouble();
                String dispositivo = jsonNode.has("dispositivo") ? jsonNode.get("dispositivo").asText() : "desconocido";
                String fotoUrl = jsonNode.has("fotoUrl") ? jsonNode.get("fotoUrl").asText() : null;
                String distrito = jsonNode.has("distrito") ? jsonNode.get("distrito").asText() : "San Martín de Porres";

                // FILTRO ANTI-RIPIO (Solo aplica para el acelerómetro, no para fotos manuales)
                if (severidad < 8.0 && !"app_FOTO".equals(dispositivo) && !"app_MANUAL".equals(dispositivo)) {
                    System.out.println("⏳ Alerta ignorada: Severidad leve (" + severidad + ") considerada ruido de pavimento.");
                    return;
                }

                Point ubicacion = geometryFactory.createPoint(new Coordinate(longitud, latitud));
                BigDecimal severidadNueva = BigDecimal.valueOf(severidad);

                Optional<EventoPavewatch> bacheExistenteOpt = repository.buscarBacheCercano(ubicacion, 20.0);

                if (bacheExistenteOpt.isPresent()) {
                    EventoPavewatch bacheExistente = bacheExistenteOpt.get();

                    // NUEVA LÓGICA: Separar si trae foto o si es pura vibración
                    if (fotoUrl != null && !fotoUrl.isEmpty()) {
                        // Viene con foto nueva: Forzar a la IA a analizar, NO sumar contador
                        bacheExistente.setUrlFoto(fotoUrl);
                        bacheExistente.setClasificacionIa("SIN_ANALIZAR");
                        bacheExistente.setVerificado(false); // Espera la validación de tu script Python
                        bacheExistente.setOrigenDeteccion("REPORTE_FOTO");
                        bacheExistente.setDistrito(distrito);
                        System.out.println("🔄 Nueva foto en bache existente. Regresando a estado SIN_ANALIZAR para la IA.");
                    } else {
                        // Sin foto (Solo sensor IMU): Sumar contador normal
                        int nuevasConfirmaciones = bacheExistente.getContadorConfirmaciones() + 1;
                        bacheExistente.setContadorConfirmaciones(nuevasConfirmaciones);

                        if (bacheExistente.getSeveridad() != null) {
                            BigDecimal promedio = bacheExistente.getSeveridad()
                                    .add(severidadNueva)
                                    .divide(new BigDecimal(2), 2, RoundingMode.HALF_UP);
                            bacheExistente.setSeveridad(promedio);
                        }

                        if (nuevasConfirmaciones >= 3) {
                            bacheExistente.setVerificado(true);
                            System.out.println("⭐ ¡ALERTA CONFIRMADA! El bache en ID " + bacheExistente.getId() + " fue verificado por múltiples lecturas IMU.");
                        }
                        bacheExistente.setDistrito(distrito);
                        System.out.println("-> Bache existente actualizado solo por IMU. Confirmaciones: " + nuevasConfirmaciones);
                    }

                    repository.save(bacheExistente);

                } else {
                    // LÓGICA ORIGINAL PARA BACHES NUEVOS (Se mantiene intacta)
                    EventoPavewatch nuevoBache = new EventoPavewatch();
                    nuevoBache.setUbicacion(ubicacion);
                    nuevoBache.setSeveridad(severidadNueva);
                    nuevoBache.setReportadoPor(dispositivo);
                    nuevoBache.setContadorConfirmaciones(1);
                    nuevoBache.setClasificacionIa("SIN_ANALIZAR");
                    nuevoBache.setDistrito(distrito);

                    if (fotoUrl != null && !fotoUrl.isEmpty()) {
                        nuevoBache.setUrlFoto(fotoUrl);
                    }

                    if ("app_FOTO".equals(dispositivo)) {
                        nuevoBache.setOrigenDeteccion("REPORTE_FOTO");
                        // Lo ponemos en false para que pase por el filtro de tu IA de Python al ser nuevo
                        nuevoBache.setVerificado(false);
                    } else if ("app_MANUAL".equals(dispositivo)) {
                        nuevoBache.setOrigenDeteccion("REPORTE_MANUAL");
                        nuevoBache.setVerificado(false);
                    } else {
                        nuevoBache.setOrigenDeteccion("SENSOR_IMU");
                        nuevoBache.setVerificado(false);
                    }

                    repository.save(nuevoBache);
                    System.out.println("-> Nuevo bache (" + dispositivo + ") registrado con éxito en Base de Datos.");
                }
            } catch (Exception e) {
                System.err.println("❌ Error procesando mensaje MQTT o calculando distancia espacial: " + e.getMessage());
            }
        };
    }
    /*@Bean
    @ServiceActivator(inputChannel = "mqttCanalInput")
    public MessageHandler handler() {
        return message -> {
            String payload = (String) message.getPayload();
            System.out.println("¡Alerta MQTT desde HiveMQ Cloud! Datos: " + payload);

            try {
                ObjectMapper mapeador = new ObjectMapper();
                JsonNode jsonNode = mapeador.readTree(payload);

                double latitud = jsonNode.get("latitud").asDouble();
                double longitud = jsonNode.get("longitud").asDouble();
                double severidad = jsonNode.get("severidad").asDouble();

                // --- 1. FILTRO ANTI-RIPIO: Si el salto es menor a 8.0, lo ignoramos para no saturar ---
                if (severidad < 8.0) {
                    System.out.println("⏳ Alerta ignorada: Severidad leve (" + severidad + ") considerada ruido de pavimento.");
                    return;
                }

                String dispositivo = jsonNode.has("dispositivo") ? jsonNode.get("dispositivo").asText() : "desconocido";
                String distrito = jsonNode.has("distrito") ? jsonNode.get("distrito").asText() : "San Martín de Porres";

                Point ubicacion = geometryFactory.createPoint(new Coordinate(longitud, latitud));
                BigDecimal severidadNueva = BigDecimal.valueOf(severidad);

                // --- 2. RADAR ESPACIAL AMPLIADO A 20 METROS ---
                Optional<EventoPavewatch> bacheExistenteOpt = repository.buscarBacheCercano(ubicacion, 20.0);

                if (bacheExistenteOpt.isPresent()) {
                    EventoPavewatch bacheExistente = bacheExistenteOpt.get();

                    int nuevasConfirmaciones = bacheExistente.getContadorConfirmaciones() + 1;
                    bacheExistente.setContadorConfirmaciones(nuevasConfirmaciones);

                    if (bacheExistente.getSeveridad() != null) {
                        BigDecimal promedio = bacheExistente.getSeveridad()
                                .add(severidadNueva)
                                .divide(new BigDecimal(2), 2, RoundingMode.HALF_UP);
                        bacheExistente.setSeveridad(promedio);
                    }

                    if (nuevasConfirmaciones >= 3) {
                        bacheExistente.setVerificado(true);
                        System.out.println("⭐ ¡ALERTA CONFIRMADA! El bache en ID " + bacheExistente.getId() + " ya alcanzó 3 detecciones.");
                    }

                    bacheExistente.setDistrito(distrito);
                    repository.save(bacheExistente);
                    System.out.println("-> Bache existente actualizado. Confirmaciones actuales: " + nuevasConfirmaciones);

                } else {
                    EventoPavewatch nuevoBache = new EventoPavewatch();
                    nuevoBache.setUbicacion(ubicacion);
                    nuevoBache.setSeveridad(severidadNueva);
                    nuevoBache.setReportadoPor(dispositivo);
                    nuevoBache.setVerificado(false);
                    nuevoBache.setContadorConfirmaciones(1);
                    nuevoBache.setOrigenDeteccion("SENSOR_IMU");
                    nuevoBache.setClasificacionIa("SIN_ANALIZAR");
                    nuevoBache.setDistrito(distrito);

                    repository.save(nuevoBache);
                    System.out.println("-> Nuevo bache sospechoso registrado con éxito en Base de Datos.");
                }

            } catch (Exception e) {
                System.err.println("❌ Error procesando mensaje MQTT o calculando distancia espacial: " + e.getMessage());
            }
        };
    }*/
}