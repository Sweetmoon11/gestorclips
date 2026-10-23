package com.sweetmoon.gestorclips.controller;

import com.sweetmoon.gestorclips.dto.ParametrosDeteccion;
import com.sweetmoon.gestorclips.model.Clip;
import com.sweetmoon.gestorclips.model.Comentario;
import com.sweetmoon.gestorclips.service.ClipService;
import com.sweetmoon.gestorclips.service.DeteccionMomentosService;
import com.sweetmoon.gestorclips.service.DescargaChatService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/clips")
public class ClipController {

    private final ClipService clipService;
    private final DeteccionMomentosService deteccionService;
    private final DescargaChatService descargaChatService;

    public ClipController(ClipService clipService, DeteccionMomentosService deteccionService,
                          DescargaChatService descargaChatService) {
        this.clipService = clipService;
        this.deteccionService = deteccionService;
        this.descargaChatService = descargaChatService;
    }

    @GetMapping
    public List<Clip> listarClips(@RequestParam(required = false) String estado) {
        return estado == null ? clipService.obtenerTodos() : clipService.obtenerPorEstado(estado);
    }

    @PostMapping
    public Clip crearClip(@Valid @RequestBody Clip clip) {
        return clipService.guardarClip(clip);
    }

    /**
     * Sube el chat.json de TwitchDownloaderCLI y devuelve los momentos clave.
     * Postman: Body -> form-data -> key "chat" (tipo File).
     * Parámetros opcionales en la URL: ?streamer=Sweetmoon&sensibilidad=2&maxClips=10&ventanaSegundos=10&guardar=true
     */
    @PostMapping(value = "/detectar", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public List<Clip> detectar(@RequestParam("chat") MultipartFile chat,
                               @RequestParam(required = false) String streamer,
                               @ModelAttribute ParametrosDeteccion parametros) {
        try {
            return deteccionService.detectar(chat.getInputStream(), streamer, parametros);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IOException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No se pudo leer el JSON del chat: " + e.getMessage());
        }
    }

    /**
     * Pega el link del VOD y la app descarga el chat sola (con TwitchDownloaderCLI).
     * Postman: POST /api/clips/detectar-vod?url=https://www.twitch.tv/videos/2907079590&streamer=Sweetmoon
     */
    @PostMapping("/detectar-vod")
    public List<Clip> detectarDesdeVod(@RequestParam String url,
                                       @RequestParam(required = false) String streamer,
                                       @ModelAttribute ParametrosDeteccion parametros) {
        Path chat = null;
        try {
            String vodId = descargaChatService.extraerId(url);
            chat = descargaChatService.descargar(vodId);
            try (InputStream in = Files.newInputStream(chat)) {
                return deteccionService.detectar(in, streamer, parametros);
            }
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        } catch (IllegalStateException | IOException e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Descarga interrumpida");
        } finally {
            if (chat != null) try { Files.deleteIfExists(chat); } catch (IOException ignored) {}
        }
    }

    /** Aprobar o descartar un candidato: body {"estado": "aprobado"} */
    @PatchMapping("/{id}/estado")
    public Clip cambiarEstado(@PathVariable String id, @RequestBody Map<String, String> body) {
        return clipService.cambiarEstado(id, body.get("estado"));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable String id) {
        clipService.eliminar(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{clipId}/comentarios")
    public ResponseEntity<Clip> comentarClip(@PathVariable String clipId, @Valid @RequestBody Comentario comentario) {
        return ResponseEntity.ok(clipService.agregarComentario(clipId, comentario));
    }

    @GetMapping("/estadisticas/streamers")
    public List<org.bson.Document> verEstadisticas() {
        return clipService.obtenerEstadisticasPorStreamer();
    }
}
