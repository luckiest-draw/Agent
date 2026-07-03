package com.agent.knowledge.chunker;

import org.springframework.stereotype.Component;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 文本分片器: 语义边界分句 + 聚合到 chunk_size + 重叠
 *
 * 先按中文（。！？）、英文（.!?）换行符等自然句边界分句，
 * 再聚合句子到不超过 chunkSize 的块。比固定字符数硬切语义更完整。
 */
@Component
public class TextChunker {

    private static final int DEFAULT_CHUNK_SIZE = 500;
    private static final int DEFAULT_OVERLAP = 50;

    // 中英文句末边界正则：句号/感叹号/问号/分号/换行符 后跟非文字字符
    private static final Pattern SENTENCE_BOUNDARY = Pattern.compile(
        "(?<=[。！？；.?!;\\n])(?=[^a-zA-Z0-9\\u4e00-\\u9fa5])"
    );

    /**
     * 语义分句 → 聚合块
     */
    public List<String> chunk(String text) {
        return chunk(text, DEFAULT_CHUNK_SIZE, DEFAULT_OVERLAP);
    }

    public List<String> chunk(String text, int chunkSize, int overlap) {
        List<String> result = new ArrayList<>();
        if (text == null || text.isBlank()) return result;

        // Step 1: 按自然句边界分句
        List<String> sentences = splitSentences(text);

        // Step 2: 聚合句子到不超过 chunkSize 的块
        StringBuilder buffer = new StringBuilder();
        int currentLen = 0;

        for (String sentence : sentences) {
            int sentLen = sentence.length();

            // 单句超过 chunkSize：硬切成多个块
            if (sentLen > chunkSize && buffer.isEmpty()) {
                addFixedChunks(result, sentence, chunkSize, overlap);
                continue;
            }

            // 加入当前句会超过阈值 → 先保存当前块，再起新块
            if (currentLen + sentLen > chunkSize && currentLen > 0) {
                result.add(buffer.toString());

                // 重叠：新块保留上一块末尾的一部分
                buffer = new StringBuilder();
                if (overlap > 0 && !result.isEmpty()) {
                    String last = result.get(result.size() - 1);
                    if (last.length() > overlap) {
                        String overlapText = last.substring(last.length() - overlap);
                        buffer.append(overlapText);
                        currentLen = overlapText.length();
                    }
                }
            }

            buffer.append(sentence);
            currentLen = buffer.length();
        }

        // 收尾
        if (currentLen > 0) {
            result.add(buffer.toString());
        }

        return result;
    }

    /**
     * 按句末标点分句
     */
    private List<String> splitSentences(String text) {
        List<String> sentences = new ArrayList<>();
        int start = 0;
        int len = text.length();

        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (isSentenceEnd(c)) {
                sentences.add(text.substring(start, i + 1));
                start = i + 1;
            }
        }

        // 最后一段（无结束标点的）
        if (start < len) {
            String tail = text.substring(start).trim();
            if (!tail.isEmpty()) {
                sentences.add(tail);
            }
        }

        return sentences;
    }

    private boolean isSentenceEnd(char c) {
        return c == '。' || c == '！' || c == '？' || c == '；'
            || c == '.' || c == '!' || c == '?' || c == '\n'
            || c == ';' || c == '…';
    }

    /**
     * 固定长度硬切（超大单句回退）
     */
    private void addFixedChunks(List<String> result, String text, int size, int overlap) {
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + size, text.length());
            result.add(text.substring(start, end));
            start = end - overlap;
        }
    }
}
