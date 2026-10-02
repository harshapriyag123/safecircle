import {test} from 'node:test';
import assert from 'node:assert/strict';
import worker, {tick} from './worker.mjs';
const env={SAFECIRCLE_PUBLIC_BASE_URL:'https://safecircle-site.vercel.app',SAFECIRCLE_WORKER_SECRET:'x'.repeat(32)};
test('cron authenticates a single tick and never follows redirects',async()=>{
 let calls=0; await tick(env,async(url,options)=>{calls++;assert.equal(url.pathname,'/internal/worker/tick');assert.equal(options.method,'POST');assert.equal(options.redirect,'error');assert.equal(options.headers.Authorization,'Bearer '+env.SAFECIRCLE_WORKER_SECRET);return Response.json({status:'ok'});});assert.equal(calls,1);
});
test('failed ticks stay failed for monitoring',async()=>{await assert.rejects(tick(env,async()=>new Response('',{status:503})),/503/);await assert.rejects(tick(env,async()=>Response.json({status:'not_ready'})),/confirm/);});
test('invalid configuration never sends a credential',async()=>{let calls=0;const request=()=>{calls++;};await assert.rejects(tick({...env,SAFECIRCLE_PUBLIC_BASE_URL:'http://example.com'},request));await assert.rejects(tick({...env,SAFECIRCLE_WORKER_SECRET:''},request));assert.equal(calls,0);assert.equal(worker.fetch().status,404);});
