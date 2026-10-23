package com.sweetmoon.gestorclips.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Descarga el chat de un VOD de Twitch llamando a TwitchDownloaderCLI.exe.
 * La ruta del .exe se configura en application.properties (twitch.downloader.path).
 */
@Service
public class DescargaChatService {

    private static final Pattern ID_VOD = Pattern.compile("(?:videos/|^)(\\d{6,})");

    @Value("${twitch.downloader.path}")
    private String rutaDownloader;

    /** Acepta "https://www.twitch.tv/videos/2907079590?t=..." o solo "2907079590". */
    public String extraerId(String link) {
        if (link == null) throw new IllegalArgumentException("Falta el link del VOD");
        Matcher m = ID_VOD.matcher(link.trim());
        if (!m.find()) {
            throw new IllegalArgumentException("No reconozco ese link. Debe ser como https://www.twitch.tv/videos/2907079590");
        }
        return m.group(1);
    }

    /** Descarga el chat a un archivo temporal y devuelve su ruta. Quien lo use debe borrarlo. */
    public Path descargar(String vodId) throws IOException, InterruptedException {
        File exe = new File(rutaDownloader);
        if (!exe.isFile()) {
            throw new IllegalStateException("No encuentro TwitchDownloaderCLI en: " + rutaDownloader
                    + ". Revisa 'twitch.downloader.path' en application.properties");
        }
        Path salida = Files.createTempFile("chat-" + vodId + "-", ".json");
        Path log = Files.createTempFile("chat-" + vodId + "-", ".log");
        Files.deleteIfExists(salida); // el CLI crea el archivo

        Process proceso = new ProcessBuilder(exe.getAbsolutePath(),
                "chatdownload", "--id", vodId, "-o", salida.toString(), "--collision", "Overwrite")
                .redirectErrorStream(true)
                .redirectOutput(log.toFile())
                .start();

        boolean termino = proceso.waitFor(15, TimeUnit.MINUTES);
        String detalle = Files.readString(log);
        Files.deleteIfExists(log);

        if (!termino) {
            proceso.destroyForcibly();
            throw new IllegalStateException("La descarga del chat tardó demasiado (más de 15 minutos)");
        }
        if (proceso.exitValue() != 0 || !Files.exists(salida)) {
            throw new IllegalStateException("TwitchDownloaderCLI falló. ¿El VOD existe y es público?\n" + detalle);
        }
        return salida;
    }
}
