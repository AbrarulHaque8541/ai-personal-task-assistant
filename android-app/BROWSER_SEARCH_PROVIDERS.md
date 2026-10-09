# Browser search providers

Daymark's browser provider selector is backed by `BrowserAddress.SearchEngine`. Adding an enum entry automatically makes it available in both the home search selector and the browser's saved default-engine preference.

## Included web search providers

Google, DuckDuckGo, DuckDuckGo Lite, Bing, Brave Search, Yahoo, Startpage, Baidu, Yandex, Ecosia, Qwant, Mojeek, Kagi, You.com, WolframAlpha and Wikipedia.

## AI assistants and AI search

ChatGPT, Perplexity, Google Gemini, Claude, Microsoft Copilot, Grok, DeepSeek, Phind and Kimi are included as launch destinations. They open their provider website; Daymark does not call their private APIs, bypass sign-in, or guarantee that a query parameter is accepted by every provider. Provider websites may change their URL formats and may require login.

## Important behavior

- Search terms and URLs are only submitted when the user taps Go / Search.
- Bare domains still open over HTTPS, and explicit HTTP navigation remains blocked.
- The selected provider is stored in Daymark's local browser preferences.
- This is a provider selector, not a federated meta-search: it does not combine results from different providers into one result page.
- Provider availability, regional restrictions and query-prefill behavior must be verified on physical Android devices.
