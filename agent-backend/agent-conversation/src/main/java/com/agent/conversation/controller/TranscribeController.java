package com.agent.conversation.controller;

import com.agent.common.Result;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.Map;

@RestController
@RequestMapping("/api/conversations")
public class TranscribeController {

    private static final Logger log = LoggerFactory.getLogger(TranscribeController.class);

    @Value("${ai.engine.url:http://localhost:8000}")
    private String engineUrl;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/transcribe")
    public Result<Map<String, String>> transcribe(@RequestParam("file") MultipartFile file) throws Exception {
        if (file.isEmpty()) {
            return Result.fail(500, "音频文件为空");
        }

        String url = engineUrl + "/speech/transcribe";
        log.info("Forwarding audio to Python engine: {} ({} bytes)", file.getOriginalFilename(), file.getSize());

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new org.springframework.core.io.ByteArrayResource(file.getBytes()) {
            @Override
            public String getFilename() {
                return file.getOriginalFilename() != null ? file.getOriginalFilename() : "recording.webm";
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        try {
            ResponseEntity<Map> response = restTemplate.exchange(url, HttpMethod.POST, requestEntity, Map.class);
            if (response.getStatusCode().is2xxSuccessful() && response.getBody() != null) {
                String text = (String) response.getBody().get("text");
                log.info("Transcribe result: {}", text);
                return Result.ok(Map.of("text", text));
            }
            log.error("Transcribe failed: HTTP {}", response.getStatusCode());
            return Result.fail(500, "语音转写失败");
        } catch (Exception e) {
            log.error("Transcribe error: {}", e.getMessage(), e);
            return Result.fail(500, "语音转写服务异常: " + e.getMessage());
        }
    }
}
