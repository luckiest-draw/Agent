package com.agent.conversation.memory;

import com.agent.conversation.service.impl.MessageInfo;
import org.springframework.stereotype.Component;
import java.util.List;

@Component
public class TokenCounter {

    private static final double CJK_DIVISOR = 1.5;
    private static final double ENG_DIVISOR = 4.0;
    private static final int OVERHEAD_PER_MESSAGE = 4;

    public int estimateTokens(String text) {
        if (text == null || text.isEmpty()) return 0;
        int cjk = 0;
        int total = 0;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            total++;
            if (Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_A
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_UNIFIED_IDEOGRAPHS_EXTENSION_B
                || Character.UnicodeBlock.of(c) == Character.UnicodeBlock.CJK_COMPATIBILITY_IDEOGRAPHS) {
                cjk++;
            }
        }
        double ratio = (double) cjk / total;
        double divisor = ratio > 0.5 ? CJK_DIVISOR : ENG_DIVISOR;
        return (int) Math.ceil(total / divisor);
    }

    public int estimateTokens(List<MessageInfo> messages) {
        int total = 0;
        for (MessageInfo msg : messages) {
            total += estimateTokens(msg.content()) + OVERHEAD_PER_MESSAGE;
        }
        return total;
    }
}
