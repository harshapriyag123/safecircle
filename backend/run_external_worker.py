"""Run on a user-controlled always-on host; never print the worker credential.

Vercel's free daily cron cannot drive minute-scale safety escalation. This
bounded HTTP runner invokes the server's durable tick every 15 seconds and
backs off on failures. It does not send notifications directly.
"""
import os
import time
import urllib.request
from urllib.parse import urlparse


def main():
    origin = os.environ['SAFECIRCLE_PUBLIC_BASE_URL'].rstrip('/')
    parsed = urlparse(origin)
    secret = os.environ['SAFECIRCLE_WORKER_SECRET']
    if parsed.scheme != 'https' or not parsed.netloc or parsed.path or parsed.username or parsed.query or parsed.fragment:
        raise SystemExit('Expected a public HTTPS origin')
    if len(secret) < 32:
        raise SystemExit('Worker secret must contain at least 32 characters')
    failures = 0
    while True:
        request = urllib.request.Request(origin + '/internal/worker/tick', data=b'',
                                         headers={'Authorization':'Bearer ' + secret}, method='POST')
        try:
            with urllib.request.urlopen(request, timeout=120) as response:
                if response.status != 200:
                    raise RuntimeError('Worker tick refused')
            failures = 0
            print('Worker tick succeeded', flush=True)
        except Exception:
            failures += 1
            # Exception messages can include connection details; never log them.
            print('Worker tick failed; retrying', flush=True)
        time.sleep(min(60, 15 * max(1, failures)))


if __name__ == '__main__':
    main()
