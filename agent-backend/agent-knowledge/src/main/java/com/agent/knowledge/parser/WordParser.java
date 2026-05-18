package com.agent.knowledge.parser;

import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// Word 解析器: 基于 Apache POI
@Component
public class WordParser implements DocumentParser {
    @Override public String supportedType() { return "docx"; }

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        try (XWPFDocument doc = new XWPFDocument(inputStream)) {
            StringBuilder sb = new StringBuilder();
            doc.getParagraphs().forEach(p ->
                    sb.append(p.getText()).append("\n"));
            result.put("text", sb.toString().trim());
        }
        return result;
    }
}
