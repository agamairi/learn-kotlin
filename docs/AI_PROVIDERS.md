# AI provider notes (bring-your-own-key tutor)

Researched 2026-10-24 by Codex GPT-5.6-Luna from official docs. The Anthropic version header (`2026-10-21`) and the removal of `temperature`/`top_p`/`top_k` in that version were re-checked by the PM against https://platform.claude.com/docs/en/api/versioning. Re-verify model IDs and shapes against each vendor's docs before release; keep them as constants in one place.

## Anthropic Messages API

Current endpoint: `POST https://api.anthropic.com/v1/messages`.

Required headers:

- `Authorization: Bearer YOUR_API_KEY`
- `anthropic-version: 2026-10-21`
- `Content-Type: application/json`

The legacy `x-api-key` header remains supported, but Anthropic documents `Authorization` as the current form. The latest API version removes `temperature`, `top_p`, and `top_k`; sending them returns HTTP 400. Therefore, no current request can satisfy the temperature requirement. This is **unverified** for legacy API versions and individual older models. ([authentication](https://platform.claude.com/docs/en/api/overview), [versioning](https://platform.claude.com/docs/en/api/versioning), [Messages reference](https://platform.claude.com/docs/en/api/messages/create))

Current JSON:

```json
{
  "model": "claude-haiku-5-5",
  "max_tokens": 256,
  "system": [
    {
      "type": "text",
      "text": "You are a patient Kotlin tutor."
    }
  ],
  "messages": [
    {
      "role": "user",
      "content": "Explain Kotlin null safety simply."
    }
  ]
}
```

Successful response shape:

```json
{
  "id": "msg_...",
  "type": "message",
  "role": "assistant",
  "content": [
    {
      "type": "text",
      "text": "Kotlin null safety..."
    }
  ],
  "model": "claude-haiku-5-5",
  "stop_reason": "end_turn"
}
```

Generated text path: `content[0].text`. ([Messages guide](https://platform.claude.com/docs/en/build-with-claude/working-with-messages))

Errors:

```json
{
  "type": "error",
  "error": {
    "type": "authentication_error",
    "message": "..."
  },
  "request_id": "req_..."
}
```

Invalid or expired key: HTTP 401. Rate limit: HTTP 429. Internal server error: HTTP 500. Overloaded service: HTTP 529. ([error reference](https://platform.claude.com/docs/en/api/errors))

Cheap key test: `GET https://api.anthropic.com/v1/models` with the same authentication and version headers. ([Models API](https://platform.claude.com/docs/en/api/models))

Suitable models: fast/cheap `claude-haiku-5-5`; strong `claude-opus-5-5`. Availability for a particular key is **unverified**. ([models overview](https://platform.claude.com/docs/en/models/overview))

## OpenAI Responses API

Use the Responses API. OpenAI recommends it for direct model requests and recommends migrating existing flows toward it. ([text generation](https://developers.openai.com/api/docs/guides/text), [migration guide](https://developers.openai.com/api/docs/guides/migrate-to-responses))

Endpoint: `POST https://api.openai.com/v1/responses`.

Required headers:

- `Authorization: Bearer YOUR_API_KEY`
- `Content-Type: application/json`

There is no separate API-version header; OpenAI’s REST API is currently under `/v1`. ([API overview](https://developers.openai.com/api/reference/overview))

Minimal JSON:

```json
{
  "model": "gpt-6-luna",
  "instructions": "You are a patient Kotlin tutor.",
  "input": "Explain Kotlin null safety simply.",
  "max_output_tokens": 256,
  "temperature": 0.2
}
```

Successful response shape:

```json
{
  "id": "resp_...",
  "object": "response",
  "status": "completed",
  "output": [
    {
      "type": "message",
      "role": "assistant",
      "content": [
        {
          "type": "output_text",
          "text": "Kotlin null safety..."
        }
      ]
    }
  ]
}
```

Raw JSON text path: `output[0].content[0].text`. The SDK convenience property `output_text` is also documented. ([Responses reference](https://developers.openai.com/api/reference/resources/responses/methods/create))

Typical error shape:

```json
{
  "error": {
    "type": "invalid_request_error",
    "code": "invalid_request_error",
    "message": "...",
    "param": null
  }
}
```

Invalid key: HTTP 401. Rate limit or quota exhaustion: HTTP 429. General server failure: HTTP 500. Model overload: HTTP 503, commonly with code `server_is_overloaded`. ([error codes](https://developers.openai.com/api/docs/guides/error-codes))

Cheap key test: `GET https://api.openai.com/v1/models` with the Bearer header. ([authentication](https://developers.openai.com/api/reference/overview))

Suitable models: fast/cheap `gpt-6-luna`; strong `gpt-6-astra`. Availability for a particular key is **unverified**. ([model catalog](https://developers.openai.com/api/docs/models))

## Google Gemini API

Endpoint: `POST https://generativelanguage.googleapis.com/v1beta/models/{model}:generateContent`.

Required headers:

- `x-goog-api-key: YOUR_API_KEY`
- `Content-Type: application/json`

The API version is expressed by `/v1beta`; there is no separate version header. ([Generate Content reference](https://ai.google.dev/api/generate-content), [API-key guide](https://ai.google.dev/gemini-api/docs/api-key))

Minimal JSON:

```json
{
  "systemInstruction": {
    "parts": [
      {
        "text": "You are a patient Kotlin tutor."
      }
    ]
  },
  "contents": [
    {
      "role": "user",
      "parts": [
        {
          "text": "Explain Kotlin null safety simply."
        }
      ]
    }
  ],
  "generationConfig": {
    "maxOutputTokens": 256,
    "temperature": 0.2
  }
}
```

Generated text path:

```json
{
  "candidates": [
    {
      "content": {
        "parts": [
          {
            "text": "Kotlin null safety..."
          }
        ],
        "role": "model"
      },
      "finishReason": "STOP"
    }
  ]
}
```

Path: `candidates[0].content.parts[0].text`. ([response reference](https://ai.google.dev/api/generate-content), [text-generation guide](https://ai.google.dev/gemini-api/docs/generate-content/text-generation))

Error shape:

```json
{
  "error": {
    "code": 429,
    "message": "...",
    "status": "RESOURCE_EXHAUSTED",
    "details": []
  }
}
```

Invalid key: usually HTTP 400 with `API_KEY_INVALID`; a valid key lacking permission can produce HTTP 403. Rate limit: HTTP 429. Internal server error: HTTP 500. Overloaded/unavailable service: HTTP 503. ([API errors](https://ai.google.dev/gemini-api/docs/generate-content/api-errors))

Cheap key test: `GET https://generativelanguage.googleapis.com/v1beta/models` with `x-goog-api-key`. It validates that the key exists, but does not confirm project billing or inference eligibility. ([troubleshooting](https://ai.google.dev/gemini-api/docs/troubleshooting))

Suitable models: fast/cheap `gemini-3.5-flash-lite`; strong `gemini-4-argon`. Note that Gemini 4 ignores custom temperature, so omit `temperature` when using Argon. ([models](https://ai.google.dev/gemini-api/docs/models), [Gemini 4 guide](https://ai.google.dev/gemini-api/docs/latest-model))

## Android secret storage

`EncryptedSharedPreferences` and `MasterKey` in `androidx.security:security-crypto` are deprecated as of 1.1.0; Android recommends Android Keystore directly. ([Android API reference](https://developer.android.com/reference/kotlin/androidx/security/crypto/package-summary))

In this app, the non-exportable AES key is generated in Android Keystore, and the user’s API key is encrypted with AES-GCM. The ciphertext and IV are stored in the app’s SQLDelight `settings_kv` table, not DataStore. Android recommends AES/GCM, and Keystore keeps key material non-exportable. ([cryptography guidance](https://developer.android.com/privacy-and-security/cryptography), [Keystore](https://developer.android.com/privacy-and-security/keystore), [DataStore](https://developer.android.com/topic/libraries/architecture/datastore))

Current coordinates:

- `androidx.datastore:datastore-preferences:1.2.1`
- Optional Tink alternative: `com.google.crypto.tink:tink-android:1.24.0`

For one small secret, direct Keystore plus AES-GCM is simpler. Tink is useful if the project wants a higher-level AEAD/keyset abstraction. ([Tink setup](https://developers.google.com/tink/setup/java), [Tink AEAD](https://developers.google.com/tink/aead))

### Source list

All sources above are official Anthropic, OpenAI, Google Gemini, Android, or Google Tink documentation.
