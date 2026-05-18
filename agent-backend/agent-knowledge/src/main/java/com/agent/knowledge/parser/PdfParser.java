package com.agent.knowledge.parser;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;

// PDF 解析器
@Component
public class PdfParser implements DocumentParser {
    @Override public String supportedType() { return "pdf"; }

    @Override
    public Map<String, String> parse(InputStream inputStream, String fileName) throws Exception {
        Map<String, String> result = new HashMap<>();
        try (PDDocument doc = Loader.loadPDF(inputStream.readAllBytes())) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(doc);
            result.put("text", text != null ? text.trim() : "");
        }
        return result;
    }
}
