"""Export public pages without advertising an unconnected backend as functional."""
import argparse
import shutil
from pathlib import Path


def export(output: Path):
    repo = Path(__file__).resolve().parents[1]
    pages = output / 'site'
    pages.mkdir(parents=True, exist_ok=True)
    shutil.copytree(repo / 'web/site', pages, dirs_exist_ok=True)
    (output / 'index.html').write_text('<!doctype html><html lang="en"><head><meta charset="utf-8"><meta http-equiv="refresh" content="0;url=/site/"><title>SafeCircle</title></head><body><a href="/site/">Open SafeCircle</a></body></html>')
    requirements = 'https://github.com/harshapriyag123/safecircle/blob/shipaton/completion/docs/EXTERNAL_REQUIREMENTS.md'
    index = pages / 'index.html'
    text = index.read_text().replace('href="/app/v4.html"', f'href="{requirements}"')
    text = text.replace('Open web companion', 'Backend setup').replace('Open companion →', 'Backend setup →')
    text = text.replace('<main>', '<main><aside class="note" style="margin:24px 0"><strong>Public preview: website and simulated demo.</strong> Accounts, signed Guardian sessions and external alerts require a separately hosted backend. Do not enter real personal or emergency information into the demo.</aside>', 1)
    text = text.replace('The companion connects to this server for account and session actions.', 'The companion requires a separate backend for account and session actions; it is not connected on this public preview.')
    text = text.replace('For a real Guardian session, start a session while signed in, create a Primary or Backup link, then open that signed link on another device.', 'Once the backend is deployed, sign in there, create a session and share a signed Primary or Backup link. This static preview cannot create real Guardian sessions.')
    index.write_text(text)
    privacy = pages / 'privacy.html'
    privacy.write_text(privacy.read_text().replace('<main>', '<main><p class="note">This public preview serves static pages and a fictional demo. It has no account database or live Guardian backend. The policy below describes the SafeCircle application when its backend is configured.</p>', 1))


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--output', type=Path, required=True)
    export(parser.parse_args().output)
