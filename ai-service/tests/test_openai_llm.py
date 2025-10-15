import httpx
import pytest

from app.generation.base import LLMError
from app.generation.openai_llm import OpenAIChatClient


def _client(handler):
    return OpenAIChatClient("http://llm.test/v1/", "", "m", timeout=1,
                            http=httpx.Client(transport=httpx.MockTransport(handler)))


def test_sends_chat_request_and_returns_content():
    def handler(request):
        assert request.url.path == "/v1/chat/completions"
        assert "Authorization" not in request.headers  # no key configured, e.g. local Ollama
        body = request.read().decode()
        assert '"temperature":0' in body.replace(" ", "")
        return httpx.Response(200, json={"choices": [{"message": {"content": "Answer [1]."}}]})

    assert _client(handler).complete("sys", "user") == "Answer [1]."


def test_timeout_becomes_llm_error():
    def handler(request):
        raise httpx.ReadTimeout("slow", request=request)

    with pytest.raises(LLMError, match="timed out"):
        _client(handler).complete("sys", "user")


@pytest.mark.parametrize("response", [
    httpx.Response(500),
    httpx.Response(200, json={"choices": []}),
    httpx.Response(200, json={"choices": [{"message": {"content": "  "}}]}),
])
def test_bad_responses_become_llm_error(response):
    with pytest.raises(LLMError):
        _client(lambda request: response).complete("sys", "user")
