package com.sweetmoon.gestorclips.service;

import com.sweetmoon.gestorclips.model.Clip;
import com.sweetmoon.gestorclips.model.Comentario;
import com.sweetmoon.gestorclips.repository.ClipRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
public class ClipService {
    private static final Set<String> ESTADOS = Set.of("candidato", "aprobado", "descartado", "publicado");

    private final ClipRepository clipRepository;
    private final MongoTemplate mongoTemplate;

    public ClipService(ClipRepository clipRepository, MongoTemplate mongoTemplate) {
        this.clipRepository = clipRepository;
        this.mongoTemplate = mongoTemplate;
    }

    public List<Clip> obtenerTodos() {
        return clipRepository.findAll();
    }

    public List<Clip> obtenerPorEstado(String estado) {
        return clipRepository.findByEstado(estado);
    }

    public Optional<Clip> obtenerPorId(String id) {
        return clipRepository.findById(id);
    }

    public Clip guardarClip(Clip clip) {
        if (clip.getId() == null && clipRepository.existsByUrlVideo(clip.getUrlVideo())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Ya existe un clip con esa urlVideo");
        }
        return clipRepository.save(clip);
    }

    public Clip cambiarEstado(String id, String estado) {
        if (!ESTADOS.contains(estado)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado inválido. Usa: " + ESTADOS);
        }
        Clip clip = clipRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clip no encontrado"));
        clip.setEstado(estado);
        return clipRepository.save(clip);
    }

    public void eliminar(String id) {
        clipRepository.deleteById(id);
    }

    public Clip agregarComentario(String clipId, Comentario comentario) {
        Clip clip = clipRepository.findById(clipId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Clip no encontrado"));

        comentario.setIdComentario(UUID.randomUUID().toString());
        if (clip.getComentarios() == null) {
            clip.setComentarios(new ArrayList<>());
        }
        clip.getComentarios().add(comentario);
        return clipRepository.save(clip);
    }

    public List<org.bson.Document> obtenerEstadisticasPorStreamer() {
        Aggregation aggregation = Aggregation.newAggregation(
                Aggregation.group("streamer").sum("visualizaciones").as("totalVisualizaciones"),
                Aggregation.sort(Sort.Direction.DESC, "totalVisualizaciones")
        );
        AggregationResults<org.bson.Document> results = mongoTemplate.aggregate(
                aggregation, "clips", org.bson.Document.class);
        return results.getMappedResults();
    }
}
