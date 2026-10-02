// Cloudflare's platform invokes this handler each minute; no in-request loop.
export async function tick(env, request = fetch) {
  const origin = new URL(env.SAFECIRCLE_PUBLIC_BASE_URL || '');
  if (origin.protocol !== 'https:' || origin.username || origin.password || origin.search || origin.hash || origin.pathname !== '/') throw new Error('Invalid backend origin');
  if (!env.SAFECIRCLE_WORKER_SECRET || env.SAFECIRCLE_WORKER_SECRET.length < 32) throw new Error('Missing worker secret');
  const response = await request(new URL('/internal/worker/tick', origin), {
    method: 'POST', headers: {Authorization: `Bearer ${env.SAFECIRCLE_WORKER_SECRET}`},
    redirect: 'error', signal: AbortSignal.timeout(75000)
  });
  if (!response.ok) throw new Error(`Backend tick failed (${response.status})`);
  const result = await response.json();
  if (result.status !== 'ok') throw new Error('Backend did not confirm tick');
}
export default {
  async scheduled(_event, env) { await tick(env); },
  fetch() { return new Response('SafeCircle scheduler: no public execution endpoint', {status: 404}); }
};
