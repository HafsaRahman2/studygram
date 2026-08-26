package com.studygram.controller;

import com.studygram.service.BreakService;
import com.studygram.security.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

/*
 * BreakController - API endpoints for "Take a break"
 *
 * Endpoints:
 *   GET  /api/breaks/status -> can I take a break, or am I on one?
 *   POST /api/breaks/start  -> begin a 5 minute break
 *   POST /api/breaks/extend -> add the one allowed 5 extra minutes
 *   POST /api/breaks/end    -> finish early
 *
 * None of these take a user id. The break they act on is always the caller's
 * own, decided by the token - otherwise anyone could inspect (or reset) another
 * student's cooldown just by changing a number in the URL.
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

    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@AuthenticationPrincipal AuthenticatedUser me) {
        try {
            return ResponseEntity.ok(breakService.getStatus(me.id()));
        } catch (RuntimeException e) {
            return ResponseEntity.status(404).body(e.getMessage());
        }
    }

    @PostMapping("/start")
    public ResponseEntity<?> startBreak(@AuthenticationPrincipal AuthenticatedUser me) {
        try {
            return ResponseEntity.ok(breakService.startBreak(me.id()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/extend")
    public ResponseEntity<?> extendBreak(@AuthenticationPrincipal AuthenticatedUser me) {
        try {
            return ResponseEntity.ok(breakService.extendBreak(me.id()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

    @PostMapping("/end")
    public ResponseEntity<?> endBreak(@AuthenticationPrincipal AuthenticatedUser me) {
        try {
            return ResponseEntity.ok(breakService.endBreak(me.id()));
        } catch (RuntimeException e) {
            return ResponseEntity.badRequest().body(e.getMessage());
        }
    }

}
