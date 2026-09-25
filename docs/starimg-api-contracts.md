# Starimg API contracts observed

Verified 2026-09-23 using unauthenticated GET requests. No credential values or authenticated responses are stored here.

## Site bootstrap and news

- `GET https://ai.starimg.ru/` returns `200`, `text/html; charset=utf-8`; response sets an HttpOnly, Secure, SameSite=Lax `tokens_user_csrf` cookie.
- The page embeds `window.__STARIMG_BOOTSTRAP__` with `csrf`, `publicBase`, `apiBase`, `regions`, `state`, `instructions`, and `models`. `state.key` contains `api_key`, `access_code`, `base_url`, limit/usage and account metadata when authenticated. Treat all of these as secrets/private data; do not log or persist page HTML in diagnostics.
- `GET https://ai.starimg.ru/news/api` returns `200`, `application/json`, object `{ "posts": [...] }` with cache-control `no-store`. Each observed post has `id`, `status`, `tag`, `tagLabel`, `tagColor`, `title`, `titleEn`, `text`, `textEn`, `imgs` (site-relative image paths), and `pinned`. Parsers accept absent/unknown optional fields.

## Model catalog

- `GET https://ai.starimg.ru/v1/models` without API authorization returned `401`; `GET https://ai.starimg.ru/models` returned `404`.
- Chat uses the Anthropic-compatible `/v1/messages` contract with `x-api-key`, `anthropic-version: 2023-06-01`, JSON request body (`model`, `max_tokens`, `messages`, optional `system`, `stream`) and either JSON or `text/event-stream` response.
- Catalog item properties are retained only when actually supplied. Missing vision, context size, pricing, or reasoning parameters are unknown; model names never imply capabilities. Legacy cache values remain readable.

## Telemetry

- `GET https://ai.starimg.ru/telemetry?logs=1` without the `tokens_access_key` cookie returned `401`. Authenticated request uses that cookie; the short site session token is distinct from the `sk-` API key.
- Observed response shape from the client contract: `balance` (`remaining_tokens`, `token_limit`, `used_tokens`, `stale`) and optional `logs.logs[]` (`model`, `input_tokens`, `output_tokens`, `cached_tokens`, `cost_tokens`, `latency_ms`, `status`, `error_message`, `created_at`). Optional request ID aliases are preserved if present. The endpoint's full authenticated response and error body were not captured, so no stronger claims are made.
- Telemetry log rows do carry per-request input/output usage fields. Whether an individual chat completion response carries usage depends on the returned JSON/SSE events; the client records whether it was present. Server request IDs are extracted when supplied but were not confirmed in the unauthenticated observations.

## Chat usage, attempt identity, errors and reasoning

- The local request attempt UUID is generated for every call and is distinct from a server request ID. Both are represented independently; usage has a source (`CHAT_RESPONSE`, `TELEMETRY`, `ESTIMATE`, `UNKNOWN`). Existing aggregate message token values remain unchanged for old stored conversations.
- Non-2xx API responses are errors, not empty model lists. The observed unauthenticated 401s establish authentication is required for catalog and telemetry. Error text/body can contain private server details and must not be copied to logs/fixtures.
- News content advertises reasoning modes for particular posts/models, but no chat parameter or accepted values were verified from an API request/response. `confirmedReasoningParameters` is only populated from an explicit catalog field and otherwise remains unknown; the client does not infer options from model names or news text.

## Storage compatibility and security

- Database migration 2→3 adds nullable-by-default attribution fields and cache tables without rewriting or dropping chats/messages. Existing serialized settings with an endpoint still import; the runtime base is fixed at `https://ai.starimg.ru/v1`.
- Fixtures in tests are synthetic contract examples only. Never put API keys, access/CSRF cookies, authenticated bootstrap HTML, or live account identifiers in source, test fixtures, logs, or docs.
