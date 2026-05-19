package com.agent.conversation.memory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import java.util.regex.Pattern;

@Component
public class PiiMasker {
    private static final Logger log = LoggerFactory.getLogger(PiiMasker.class);

    private static final Pattern PHONE = Pattern.compile("(?<![0-9])1[3-9]\\d{9}(?![0-9])");
    private static final Pattern ID_CARD = Pattern.compile("(?<![0-9])\\d{17}[\\dXx](?![0-9])");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.]+");
    private static final Pattern BANK_CARD = Pattern.compile("(?<![0-9])\\d{16,19}(?![0-9])");

    public static final String PHONE_MASK = "[PHONE_REDACTED]";
    public static final String ID_MASK = "[ID_REDACTED]";
    public static final String EMAIL_MASK = "[EMAIL_REDACTED]";
    public static final String BANK_MASK = "[BANK_CARD_REDACTED]";

    public String mask(String text) {
        if (text == null || text.isEmpty()) return text;
        String result = text;
        boolean masked = false;

        if (PHONE.matcher(result).find()) {
            result = PHONE.matcher(result).replaceAll(PHONE_MASK);
            masked = true;
        }
        if (ID_CARD.matcher(result).find()) {
            result = ID_CARD.matcher(result).replaceAll(ID_MASK);
            masked = true;
        }
        if (EMAIL.matcher(result).find()) {
            result = EMAIL.matcher(result).replaceAll(EMAIL_MASK);
            masked = true;
        }
        if (BANK_CARD.matcher(result).find()) {
            result = BANK_CARD.matcher(result).replaceAll(BANK_MASK);
            masked = true;
        }
        if (masked) {
            log.debug("PII masked in message");
        }
        return result;
    }
}
