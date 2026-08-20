package com.studygram.controller;

import com.studygram.service.BreakService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/*
 * BreakController - API endpoints for "Take a break"
 *
 * Endpoints:
 *   GET  /api/breaks/status/{userId} -> can I take a break, or am I on one?
 *   POST /api/breaks/start           -> begin a 5 minute break
 *   POST /api/breaks/extend          -> add the one allowed 5 extra minutes
 *   POST /api/breaks/end             -> finish early
 *
 * Every one of these returns the same BreakStatusResponse, so the frontend has
 * a single shape to handle and can simply replace its state with whatever came
 * back. No endpoint returns a bare "OK" that would force the client to guess
 * what changed and refetch.
 */
@RestController
@RequestMapping("/api/breaks")
public class BreakController {

    @Autowired
    private BreakService breakService;

    @GetMapping("/status/{userId}")
    public ResponseEntity<?> getStatus(@PathVariable Long userId) {
        try {
            return ResponseEntity.ok(breakService.getStatus(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PostMapping("/start")
    public ResponseEntity<?> startBreak(@RequestParam Long userId) {
        try {
            return ResponseEntity.ok(breakService.startBreak(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/extend")
    public ResponseEntity<?> extendBreak(@RequestParam Long userId) {
        try {
            return ResponseEntity.ok(breakService.extendBreak(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/end")
    public ResponseEntity<?> endBreak(@RequestParam Long userId) {
        try {
            return ResponseEntity.ok(breakService.endBreak(userId));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
