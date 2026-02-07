package org.rap.poccodingplatform.controller;

import org.rap.poccodingplatform.dto.ExecutionResult;
import org.rap.poccodingplatform.services.DockerSandboxService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/sandbox")
@CrossOrigin(origins = "http://localhost:5173")
public class SubmissionController {

    @Autowired
    private DockerSandboxService sandboxService;

    @PostMapping("/execute")
    public ExecutionResult executeCode(@RequestBody Map<String, String> payload) {
        // Payload dạng: { "code": "public class Main { ... }" }
        String code = payload.get("code");
        return sandboxService.runCode(code);
    }
}