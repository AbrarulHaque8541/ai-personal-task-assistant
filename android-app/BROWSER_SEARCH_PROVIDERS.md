# Browser search providers

Daymark keeps a broad native provider selector and also offers one-tap provider chips on the browser home screen. The catalog is defined by `BrowserAddress.SearchEngine`; adding an enum entry makes it available in the selectors, while the home screen surfaces a curated set of frequently used providers.

## Web search providers

Google, DuckDuckGo, DuckDuckGo Lite, Bing, Brave Search, Yahoo, Startpage, Baidu, Yandex, Ecosia, Qwant, Mojeek, Kagi, You.com, Andi Search, Exa Search, WolframAlpha and Wikipedia.

## AI assistants and AI search destinations

ChatGPT, Perplexity, Google Gemini, Claude, Microsoft Copilot, Grok, DeepSeek, Phind, Kimi, Meta AI, Mistral Le Chat, Poe, HuggingChat, Duck.ai and Qwen Chat.

## One-tap behavior

- Enter a question in the browser address bar, then tap an AI or search provider chip to immediately navigate to that provider with the query in its URL.
- The chip also selects and persists that provider as the current default for the existing Go/Search action.
- Provider query URLs are website conventions, not a guaranteed API contract. Some websites may ignore prefilled queries, require sign-in, or ask the user to submit the question again. A true guaranteed answer-in-app integration requires a supported API and user-authorized credentials; this feature does not bypass provider access controls.
- The chips are horizontally scrollable to keep the interface compact on phones. The full catalog remains available from the provider selector.
- The browser remains offline until the user enables Online. Queries are not sent in the background.

## Security and scope

Bare domains still open over HTTPS, and explicit HTTP navigation remains blocked. The selected provider is stored in Daymark's local browser preferences. This is a provider launcher, not a federated meta-search and does not combine results from multiple providers into one page. Provider availability, regional restrictions, URL formats and query-prefill behavior should be verified on physical Android devices.
