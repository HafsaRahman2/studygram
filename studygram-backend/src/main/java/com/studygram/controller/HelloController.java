package com.studygram.controller;

import org.springframework.web.bind.annotation.GetMapping;//maps URL to method
import org.springframework.web.bind.annotation.RestController;//marks class as API handler

@RestController
public class HelloController {

    @GetMapping("/api/hello")
    public String sayHello() {
        return "Hello! Your Studygram API is working!";
    }

}
