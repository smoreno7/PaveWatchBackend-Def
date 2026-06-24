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

@Configuration
public class MqttConfig {

    @Value("${mqtt.broker.url}")
    private String urlBroker;

    @Value("${mqtt.topic}")
    private String topico;

    // Inyectamos las nuevas credenciales de la nube
    @Value("${mqtt.username}")
    private String username;

    @Value("${mqtt.password}")
    private String password;

    @Autowired
    private EventoPavewatchRepositorio repository;

    private final GeometryFactory geometryFactory = new GeometryFactory(new PrecisionModel(), 4326);

    // Configuramos la conexión segura a HiveMQ Cloud
    @Bean
    public MqttPahoClientFactory mqttClientFactory() {
        DefaultMqttPahoClientFactory factory = new DefaultMqttPahoClientFactory();
        MqttConnectOptions opciones = new MqttConnectOptions();

        opciones.setServerURIs(new String[] { urlBroker });
        opciones.setUserName(username);
        opciones.setPassword(password.toCharArray());

        // Configuraciones recomendadas para conexiones en la nube
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

    // Adaptador de Mensajes (Suscripción al tema en la nube)
    @Bean
    public MessageProducer receptorMensajes() {
        // Usamos un ClientID único para evitar conflictos con otros dispositivos
        String idCliente = "server_pavewatch_" + System.currentTimeMillis();

        MqttPahoMessageDrivenChannelAdapter adaptador = new MqttPahoMessageDrivenChannelAdapter(idCliente, mqttClientFactory(), topico);
        adaptador.setCompletionTimeout(5000);
        adaptador.setConverter(new DefaultPahoMessageConverter());
        adaptador.setQos(1); // QOS 1 garantiza que el bache se entregue al menos una vez
        adaptador.setOutputChannel(mqttCanalInput());
        return adaptador;
    }

    // === 4. Procesamiento del JSON y PostGIS ===
    @Bean
    @ServiceActivator(inputChannel = "mqttCanalInput")
    public MessageHandler handler() {
        return message -> {
            String payload = (String) message.getPayload();
            System.out.println("¡Bache recibido desde HiveMQ Cloud! Datos: " + payload);

            try {
                ObjectMapper mapeador = new ObjectMapper();
                JsonNode jsonNode = mapeador.readTree(payload);

                double latitud = jsonNode.get("latitud").asDouble();
                double longitud = jsonNode.get("longitud").asDouble();
                double severidad = jsonNode.get("severidad").asDouble();
                String dispositivo = jsonNode.has("dispositivo") ? jsonNode.get("dispositivo").asText() : "desconocido";

                Point ubicacion = geometryFactory.createPoint(new Coordinate(longitud, latitud));

                EventoPavewatch nuevoBache = new EventoPavewatch();
                nuevoBache.setUbicacion(ubicacion);
                nuevoBache.setSeveridad(BigDecimal.valueOf(severidad));
                nuevoBache.setReportadoPor(dispositivo);
                nuevoBache.setVerificado(false);

                repository.save(nuevoBache);
                System.out.println("-> Guardado exitosamente en Base de Datos vía encriptada.");

            } catch (Exception e) {
                System.err.println("Error procesando mensaje de la nube: " + e.getMessage());
            }
        };
    }
}