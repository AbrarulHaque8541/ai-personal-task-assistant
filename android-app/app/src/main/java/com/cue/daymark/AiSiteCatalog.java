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
        final BrowserAddress.SearchEngine searchEngine;

        Entry(String label, String httpsUrl, BrowserAddress.SearchEngine searchEngine) {
            this.label = label;
            this.httpsUrl = httpsUrl;
            this.searchEngine = searchEngine;
        }
    }

    private static final Entry[] ENTRIES = {
            new Entry("ChatGPT", "https://chatgpt.com/", BrowserAddress.SearchEngine.CHATGPT),
            new Entry("Claude", "https://claude.ai/", BrowserAddress.SearchEngine.CLAUDE),
            new Entry("Gemini", "https://gemini.google.com/", BrowserAddress.SearchEngine.GEMINI),
            new Entry("Copilot", "https://copilot.microsoft.com/", BrowserAddress.SearchEngine.COPILOT),
            new Entry("Grok", "https://grok.com/", BrowserAddress.SearchEngine.GROK),
            new Entry("DeepSeek", "https://chat.deepseek.com/", BrowserAddress.SearchEngine.DEEPSEEK),
            new Entry("Mistral Le Chat", "https://chat.mistral.ai/", BrowserAddress.SearchEngine.MISTRAL),
            new Entry("Meta AI", "https://www.meta.ai/", BrowserAddress.SearchEngine.META_AI),
            new Entry("Qwen Chat", "https://chat.qwen.ai/", BrowserAddress.SearchEngine.QWEN_CHAT),
            new Entry("Kimi", "https://www.kimi.com/", BrowserAddress.SearchEngine.KIMI),
            new Entry("Poe", "https://poe.com/", BrowserAddress.SearchEngine.POE),
            new Entry("HuggingChat", "https://huggingface.co/chat/", BrowserAddress.SearchEngine.HUGGINGCHAT),
            new Entry("You.com AI", "https://you.com/", BrowserAddress.SearchEngine.YOU_COM_AI),
            new Entry("Character AI", "https://character.ai/", BrowserAddress.SearchEngine.CHARACTER_AI),
            new Entry("Perplexity", "https://www.perplexity.ai/", BrowserAddress.SearchEngine.PERPLEXITY),
            new Entry("Duck.ai", "https://duck.ai/", BrowserAddress.SearchEngine.DUCK_AI),
            new Entry("Phind", "https://www.phind.com/", BrowserAddress.SearchEngine.PHIND)
    };

    private AiSiteCatalog() { }

    static Entry[] entries() {
        return ENTRIES.clone();
    }

    static int size() {
        return ENTRIES.length;
    }
}
