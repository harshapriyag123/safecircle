import io
import urllib.error

import pytest
from fastapi.testclient import TestClient

from app.entrypoint import app
from app.providers import NoProviderRedirect, PushWebhookProvider


@pytest.fixture
def provider(monkeypatch):
    monkeypatch.setenv('SAFECIRCLE_PUSH_WEBHOOK_URL', 'https://adapter.example/push')
    monkeypatch.setenv('SAFECIRCLE_PUSH_WEBHOOK_SECRET', 'x' * 32)
    return PushWebhookProvider()


@pytest.mark.parametrize('url', ['http://adapter.example', 'https://user:pass@adapter.example',
                                'https://adapter.example?key=secret', 'https://adapter.example#fragment',
                                'https://', 'https://[malformed'])
def test_invalid_destination_never_transmits(provider, url, monkeypatch):
    provider.url = url
    monkeypatch.setattr('urllib.request.build_opener', lambda *args: pytest.fail('No request permitted'))
    assert not provider.send({'delivery_id': 'test'}).accepted


def test_missing_adapter_auth_never_transmits(provider, monkeypatch):
    provider.secret = ''
    monkeypatch.setattr('urllib.request.build_opener', lambda *args: pytest.fail('No request permitted'))
    assert not provider.send({}).accepted


@pytest.mark.parametrize('body,identifier', [(b'{"message_id":"msg-1"}', 'msg-1'),
                                          (b'private provider diagnostics', None),
                                          (b'{"message_id":{"secret":"value"}}', None),
                                          (b'{"message_id":"private email@example.test"}', None),
                                          (b'x' * 5000, None)])
def test_only_allowlisted_identifier_is_retained(provider, monkeypatch, body, identifier):
    def build(*handlers):
        assert isinstance(handlers[0], NoProviderRedirect)
        class Opener:
            def open(self, request, timeout):
                assert request.get_header('Authorization') == 'Bearer ' + provider.secret
                assert request.get_header('Idempotency-key') == 'same-job'
                assert timeout == 8
                response = io.BytesIO(body)
                response.status = 202
                return response
        return Opener()
    monkeypatch.setattr('urllib.request.build_opener', build)
    result = provider.send({'delivery_id': 'same-job'})
    assert result.accepted
    assert result.provider_message_id == identifier


def test_redirect_and_exception_diagnostics_are_not_disclosed(provider, monkeypatch):
    assert NoProviderRedirect().redirect_request(None, None, 302, '', {}, 'https://other.example') is None
    class Opener:
        def open(self, *args, **kwargs):
            raise OSError('private endpoint credentials and contact')
    monkeypatch.setattr('urllib.request.build_opener', lambda *args: Opener())
    assert provider.send({}).error == 'adapter_unavailable'


@pytest.mark.parametrize('body', ['not-json', '[]', 'null', '"delivered"', '{"status":"accepted"}'])
def test_authenticated_malformed_receipt_is_validation_error(monkeypatch, body):
    monkeypatch.setenv('SAFECIRCLE_DELIVERY_RECEIPT_SECRET', 'test-receipt')
    with TestClient(app) as client:
        response = client.post('/v1/delivery-receipts/push/job', content=body,
                               headers={'Authorization': 'Bearer test-receipt'})
    assert response.status_code == 422


def test_production_push_requires_independent_adapter_auth(monkeypatch):
    from app.config import validate_production_config
    from cryptography.fernet import Fernet
    monkeypatch.setenv('SAFECIRCLE_ENV', 'production')
    for index, key in enumerate(('SAFECIRCLE_API_SECRET', 'SAFECIRCLE_ACCESS_TOKEN_SECRET',
                                 'GUARDIAN_SIGNING_SECRET', 'REVENUECAT_WEBHOOK_SECRET',
                                 'SAFECIRCLE_WORKER_SECRET', 'SAFECIRCLE_DELIVERY_RECEIPT_SECRET')):
        monkeypatch.setenv(key, str(index) * 32)
    monkeypatch.setenv('SAFECIRCLE_DATA_ENCRYPTION_KEY', Fernet.generate_key().decode())
    monkeypatch.setenv('SAFECIRCLE_PUSH_WEBHOOK_URL', 'https://adapter.example/push')
    monkeypatch.setenv('SAFECIRCLE_PUBLIC_BASE_URL', 'https://example.test')
    monkeypatch.setenv('SAFECIRCLE_ALLOW_DEMO_TOKEN', 'false')
    monkeypatch.setenv('DATABASE_URL', 'postgresql://isolated.example/test?sslmode=require')
    monkeypatch.delenv('SAFECIRCLE_PUSH_WEBHOOK_SECRET', raising=False)
    with pytest.raises(RuntimeError, match='adapter/receipt secrets'):
        validate_production_config()
    monkeypatch.setenv('SAFECIRCLE_PUSH_WEBHOOK_SECRET', '5' * 32)
    with pytest.raises(RuntimeError, match='independent'):
        validate_production_config()
    monkeypatch.setenv('SAFECIRCLE_PUSH_WEBHOOK_SECRET', 'a' * 32)
    validate_production_config()
