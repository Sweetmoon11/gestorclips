package com.sweetmoon.gestorclips.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Document(collection = "clips")
@Data
public class Clip {
    @Id
    private String id;
    @NotBlank
    private String titulo;
    @NotBlank
    private String urlVideo; // Enlace al VOD (con ?t= si es un momento)
    private String streamer;
    private List<String> etiquetas = new ArrayList<>();

    private Map<String, Object> metadatos;

    // --- Datos del momento dentro del VOD ---
    private String vodId;
    private Integer inicioSegundos;
    private Integer finSegundos;
    private Double puntaje;          // Qué tan fuerte fue el pico de chat
    private Integer mensajesEnPico;  // Mensajes en la ventana pico
    private List<String> mensajesDestacados = new ArrayList<>(); // Muestra del chat en ese momento
    private String estado = "publicado"; // "candidato" (detectado), "aprobado", "descartado", "publicado"

    private Long visualizaciones = 0L;
    private LocalDateTime fechaPublicacion = LocalDateTime.now();

    private List<Comentario> comentarios = new ArrayList<>();
}
