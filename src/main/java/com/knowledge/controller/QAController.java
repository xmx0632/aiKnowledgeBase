package com.knowledge.controller;

import com.knowledge.service.QAService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/qa")
@RequiredArgsConstructor
public class QAController {

    private final QAService qaService;

    @PostMapping("/ask")
    public ResponseEntity<Map<String, String>> ask(@RequestBody Map<String, String> request) throws IOException {
        String question = request.get("question");
        String answer = qaService.getAnswer(question);
        
        Map<String, String> response = new HashMap<>();
        response.put("question", question);
        response.put("answer", answer);
        
        return ResponseEntity.ok(response);
    }
}
