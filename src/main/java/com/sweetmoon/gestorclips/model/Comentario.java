package com.sweetmoon.gestorclips.model;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

@Data
public class Comentario {
    private String idComentario;
    @NotBlank(message = "El autor es obligatorio")
    private String autor;
    @NotBlank(message = "El contenido es obligatorio")
    private String contenido;
    private LocalDateTime fechaCreacion = LocalDateTime.now();
    private List<Comentario> respuestas = new ArrayList<>();
}
