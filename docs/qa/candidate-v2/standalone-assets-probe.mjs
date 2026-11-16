import net from 'node:net';
import {spawn} from 'node:child_process';
import {once} from 'node:events';
import {writeFileSync} from 'node:fs';
import path from 'node:path';
const probe=net.createServer();await new Promise(r=>probe.listen(0,'127.0.0.1',r));const port=probe.address().port;await new Promise(r=>probe.close(r));
let log='';const child=spawn(process.execPath,['frontend/.next/standalone/server.js'],{cwd:process.cwd(),windowsHide:true,env:{...process.env,PORT:String(port),HOSTNAME:'127.0.0.1',BACKEND_API_URL:'http://127.0.0.1:9'}});child.stdout.on('data',d=>log+=d);child.stderr.on('data',d=>log+=d);
try{
 for(let i=0;!log.includes('Ready in');i++){if(child.exitCode!==null||i===120)throw new Error('Own standalone server failed to become ready: '+log);await new Promise(r=>setTimeout(r,100));}
 const root=await fetch('http://127.0.0.1:'+port);const html=await root.text();const assets=[...html.matchAll(/(?:src|href)="([^" ]*\/_next\/static\/[^" ]+)"/g)].map(m=>m[1]);const result=[];
 for(const asset of [...new Set(assets)].slice(0,5)){const r=await fetch(new URL(asset,'http://127.0.0.1:'+port));result.push({asset,status:r.status});}
 writeFileSync('tmp/standalone-assets.json',JSON.stringify({applicationSHA:'71ac4e0e7e74cbfe2f08fe3a049d37cd26b0c7c3',reviewedDemoTooling:'7c784c28a332d0703a201bcc4ee9496308f1b3f4',scope:'Fresh production build, standalone entrypoint on task-owned ephemeral loopback port; no Docker or public tunnel',rootStatus:root.status,assets:result},null,2));console.log(JSON.stringify({rootStatus:root.status,assets:result}));
}finally{child.kill();await once(child,'exit');writeFileSync('tmp/standalone-assets.log',log);}
