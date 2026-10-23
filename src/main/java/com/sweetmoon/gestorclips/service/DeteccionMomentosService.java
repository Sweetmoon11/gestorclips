package com.sweetmoon.gestorclips.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sweetmoon.gestorclips.dto.ParametrosDeteccion;
import com.sweetmoon.gestorclips.model.Clip;
import com.sweetmoon.gestorclips.repository.ClipRepository;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Detecta momentos clave de un VOD de Twitch analizando el chat.
 *
 * Idea: cuando pasa algo bueno en el stream, el chat explota (más mensajes,
 * más "KEKW", "jajaja", "clip", emotes...). Se divide el VOD en ventanas de N
 * segundos, se puntúa cada ventana y las que superan promedio + k·desviación
 * se consideran picos.
 *
 * Entrada: el JSON que genera TwitchDownloaderCLI:
 *   TwitchDownloaderCLI chatdownload --id 2907079590 -o chat.json
 */
@Service
public class DeteccionMomentosService {

    // Palabras/emotes que indican reacción fuerte (peso extra)
    private static final Map<Pattern, Double> SENALES = new LinkedHashMap<>();
    static {
        SENALES.put(Pattern.compile("(?i)\\b(kekw|lul|lmao|omegalul|icant|xd+)\\b"), 1.5);
        SENALES.put(Pattern.compile("(?i)\\b(ja){2,}|\\b(je){2,}|\\b(ha){2,}|\\bjsjs\\w*"), 1.5);
        SENALES.put(Pattern.compile("(?i)\\bclip(ea|een|it)?\\b"), 3.0);
        SENALES.put(Pattern.compile("(?i)\\b(pog|pogchamp|poggers|pogu|ez|gg|wtf|omg|noo+|nooo+|f)\\b"), 1.2);
        SENALES.put(Pattern.compile("(?i)\\b(monkas|sadge|d:|aaa+h*)\\b"), 1.2);
        SENALES.put(Pattern.compile("!{3,}|\\?{3,}"), 0.8);
    }

    // Bots conocidos: sus mensajes no son reacciones reales del público
    private static final Set<String> BOTS = Set.of(
            "streamelements", "nightbot", "moobot", "fossabot", "streamlabs", "wizebot",
            "sery_bot", "soundalerts", "kofistreambot", "botrix", "botrixoficial",
            "tangiabot", "pokemoncommunitygame", "streamloots", "commanderroot");

    // Mensajes automáticos (pedidos de canciones, anuncios, etc.)
    private static final Pattern MENSAJE_AUTOMATICO = Pattern.compile(
            "(?i)(requested by|added to the queue|now playing|ranking de top supporters|https?://|^\\s*\\|)");

    private final ClipRepository clipRepository;
    private final ObjectMapper mapper = new ObjectMapper();

    public DeteccionMomentosService(ClipRepository clipRepository) {
        this.clipRepository = clipRepository;
    }

    private record Mensaje(double segundo, String autor, String texto) {}
    private record Ventana(int indice, int mensajes, int autoresUnicos, double puntaje, List<String> muestra) {}

    public List<Clip> detectar(InputStream chatJson, String streamer, ParametrosDeteccion p) throws IOException {
        JsonNode raiz = mapper.readTree(chatJson);
        String vodId = raiz.path("video").path("id").asText("desconocido");
        String tituloVod = raiz.path("video").path("title").asText("Stream");
        if (streamer == null || streamer.isBlank()) {
            streamer = raiz.path("streamer").path("name").asText("desconocido");
        }

        List<Mensaje> mensajes = new ArrayList<>();
        int descartados = 0;
        for (JsonNode c : raiz.path("comments")) {
            String autor = c.path("commenter").path("display_name").asText("");
            String login = c.path("commenter").path("name").asText(autor).toLowerCase();
            String texto = c.path("message").path("body").asText("").trim();
            boolean esBot = BOTS.contains(login) || login.endsWith("bot");
            boolean esComando = texto.startsWith("!");
            boolean esAutomatico = MENSAJE_AUTOMATICO.matcher(texto).find();
            boolean esDelStreamer = login.equalsIgnoreCase(streamer);
            if (texto.isEmpty() || esBot || esComando || esAutomatico || esDelStreamer) {
                descartados++;
                continue;
            }
            mensajes.add(new Mensaje(c.path("content_offset_seconds").asDouble(), autor, texto));
        }
        if (mensajes.isEmpty()) {
            throw new IllegalArgumentException("El JSON no tiene mensajes en 'comments'. ¿Es la salida de TwitchDownloaderCLI?");
        }

        // Chat pequeño: ventanas más grandes para que haya suficientes mensajes por ventana
        double duracion = mensajes.get(mensajes.size() - 1).segundo();
        double mensajesPorMinuto = mensajes.size() / Math.max(1, duracion / 60.0);
        if (p.isVentanaAutomatica()) {
            if (mensajesPorMinuto < 3) p.setVentanaSegundos(30);
            else if (mensajesPorMinuto < 10) p.setVentanaSegundos(20);
        }

        List<Ventana> ventanas = construirVentanas(mensajes, p.getVentanaSegundos());

        // Estadística del puntaje
        double promedio = ventanas.stream().mapToDouble(Ventana::puntaje).average().orElse(0);
        double varianza = ventanas.stream().mapToDouble(v -> Math.pow(v.puntaje() - promedio, 2)).average().orElse(0);
        double desviacion = Math.sqrt(varianza);
        double umbral = promedio + p.getSensibilidad() * desviacion;

        // Picos ordenados por puntaje, respetando separación mínima
        List<Ventana> candidatas = ventanas.stream()
                .filter(v -> v.puntaje() > umbral && v.mensajes() >= p.getMinMensajes()
                        && v.autoresUnicos() >= p.getMinAutores())
                .sorted(Comparator.comparingDouble(Ventana::puntaje).reversed())
                .toList();

        List<Ventana> elegidas = new ArrayList<>();
        for (Ventana v : candidatas) {
            int seg = v.indice() * p.getVentanaSegundos();
            boolean cerca = elegidas.stream().anyMatch(e ->
                    Math.abs(e.indice() * p.getVentanaSegundos() - seg) < p.getSeparacionMinima());
            if (!cerca) elegidas.add(v);
            if (elegidas.size() >= p.getMaxClips()) break;
        }

        List<Clip> resultado = new ArrayList<>();
        int n = 1;
        for (Ventana v : elegidas) {
            int pico = v.indice() * p.getVentanaSegundos();
            int inicio = Math.max(0, pico - p.getSegundosAntes());
            int fin = pico + p.getVentanaSegundos() + p.getSegundosDespues();

            Clip clip = new Clip();
            clip.setVodId(vodId);
            clip.setStreamer(streamer);
            clip.setTitulo("Momento #" + n++ + " – " + tituloVod + " (" + formatear(inicio) + ")");
            clip.setUrlVideo("https://www.twitch.tv/videos/" + vodId + "?t=" + formatoTwitch(inicio));
            clip.setInicioSegundos(inicio);
            clip.setFinSegundos(fin);
            clip.setPuntaje(Math.round((desviacion == 0 ? 0 : (v.puntaje() - promedio) / desviacion) * 100) / 100.0);
            clip.setMensajesEnPico(v.mensajes());
            clip.setMensajesDestacados(v.muestra());
            clip.setEtiquetas(new ArrayList<>(List.of("auto", "candidato")));
            clip.setEstado("candidato");
            clip.setMetadatos(Map.of("duracionSegundos", fin - inicio, "autoresUnicos", v.autoresUnicos()));

            if (p.isGuardar()) {
                if (clipRepository.existsByVodIdAndInicioSegundos(vodId, inicio)) continue; // evita duplicados
                clip = clipRepository.save(clip);
            }
            resultado.add(clip);
        }
        resultado.sort(Comparator.comparing(Clip::getInicioSegundos));
        return resultado;
    }

        private List<Ventana> construirVentanas(List<Mensaje> mensajes, int tam) {
            int total = (int) (mensajes.stream().mapToDouble(Mensaje::segundo).max().orElse(0) / tam) + 1;
            List<List<Mensaje>> cubetas = new ArrayList<>();
            for (int i = 0; i < total; i++) cubetas.add(new ArrayList<>());
            for (Mensaje m : mensajes) cubetas.get((int) (m.segundo() / tam)).add(m);

        List<Ventana> ventanas = new ArrayList<>();
        for (int i = 0; i < total; i++) {
            List<Mensaje> ms = cubetas.get(i);
            Set<String> autores = new HashSet<>();
            double senal = 0;
            for (Mensaje m : ms) {
                autores.add(m.autor());
                for (var e : SENALES.entrySet()) {
                    if (e.getKey().matcher(m.texto()).find()) senal += e.getValue();
                }
                if (m.texto().length() > 3 && m.texto().equals(m.texto().toUpperCase())
                        && m.texto().chars().anyMatch(Character::isLetter)) senal += 0.5; // MAYÚSCULAS
            }
            // Autores únicos pesan más que mensajes: evita que un solo spammer cree picos
            double puntaje = autores.size() * 1.0 + ms.size() * 0.3 + senal;
            List<String> muestra = ms.stream().map(Mensaje::texto).filter(t -> !t.isBlank()).limit(8).toList();
            ventanas.add(new Ventana(i, ms.size(), autores.size(), puntaje, muestra));
        }
        // Suavizado: un pico real suele durar más de una ventana
        List<Ventana> suav = new ArrayList<>();
        for (int i = 0; i < ventanas.size(); i++) {
            double prev = i > 0 ? ventanas.get(i - 1).puntaje() : 0;
            double next = i < ventanas.size() - 1 ? ventanas.get(i + 1).puntaje() : 0;
            Ventana v = ventanas.get(i);
            suav.add(new Ventana(v.indice(), v.mensajes(), v.autoresUnicos(),
                    v.puntaje() * 0.6 + (prev + next) * 0.2, v.muestra()));
        }
        return suav;
    }

    private static String formatoTwitch(int s) {
        return String.format("%02dh%02dm%02ds", s / 3600, (s % 3600) / 60, s % 60);
    }

    private static String formatear(int s) {
        return String.format("%d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
    }
}
