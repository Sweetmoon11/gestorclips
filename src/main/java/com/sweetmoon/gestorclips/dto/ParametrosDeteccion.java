package com.sweetmoon.gestorclips.dto;

import lombok.Data;

/** Ajustes del detector. Todos tienen un valor por defecto razonable. */
@Data
public class ParametrosDeteccion {
    private int ventanaSegundos = 10;      // Tamaño de cada "cubeta" de chat
    private double sensibilidad = 2.0;     // Desviaciones estándar sobre el promedio para contar como pico
    private int maxClips = 10;             // Máximo de momentos a devolver
    private int segundosAntes = 25;        // El chat reacciona tarde: el clip empieza antes del pico
    private int segundosDespues = 10;
    private int separacionMinima = 60;     // Segundos mínimos entre dos clips
    private boolean guardar = true;
    private boolean ventanaAutomatica = true; // Agranda la ventana si el chat es pequeño
    private int minMensajes = 4;              // Mínimo de mensajes reales en la ventana
    private int minAutores = 2;               // Mínimo de personas distintas reaccionando        // Guardar en Mongo como "candidato"
}
