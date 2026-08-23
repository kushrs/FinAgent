package com.finagent.notificationservice.controller;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

@RestController
@RequestMapping("/notifications")
@Slf4j
public class NotificationController {

    private final Map<UUID, List<SseEmitter>> emitters = new ConcurrentHashMap<>();

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter streamNotifications(@RequestParam("userId") UUID userId) {
        log.info("Client registering SSE stream for user: {}", userId);
        
        // Timeout set to 1 hour (3600000ms)
        SseEmitter emitter = new SseEmitter(3600000L);

        List<SseEmitter> userEmitters = emitters.computeIfAbsent(userId, k -> new CopyOnWriteArrayList<>());
        userEmitters.add(emitter);

        emitter.onCompletion(() -> {
            log.debug("SSE stream completed for user: {}", userId);
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        });

        emitter.onTimeout(() -> {
            log.debug("SSE stream timed out for user: {}", userId);
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        });

        emitter.onError((ex) -> {
            log.debug("SSE stream error for user: {}", userId, ex);
            userEmitters.remove(emitter);
            if (userEmitters.isEmpty()) {
                emitters.remove(userId);
            }
        });

        // Send initialization event
        try {
            emitter.send(SseEmitter.event()
                    .name("INIT")
                    .data("Connection established. Listening for real-time AI loan decisions."));
        } catch (IOException e) {
            log.error("Failed to send init SSE event to user: {}", userId, e);
            emitter.complete();
        }

        return emitter;
    }

    public void broadcastToUser(UUID userId, Object data) {
        List<SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters != null && !userEmitters.isEmpty()) {
            log.info("Broadcasting SSE event to {} active listeners for user: {}", userEmitters.size(), userId);
            for (SseEmitter emitter : userEmitters) {
                try {
                    emitter.send(SseEmitter.event()
                            .name("LOAN_DECISION")
                            .data(data, MediaType.APPLICATION_JSON));
                } catch (IOException e) {
                    log.error("Failed to send SSE event, closing emitter for user: {}", userId, e);
                    emitter.complete();
                    userEmitters.remove(emitter);
                }
            }
        } else {
            log.debug("No active SSE listeners for user: {}", userId);
        }
    }
}
