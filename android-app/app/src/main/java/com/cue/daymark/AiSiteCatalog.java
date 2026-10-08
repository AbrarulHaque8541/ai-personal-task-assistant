package com.cue.daymark;

/**
 * HTTPS-only AI website shortcuts for the embedded browser home panel.
 * Ordinary websites only — no API keys, scraping, or JS bridges.
 * Order: ChatGPT first, Claude, Gemini, … Perplexity last.
 */
final class AiSiteCatalog {
    static final class Entry {
        final String label;
        final String httpsUrl;

        Entry(String label, String httpsUrl) {
            this.label = label;
            this.httpsUrl = httpsUrl;
        }
    }

    private static final Entry[] ENTRIES = {
            new Entry("ChatGPT", "https://chatgpt.com/"),
            new Entry("Claude", "https://claude.ai/"),
            new Entry("Gemini", "https://gemini.google.com/"),
            new Entry("Copilot", "https://copilot.microsoft.com/"),
            new Entry("Grok", "https://grok.com/"),
            new Entry("DeepSeek", "https://chat.deepseek.com/"),
            new Entry("Mistral", "https://chat.mistral.ai/"),
            new Entry("Meta AI", "https://www.meta.ai/"),
            new Entry("Qwen", "https://chat.qwen.ai/"),
            new Entry("Kimi", "https://www.kimi.com/"),
            new Entry("Poe", "https://poe.com/"),
            new Entry("HuggingChat", "https://huggingface.co/chat/"),
            new Entry("You.com", "https://you.com/"),
            new Entry("Character AI", "https://character.ai/"),
            new Entry("Perplexity", "https://www.perplexity.ai/")
    };

    private AiSiteCatalog() { }

    static Entry[] entries() {
        return ENTRIES.clone();
    }

    static int size() {
        return ENTRIES.length;
    }
}
