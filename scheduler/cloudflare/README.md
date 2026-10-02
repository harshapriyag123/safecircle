# External one-minute alert tick

This Cloudflare Worker uses a platform Cron Trigger every minute. It does not run
an endless loop inside Vercel, and it does not claim exact-time or guaranteed
emergency delivery. Delays, quotas and provider outages remain possible.

The implementation is tested but **not yet provisioned**: an authorized Cloudflare
account is required. Select Workers Free only; do not enable paid usage or accept
new agreements without the owner's approval. Keep the existing Vercel origin.

From this directory with an authenticated official Wrangler CLI:

```sh
wrangler secret put SAFECIRCLE_WORKER_SECRET
wrangler deploy
```

Enter the existing Vercel `SAFECIRCLE_WORKER_SECRET` privately when prompted. Never
write it to `wrangler.jsonc`, git, a URL or documentation. `SAFECIRCLE_PUBLIC_BASE_URL`
is the public origin already declared in the config. `workers_dev=false` disables
a public invocation URL; the fetch handler also returns 404. Only scheduled events
perform authenticated ticks, with redirects refused and bounded waits.

Verify at least two scheduled invocations, the deployed backend heartbeat, and
jobs crossing +5/+10/+15 minutes before claiming live monitoring. A tick completes
expiry cleanup and a bounded job batch; backlog can delay delivery. The backend
refuses new monitored sessions when its last completed tick is older than 90s.
Existing sessions and jobs persist across outages, but execution is delayed.
Provider acceptance and authenticated delivery receipts remain separate states.

Run adapter regression tests from the repository root:

```sh
node --test scheduler/cloudflare/worker.test.mjs
```
