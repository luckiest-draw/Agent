package com.agent.knowledge.parser;

import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;

@Component
public class HtmlParser implements DocumentParser {
    @Override public String supportedType() { return "html"; }

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        Document doc = Jsoup.parse(inputStream, StandardCharsets.UTF_8.name(), "");
        result.put("text", doc.body() != null ? doc.body().text() : doc.text());
        return result;
    }
}
