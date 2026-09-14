package com.aizerohub.ragguard.core.generate;

import java.util.ArrayList;
import java.util.List;

/**
 * Splits documents into roughly {@code maxChars}-sized chunks on paragraph
 * boundaries — the granularity QA pairs are generated from.
 */
final class Chunker {

    private Chunker() {
    }

    static List<String> chunk(String document, int maxChars) {
        List<String> chunks = new ArrayList<>();
        if (document == null || document.isBlank()) {
            return chunks;
        }
        StringBuilder current = new StringBuilder();
        for (String paragraph : document.strip().split("\n\n+")) {
            String p = paragraph.strip();
            if (p.isEmpty()) {
                continue;
            }
            if (p.length() > maxChars) {
                // flush current, then hard-split the oversized paragraph
                if (current.length() > 0) {
                    chunks.add(current.toString().strip());
                    current.setLength(0);
                }
                for (int i = 0; i < p.length(); i += maxChars) {
                    chunks.add(p.substring(i, Math.min(i + maxChars, p.length())));
                }
                continue;
            }
            if (current.length() + p.length() + 2 > maxChars && current.length() > 0) {
                chunks.add(current.toString().strip());
                current.setLength(0);
            }
            if (current.length() > 0) {
                current.append("\n\n");
            }
            current.append(p);
        }
        if (current.length() > 0) {
            chunks.add(current.toString().strip());
        }
        return List.copyOf(chunks);
    }
}
