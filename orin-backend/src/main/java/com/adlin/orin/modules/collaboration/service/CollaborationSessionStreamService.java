package com.adlin.orin.modules.collaboration.service;

import com.adlin.orin.modules.collaboration.dto.CollabSessionDtos;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@Slf4j
@Service
public class CollaborationSessionStreamService {

    private final Map<String, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    public SseEmitter register(String sessionId, String turnId) {
        String key = streamKey(sessionId, turnId);
        SseEmitter emitter = new SseEmitter(0L);
        emitters.computeIfAbsent(key, k -> new CopyOnWriteArrayList<>()).add(emitter);

        emitter.onCompletion(() -> remove(key, emitter));
        emitter.onTimeout(() -> remove(key, emitter));
        emitter.onError(e -> remove(key, emitter));

        return emitter;
    }

    public void publish(CollabSessionDtos.StreamEvent event) {
        String key = streamKey(event.getSessionId(), event.getTurnId());
        List<SseEmitter> list = emitters.getOrDefault(key, List.of());
        if (list.isEmpty()) {
            return;
        }

        for (SseEmitter emitter : list) {
            try {
                emitter.send(SseEmitter.event()
                        .name(event.getEventType())
                        .data(event));
            } catch (IOException e) {
                remove(key, emitter);
            }
        }
    }

    public void complete(String sessionId, String turnId) {
        String key = streamKey(sessionId, turnId);
        List<SseEmitter> list = emitters.remove(key);
        if (list == null) {
            return;
        }
        list.forEach(SseEmitter::complete);
    }

    private void remove(String key, SseEmitter emitter) {
        List<SseEmitter> list = emitters.get(key);
        if (list == null) {
            return;
        }
        list.remove(emitter);
        if (list.isEmpty()) {
            emitters.remove(key);
        }
    }

    private String streamKey(String sessionId, String turnId) {
        return sessionId + ":" + turnId;
    }
}
