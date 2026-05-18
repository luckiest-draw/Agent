package com.agent.knowledge.parser;

import org.springframework.stereotype.Component;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class MarkdownParser implements DocumentParser {
    @Override public String supportedType() { return "md"; }

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(inputStream, StandardCharsets.UTF_8))) {
            result.put("text", reader.lines().collect(Collectors.joining("\n")));
        }
        return result;
    }
}
