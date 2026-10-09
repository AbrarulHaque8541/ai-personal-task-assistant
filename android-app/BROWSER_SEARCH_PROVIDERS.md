# Browser search providers

Daymark offers a broad native search selector and a horizontal row of AI provider shortcuts above the browser home page. The provider catalog is maintained in `BrowserAddress.SearchEngine` and `AiSiteCatalog`.

## Web search providers

Google, DuckDuckGo, DuckDuckGo Lite, Bing, Brave Search, Yahoo, Startpage, Baidu, Yandex, Ecosia, Qwant, Mojeek, Kagi, You.com, Andi Search, Exa Search, WolframAlpha and Wikipedia.

## AI assistants and AI search destinations

ChatGPT, Perplexity, Google Gemini, Claude, Microsoft Copilot, Grok, DeepSeek, Phind, Kimi, Meta AI, Mistral Le Chat, Poe, HuggingChat, Duck.ai, Qwen Chat and Character AI.

## One-tap behavior

- Enter a question in the browser address bar and tap an AI provider chip. Daymark immediately navigates to that provider using its configured query URL, without requiring a separate tap on Go.
- If the address bar contains a recognizable web address, or is empty, the chip opens the provider homepage instead of treating a URL as a question.
- Tapping a provider also selects and persists its corresponding provider for the existing Go/Search action.
- Query-prefill behavior is controlled by each third-party website. Some providers may ignore the query, require sign-in, or ask the user to submit the question again. A guaranteed in-app generated answer requires a supported API and user-authorized credentials; this feature does not bypass provider access controls.
- Provider chips are horizontally scrollable to keep the interface compact on phones. The full search catalog remains available in the native selector.
- The browser remains offline until the user enables Online. Queries are not sent in the background.

## Security and scope

Bare domains still open over HTTPS, and explicit HTTP navigation remains blocked. The selected provider is stored in Daymark's local browser preferences. This is not a federated meta-search and does not combine results from multiple providers into one page. Provider availability, regional restrictions, URL formats and query-prefill behavior should be verified on physical Android devices.
