package com.sweetmoon.gestorclips.repository;

import com.sweetmoon.gestorclips.model.Clip;
import org.springframework.data.mongodb.repository.MongoRepository;
import java.util.List;

public interface ClipRepository extends MongoRepository<Clip, String> {
    List<Clip> findByStreamerIgnoreCase(String streamer);
    List<Clip> findByEtiquetasContaining(String etiqueta);
    List<Clip> findByEstado(String estado);
    List<Clip> findByVodIdOrderByPuntajeDesc(String vodId);
    boolean existsByUrlVideo(String urlVideo);
    boolean existsByVodIdAndInicioSegundos(String vodId, Integer inicioSegundos);
}
