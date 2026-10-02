# Free hosting and functional testing

## Public preview — deployed

https://safecircle-site.vercel.app/site

Published October 1, 2026 through Sites, without Railway or a new paid hosting plan. Hosting reported success; the deployment is public. Includes landing page, screenshots, labeled simulated Guardian demo, privacy, support and APK links.

This is a static public preview, not the safety backend. It has no account database, purchase flow, actual Guardian session or notification delivery. Its text explicitly distinguishes these limits. Export matching public pages using:

```sh
python scripts/export_static_preview.py --output /tmp/safecircle-public
```

The original `web/site` remains appropriate when served alongside the backend. Do not point Android's API base URL to the static preview: it has no `/v1` API.

## Free self-hosted backend — prepared, not live

Use a computer/server you already own. It must stay powered on and connected. Electricity/network and any domain costs remain your responsibility; this does not promise unlimited free cloud hosting.

1. Install Docker with Compose and install the backend Python dependencies in a virtual environment to run the setup script.
2. Obtain the HTTPS origin of your own reverse proxy/tunnel. A Cloudflare Quick Tunnel can provide a temporary testing origin without an account; it changes on restart and is not production hosting. Start it pointing at `http://127.0.0.1:8080`. The origin can be allocated before the backend becomes available.
3. Generate independent secrets once:

   ```sh
   python scripts/setup_selfhost.py --public-url https://YOUR-VERIFIED-ORIGIN
   docker compose -f compose.selfhost.yml up --build -d
   ```

   The generator rejects plain HTTP and refuses to overwrite existing keys. Keep `.env.selfhost` private and retain the encryption key. It is ignored by Git.
4. `compose.selfhost.yml` binds locally at `127.0.0.1:8080`, uses production safeguards and persists `/data/safecircle.db` in the `safecircle_data` Docker volume. Do not run `docker compose down -v`: it deletes the volume. Make consistent SQLite backups with separate encryption-key recovery. Restart the container and verify data survives before treating persistence as proven.
5. Verify the HTTPS `/health`, then `/app/v4.html`; register a test account, create a session and signed Primary/Backup links, check privacy, check in, extend ETA and resolve. Real SMS/push requires consenting contacts and configured providers. Delivery acceptance is not delivery confirmation.
6. Update Android `SAFECIRCLE_API_BASE_URL` to this verified backend origin and rebuild. Never substitute the preview URL or RevenueCat project ID.

Self-hosting setup checks passed: HTTPS validation, unique independent secrets, private file permissions, existing-key preservation and persistent-volume configuration. Docker/container startup and a public backend were not verified in this environment.

## Current access blockers

The attempted account-free Cloudflare tunnel failed because this execution environment cannot resolve/connect to its tunnel API. No temporary URL was created. No paid resources were created or plans changed.

Render is an available plugin, but connection was not confirmed. Its free web service sleeps and has an ephemeral filesystem. It cannot provide this app's durable SQLite storage and timely unattended alert worker unchanged. A free test deployment would need external durable storage and explicit handling of sleeping workers; never describe it as reliable safety hosting. Supabase/Postgres is not a drop-in replacement for the SQLite code.

A suitable always-running host with persistent storage (your own computer is one option), or approved account access for a compatible hosting service, is still needed for end-to-end shared sessions. RevenueCat/store configuration, provider credentials and store/video eligibility requirements remain in `EXTERNAL_REQUIREMENTS.md`.

References: [Cloudflare Quick Tunnels](https://developers.cloudflare.com/cloudflare-one/networks/connectors/cloudflare-tunnel/do-more-with-tunnels/trycloudflare/), [Render free limitations](https://render.com/docs/free), [Render persistent disks](https://render.com/docs/disks).

## Vercel deployment evidence — October 1, 2026

Published through Vercel Drop in the Harsha Hacks Hobby workspace. Only eight static public files were uploaded; no credentials, account database or backend code were uploaded. The corrected production project is `safecircle-site`; `safecircle-public` is the earlier upload with broken relative links and is superseded. No paid resources or plan changes were made.

Verified in the public browser: landing page, CSS and screenshot load; simulated +10-minute Backup Guardian stage; simulated Marked safe state; privacy page. The export sets an explicit `/site/` base so relative assets and links survive Vercel clean URLs. This upload is not Git-connected: subsequent changes require an explicit upload or configured Git deployment.

Live website: https://safecircle-site.vercel.app/site

Simulated demo: https://safecircle-site.vercel.app/site/demo

Accounts, actual signed Guardian sessions, persistent jobs and real delivery remain unavailable on this static website. RevenueCat billing remains native and requires store/account configuration.
