"""Generate independent local secrets without printing their values."""
import argparse
import secrets
from pathlib import Path
from urllib.parse import urlparse
from cryptography.fernet import Fernet

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--public-url', required=True, help='Verified HTTPS origin of your tunnel/server')
args = parser.parse_args()
url = urlparse(args.public_url)
if url.scheme != 'https' or not url.hostname or url.username or url.password or url.query or url.fragment or url.path not in ('', '/'):
    parser.error('Supply a valid HTTPS origin without credentials, query or path.')
p = Path(__file__).resolve().parents[1] / '.env.selfhost'
if p.exists():
    parser.error('Existing secrets preserved. Edit PUBLIC_BASE_URL/CORS manually if the origin changed.')
origin = args.public_url.rstrip('/')
values = {name: secrets.token_urlsafe(48) for name in ('SAFECIRCLE_API_SECRET', 'SAFECIRCLE_ACCESS_TOKEN_SECRET', 'GUARDIAN_SIGNING_SECRET', 'REVENUECAT_WEBHOOK_SECRET')}
values.update(SAFECIRCLE_DATA_ENCRYPTION_KEY=Fernet.generate_key().decode(), SAFECIRCLE_PUBLIC_BASE_URL=origin, SAFECIRCLE_ALLOWED_ORIGINS=origin, REVENUECAT_PROJECT_ID='proj5f7132ef')
with p.open('x') as stream:
    p.chmod(0o600)
    stream.write(''.join(f'{key}={value}\n' for key, value in values.items()))
print('Created .env.selfhost with private file permissions. Keep it off GitHub and retain its encryption key.')
