import httpx

from app.generation.base import LLMError


class OpenAIChatClient:
    """Any OpenAI-compatible /chat/completions endpoint (OpenAI, Ollama at http://localhost:11434/v1, ...)."""

    provider = "openai"

    def __init__(self, base_url: str, api_key: str, model: str, timeout: float,
                 http: httpx.Client | None = None):
        self.model = model
        self._url = base_url.rstrip("/") + "/chat/completions"
        self._headers = {"Authorization": f"Bearer {api_key}"} if api_key else {}
        self._http = http or httpx.Client(timeout=timeout)

    def complete(self, system: str, user: str) -> str:
        body = {
            "model": self.model,
            "temperature": 0,
            "messages": [{"role": "system", "content": system}, {"role": "user", "content": user}],
        }
        try:
            response = self._http.post(self._url, headers=self._headers, json=body)
            response.raise_for_status()
            content = response.json()["choices"][0]["message"]["content"]
        except httpx.TimeoutException as e:
            raise LLMError(f"LLM timed out: {e}") from e
        except (httpx.HTTPError, KeyError, IndexError, ValueError) as e:
            raise LLMError(f"LLM request failed: {e}") from e
        if not isinstance(content, str) or not content.strip():
            raise LLMError("LLM returned an empty answer")
        return content
