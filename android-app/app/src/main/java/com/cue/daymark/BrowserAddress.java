package com.cue.daymark;

import java.net.URI;
import java.net.URLEncoder;
import java.io.UnsupportedEncodingException;
import java.util.Locale;
import java.util.regex.Pattern;

/** Input parsing for explicit browser navigation; this class never performs network requests. */
final class BrowserAddress {
    private static final int MAX_INPUT_LENGTH = 2048;
    private static final Pattern SCHEME = Pattern.compile("^[A-Za-z][A-Za-z0-9+.-]*:");
    private static final Pattern BARE_DOMAIN = Pattern.compile(
            "(?i)^(?:[A-Za-z0-9-]+\\.)+[A-Za-z]{2,}(?::[0-9]{1,5})?(?:[/\\?#].*)?$");

    private BrowserAddress() { }

    enum SearchEngine {
        DUCKDUCKGO("DuckDuckGo", "https://duckduckgo.com/?q="),
        GOOGLE("Google", "https://www.google.com/search?q="),
        BING("Bing", "https://www.bing.com/search?q="),
        BRAVE("Brave Search", "https://search.brave.com/search?q="),
        STARTPAGE("Startpage", "https://www.startpage.com/sp/search?query="),
        YAHOO("Yahoo", "https://search.yahoo.com/search?p="),
        ECOSIA("Ecosia", "https://www.ecosia.org/search?q="),
        QWANT("Qwant", "https://www.qwant.com/?q="),
        MOJEEK("Mojeek", "https://www.mojeek.com/search?q="),
        KAGI("Kagi", "https://kagi.com/search?q="),
        YOU("You.com", "https://you.com/search?q="),
        YANDEX("Yandex", "https://yandex.com/search/?text="),
        BAIDU("Baidu", "https://www.baidu.com/s?wd="),
        DUCKDUCKGO_LITE("DuckDuckGo Lite", "https://lite.duckduckgo.com/lite/?q="),
        QWANT_AI("Qwant AI", "https://www.qwant.com/?q="),
        CHATGPT("ChatGPT", "https://chatgpt.com/?q="),
        PERPLEXITY("Perplexity", "https://www.perplexity.ai/search?q="),
        GEMINI("Google Gemini", "https://gemini.google.com/app?query="),
        CLAUDE("Claude", "https://claude.ai/new?q="),
        COPILOT("Microsoft Copilot", "https://copilot.microsoft.com/?q="),
        GROK("Grok", "https://grok.com/?q="),
        DEEPSEEK("DeepSeek", "https
://chat.deepseek.com/?q="),
        PHIND("Phind", "https://www.phind.com/search?q="),
        KIMI("Kimi", "https://www.kimi.com/?q="),
        YOU_COM_AI("You.com AI", "https://you.com/search?q="),
        WOLFRAM_ALPHA("WolframAlpha", "https://www.wolframalpha.com/input/?i="),
        WIKIPEDIA("Wikipedia", "https://en.wikipedia.org/wiki/Special:Search?search="),
        ANDI("Andi Search", "https://andisearch.com/?query="),
        EXA("Exa Search", "https://exa.ai/search?q="),
        META_AI("Meta AI", "https://www.meta.ai/?q="),
        MISTRAL("Mistral Le Chat", "https://chat.mistral.ai/chat/?q="),
        POE("Poe", "https://poe.com/?q="),
        HUGGINGCHAT("HuggingChat", "https://huggingface.co/chat/?q="),
        DUCK_AI("Duck.ai", "https://duck.ai/?q="),
        QWEN_CHAT("Qwen Chat", "https://chat.qwen.ai/?q="),
        CHARACTER_AI("Character AI", "https://character.ai/?q=");

        final String label;
        private final String searchPrefix;

        SearchEngine(String label, String searchPrefix) {
            this.label = label;
            this.searchPrefix = searchPrefix;
        }

        String searchUrl(String query) {
            if (query == null || query.trim().isEmpty()) {
                throw new IllegalArgumentException("Type a search or web address first.");
            }
            try {
                return searchPrefix + URLEncoder.encode(query.trim(), "UTF-8");
            } catch (UnsupportedEncodingException impossible) {
                throw new IllegalStateException("UTF-8 is unavailable.", impossible);
            }
        }

        static SearchEngine fromName(String name) {
            if (name != null) {
                for (SearchEngine engine : values()) {
                    if (engine.name().equals(name)) return engine;
                }
            }
            return DUCKDUCKGO;
        }
    }

    static String resolveInput(String input, SearchEngine engine) {
        String value = input == null ? "" :
 input.trim();
        if (value.isEmpty()) throw new IllegalArgumentException("Type a search or web address first.");
        if (value.length() > MAX_INPUT_LENGTH) {
            throw new IllegalArgumentException("Web searches and addresses can be at most 2048 characters.");
        }
        if (looksLikeBareDomain(value)) return requireAllowedWebUrl("https://" + value);
        if (SCHEME.matcher(value).find()) return requireAllowedWebUrl(value);
        return (engine == null ? SearchEngine.DUCKDUCKGO : engine).searchUrl(value);
    }

    static String requireAllowedWebUrl(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("A web address is required.");
        }
        String candidate = value.trim();
        if (candidate.length() > MAX_INPUT_LENGTH + 8) {
            throw new IllegalArgumentException("This web address is too long.");
        }
        try {
            URI uri = new URI(candidate);
            String scheme = uri.getScheme();
            if ("http".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("HTTP is blocked. Use HTTPS; a per-site HTTP exception requires a separate explicit request.");
            }
            if (scheme == null || !"https".equalsIgnoreCase(scheme)) {
                throw new IllegalArgumentException("Only HTTPS pages are supported. A per-site HTTP exception requires a separate explicit request.");
            }
            if (uri.getHost() == null || uri.getHost().trim().isEmpty() || uri.getRawUserInfo() != null) {
                throw new IllegalArgumentException("Enter a valid web address without embedded credentials.");
            }
            int port = uri.getPort();
            if (port == 0 || port > 65535) {
                throw new IllegalArgumentException("Enter a valid HTTPS port between 1 and 65535.");
            }
            return uri.normalize().toASCIIString();
        } catch (IllegalArgumentException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new IllegalArgumentException("Enter a valid HTTPS web address.");
        }
    }

    stat
ic boolean isLikelyWebAddress(String value) {
        if (value == null) return false;
        String candidate = value.trim();
        return SCHEME.matcher(candidate).find() || looksLikeBareDomain(candidate);
    }

    static boolean isAllowedWebUrl(String value) {
        try {
            requireAllowedWebUrl(value);
            return true;
        } catch (IllegalArgumentException exception) {
            return false;
        }
    }

    private static boolean looksLikeBareDomain(String value) {
        if (value.indexOf('@') >= 0 || value.chars().anyMatch(Character::isWhitespace)) return false;
        int delimiter = firstOf(value, '/', '?', '#');
        String hostPart = delimiter < 0 ? value : value.substring(0, delimiter);
        return BARE_DOMAIN.matcher(value).matches() && hostPart.toLowerCase(Locale.ROOT).contains(".");
    }

    private static int firstOf(String value, char... chars) {
        int first = -1;
        for (char ch : chars) {
            int index = value.indexOf(ch);
            if (index >= 0 && (first < 0 || index < first)) first = index;
        }
        return first;
    }
}
