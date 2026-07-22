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
    }
}