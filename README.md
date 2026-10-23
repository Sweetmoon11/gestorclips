# 🌙 Gestor de Clips · Sweetmoon

Aplicación en **Java + Spring Boot + MongoDB** que detecta los **momentos clave** de un VOD de Twitch analizando la actividad del chat, y permite revisarlos, aprobarlos y guardarlos desde una interfaz web.

## ¿Cómo detecta los momentos?
1. Descarga el chat del VOD con [TwitchDownloaderCLI](https://github.com/lay295/TwitchDownloader).
2. Filtra bots (StreamElements, Nightbot…), comandos (`!song`) y mensajes automáticos.
3. Divide el stream en ventanas de tiempo y puntúa cada una según personas distintas escribiendo, cantidad de mensajes y reacciones (`KEKW`, `jajaja`, `clip`, MAYÚSCULAS…).
4. Las ventanas que superan *promedio + k·desviación estándar* son momentos clave. El clip empieza unos segundos antes del pico (el chat reacciona tarde).

## Tecnologías
- Java 21 · Spring Boot 3.2 · Spring Data MongoDB · Lombok · Bean Validation
- Frontend: HTML, CSS y JavaScript puros (`src/main/resources/static`)
- Reproductor embebido de Twitch

## Requisitos
- JDK 21
- MongoDB local (`localhost:27017`) o un cluster de MongoDB Atlas
- [TwitchDownloaderCLI](https://github.com/lay295/TwitchDownloader/releases) (para analizar pegando el link)

## Configuración
Las credenciales **no** están en el repositorio. Se configuran con variables de entorno:

| Variable | Descripción | Por defecto |
|---|---|---|
| `MONGODB_URI` | Cadena de conexión de MongoDB | `mongodb://localhost:27017/test` |
| `TWITCH_DOWNLOADER_PATH` | Ruta a `TwitchDownloaderCLI.exe` | `C:/TwitchDownloaderCLI-1.56.5-Windows-x64/TwitchDownloaderCLI.exe` |

En IntelliJ: *Run → Edit Configurations → Environment variables*.

## Ejecutar
```bash
./mvnw spring-boot:run
```
Abrir **http://localhost:8080**

## API
| Método | Ruta | Descripción |
|---|---|---|
| POST | `/api/clips/detectar-vod?url=<link del VOD>` | Descarga el chat y detecta momentos |
| POST | `/api/clips/detectar` (form-data `chat`) | Detecta momentos desde un `chat.json` |
| GET | `/api/clips?estado=candidato` | Lista clips (filtro opcional por estado) |
| POST | `/api/clips` | Crea un clip |
| PATCH | `/api/clips/{id}/estado` | `{"estado":"aprobado"}` / `descartado` |
| DELETE | `/api/clips/{id}` | Elimina un clip |
| POST | `/api/clips/{id}/comentarios` | Agrega un comentario |
| GET | `/api/clips/estadisticas/streamers` | Visualizaciones por streamer |

Parámetros de detección opcionales: `sensibilidad`, `maxClips`, `ventanaSegundos`, `minMensajes`, `minAutores`, `guardar`.
