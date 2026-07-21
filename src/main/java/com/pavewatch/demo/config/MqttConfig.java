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

    // 1. Configuración de la conexión segura a HiveMQ Cloud
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions opciones = new MqttConnectOptions();

        opciones.setServerURIs(new String[] { urlBroker });
        opciones.setUserName(username);
        opciones.setPassword(password.toCharArray());
        opciones.setCleanSession(true);
        opciones.setAutomaticReconnect(true); // Si el WiFi parpadea, se reconecta solo
        opciones.setConnectionTimeout(30);

        factory.setConnectionOptions(opciones);
        return factory;
    }

    // 2. Canal de entrada
    @Bean
    public MessageChannel mqttCanalInput() {
        return new DirectChannel();
    }

    // 3. Adaptador de Mensajes (Suscripción al tema en la nube)
    @Bean
    public MessageProducer emisorMensajes() {
        String idCliente = "server_pavewatch_" + System.currentTimeMillis();

        MqttPahoMessageDrivenChannelAdapter adaptador = new MqttPahoMessageDrivenChannelAdapter(idCliente, mqttClientFactory(), topico);
        adaptador.setCompletionTimeout(5000);
        adaptador.setConverter(new DefaultPahoMessageConverter());
        adaptador.setQos(1); // QOS 1 garantiza que el bache se entregue al menos una vez
        adaptador.setOutputChannel(mqttCanalInput());
        return adaptador;
    }

    // === 4. Procesamiento del JSON y ALGORITMO DE CONFIRMACIÓN ESPACIAL ===
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
                String dispositivo = jsonNode.has("dispositivo") ? jsonNode.get("dispositivo").asText() : "desconocido";

                // Creamos el punto GPS (Atención: en JTS el orden siempre es Longitud X, Latitud Y)
                Point ubicacion = geometryFactory.createPoint(new Coordinate(longitud, latitud));
                BigDecimal severidadNueva = BigDecimal.valueOf(severidad);

                // ---> EJECUCIÓN DEL RADAR ESPACIAL (8 Metros) <---
                Optional<EventoPavewatch> bacheExistenteOpt = repository.buscarBacheCercano(ubicacion, 4.0);

                if (bacheExistenteOpt.isPresent()) {
                    // YA EXISTÍA UN BACHE EN ESA ZONA: Fusionamos la alerta
                    EventoPavewatch bacheExistente = bacheExistenteOpt.get();

                    int nuevasConfirmaciones = bacheExistente.getContadorConfirmaciones() + 1;
                    bacheExistente.setContadorConfirmaciones(nuevasConfirmaciones);

                    // Calculamos la severidad promedio del impacto para no sobreescribirla
                    if (bacheExistente.getSeveridad() != null) {
                        BigDecimal promedio = bacheExistente.getSeveridad()
                                .add(severidadNueva)
                                .divide(new BigDecimal(2), 2, RoundingMode.HALF_UP);
                        bacheExistente.setSeveridad(promedio);
                    }

                    // REGLA DE AUTO-VERIFICACIÓN: Si al menos 3 carros saltaron ahí, es un bache real
                    if (nuevasConfirmaciones >= 3) {
                        bacheExistente.setVerificado(true);
                        System.out.println("⭐ ¡ALERTA CONFIRMADA! El bache en ID " + bacheExistente.getId() + " ya alcanzó 3 detecciones.");
                    }

                    repository.save(bacheExistente);
                    System.out.println("-> Bache existente actualizado. Confirmaciones actuales: " + nuevasConfirmaciones);

                } else {
                    // ES LA PRIMERA DETECCIÓN EN ESTA CALLE: Creamos el registro
                    EventoPavewatch nuevoBache = new EventoPavewatch();
                    nuevoBache.setUbicacion(ubicacion);
                    nuevoBache.setSeveridad(severidadNueva);
                    nuevoBache.setReportadoPor(dispositivo);
                    nuevoBache.setVerificado(false); // Nace en falso en espera de confirmaciones por consenso
                    nuevoBache.setContadorConfirmaciones(1);
                    nuevoBache.setOrigenDeteccion("SENSOR_IMU");
                    nuevoBache.setClasificacionIa("SIN_ANALIZAR");

                    repository.save(nuevoBache);
                    System.out.println("-> Nuevo bache sospechoso registrado con éxito en Base de Datos.");
                }

            } catch (Exception e) {
                System.err.println("❌ Error procesando mensaje MQTT o calculando distancia espacial: " + e.getMessage());
            }
        };
    }
}