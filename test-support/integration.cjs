'use strict';
const assert = require('node:assert/strict');
const fs = require('node:fs');
const path = require('node:path');
const net = require('node:net');
const { spawn } = require('node:child_process');
const [rootArg, java, seedArg, platform, modules] = process.argv.slice(2);
const root = path.resolve(rootArg), seed = path.resolve(seedArg);
assert(path.basename(root).startsWith('nordguard-test-') && !fs.existsSync(root), 'Fresh isolated directory required');
assert(['Paper', 'Folia'].includes(platform));
const mineflayer = require(path.join(path.resolve(modules), 'mineflayer'));
const project = path.resolve(__dirname, '..');
let server, bot, output = '', exited = false;
const passed = [];
const sleep = ms => new Promise(resolve => setTimeout(resolve, ms));
async function until(fn, label, timeout = 60000) {
  const start = Date.now();
  while (!fn()) {
    if (Date.now() - start > timeout || exited || output.includes('GUARD_PROBE_FAIL')) throw Error('Timeout/exit/probe failure: ' + label);
    await sleep(50);
  }
}
async function marker(command, pattern) {
  const offset = output.length;
  server.stdin.write(command + '\n');
  await until(() => pattern.test(output.slice(offset)), command);
  return output.slice(offset).match(pattern);
}
function pass(label) { passed.push(label); console.log('PASS: ' + label); }
async function main() {
  const occupied = await new Promise(resolve => {
    const socket = net.connect({host:'127.0.0.1', port:25659});
    socket.on('connect', () => { socket.destroy(); resolve(true); }); socket.on('error', () => resolve(false));
  });
  assert(!occupied, 'Test port occupied');
  fs.mkdirSync(path.join(root, 'plugins'), {recursive:true});
  for (const name of ['server.jar','cache','libraries','versions','eula.txt']) {
    if (fs.existsSync(path.join(seed,name))) fs.cpSync(path.join(seed,name),path.join(root,name),{recursive:true});
  }
  assert(/eula=true/i.test(fs.readFileSync(path.join(root,'eula.txt'),'utf8')), 'Existing accepted EULA required');
  fs.copyFileSync(path.join(__dirname,'fixtures/server.properties'),path.join(root,'server.properties'));
  fs.copyFileSync(path.join(project,'target/NordGuard-0.1.0.jar'),path.join(root,'plugins/NordGuard-0.1.0.jar'));
  fs.copyFileSync(path.join(__dirname,'build/GuardProbe.jar'),path.join(root,'plugins/GuardProbe.jar'));
  server = spawn(java,['-Dterminal.jline=false','-Dterminal.ansi=false','-Xms256M','-Xmx1400M','-jar','server.jar','nogui'],
    {cwd:root,windowsHide:true,stdio:['pipe','pipe','pipe']});
  for (const stream of [server.stdout,server.stderr]) stream.on('data', chunk => { output += chunk.toString(); });
  server.on('exit',()=>{exited=true});
  await until(()=>/Done \(/.test(output), 'startup',180000);
  assert(output.includes('NordGuard 0.1.0 enabled')); pass(platform+' startup');
  await marker('nordguard status', /sessions=0/); pass('console status');
  await marker('nordguard reload', /configuration reloaded/); pass('reload');
  const configPath=path.join(root,'plugins/NordGuard/config.yml');
  const originalConfig=fs.readFileSync(configPath,'utf8');
  fs.writeFileSync(configPath,originalConfig.replace('flight: OBSERVE','flight: BROKEN'));
  await marker('nordguard reload',/Reload rejected; previous policy kept/);
  fs.writeFileSync(configPath,originalConfig);
  await marker('nordguard reload',/configuration reloaded/); pass('invalid reload retains last valid policy');
  bot=mineflayer.createBot({host:'127.0.0.1',port:25659,username:'GuardFixture',version:'26.2',auth:'offline'});
  bot.on('error',error=>{output+='\nBOT_ERROR '+error.message});
  await new Promise((resolve,reject)=>{bot.once('spawn',resolve);bot.once('error',reject);setTimeout(()=>reject(Error('bot spawn timeout')),60000)});
  await marker('guardprobe prepare GuardFixture',/GUARD_PREPARED/);
  await sleep(4000);
  await marker('guardprobe probe GuardFixture',/GUARD_GEOMETRY_PASS/); pass('actual block-shape support and body clearance');
  await marker('guardprobe permissions GuardFixture',/GUARD_PERMISSIONS_PASS/); pass('ordinary account has no admin, alerts or bypass');
  await marker('nordguard status',/sessions=1/); pass('player scheduler active');
  await marker('guardprobe fall GuardFixture',/GUARD_NATIVE_PASS fall/); pass('native fall damage and FALL event');
  await marker('guardprobe cancel GuardFixture',/GUARD_NATIVE_PASS cancel/); pass('cancelled FALL event preserves health');
  await marker('guardprobe partial GuardFixture',/GUARD_NATIVE_PASS partial/); pass('native partial recovery applies only unpaid base damage');
  await marker('guardprobe correct GuardFixture',/GUARD_CORRECT_READY/);
  bot.physicsEnabled=false;
  const lifted=await marker('guardprobe lift GuardFixture',/GUARD_LIFT ([\d.-]+) ([\d.-]+) ([\d.-]+)/);
  const x=+lifted[1], top=+lifted[2], z=+lifted[3];
  const sendPosition=y=>{
    bot.entity.position.set(x,y,z);
    bot.entity.onGround=y<=top-8;
    bot._client.write('position',{x,y,z,flags:{onGround:bot.entity.onGround,hasHorizontalCollision:false}});
  };
  for(let i=0;i<100;i++){sendPosition(top);await sleep(50)}
  await marker('nordguard status',/FLIGHT: [1-9]\d*/); pass('stationary unsupported hover detected');
  for(let y=top-.25;y>=top-8;y-=.25){sendPosition(y);await sleep(50)}
  for(let i=0;i<12;i++){sendPosition(top-8);await sleep(50)}
  const health=await marker('guardprobe health GuardFixture',/GUARD_HEALTH ([\d.]+)/);
  assert(+health[1]<20,'Vanilla must still apply damage during an ordinary landing');
  const ordinary=await marker('nordguard status',/NOFALL: (\d+)/);
  assert.equal(+ordinary[1],0,'Ordinary damage must not trigger NoFall');
  pass('existing native fall damage is not duplicated');
  await marker('guardprobe suppress GuardFixture',/GUARD_SUPPRESSION_READY/);
  const fault=await marker('guardprobe lift GuardFixture',/GUARD_LIFT ([\d.-]+) ([\d.-]+) ([\d.-]+)/);
  const fx=+fault[1], fy=+fault[2], fz=+fault[3];
  const faultPosition=y=>{
    bot.entity.position.set(fx,y,fz);
    bot.entity.onGround=true;
    bot._client.write('position',{x:fx,y,z:fz,flags:{onGround:true,hasHorizontalCollision:false}});
  };
  for(let i=0;i<100;i++){faultPosition(fy);await sleep(50)}
  for(let y=fy-.25;y>=fy-8;y-=.25){faultPosition(y);await sleep(50)}
  for(let i=0;i<12;i++){faultPosition(fy-8);await sleep(50)}
  await marker('guardprobe health GuardFixture',/GUARD_HEALTH ([\d.]+)/);
  await marker('nordguard status',/NOFALL: [1-9]\d*/);
  const corrected=await marker('guardprobe health GuardFixture',/GUARD_HEALTH ([\d.]+)/);
  assert(+corrected[1]<20,'Missing fall damage must be restored by the native bridge');
  pass('injected fall-distance suppression detected and corrected natively');
  await marker('guardprobe prepare GuardFixture',/GUARD_PREPARED/);
  await marker('guardprobe setback GuardFixture',/GUARD_SETBACK_READY/);
  await sleep(4000);
  const supported=bot.entity.position.clone();
  let setback=false;
  const forced=()=>{setback=true};
  bot.on('forcedMove',forced);
  for(let i=0;i<100&&!setback;i++) {
    bot.entity.position.set(supported.x,supported.y+4,supported.z);
    bot.entity.onGround=true;
    bot._client.write('position',{x:supported.x,y:supported.y+4,z:supported.z,
      flags:{onGround:true,hasHorizontalCollision:false}});
    await sleep(50);
  }
  await until(()=>setback,'movement setback',10000);
  bot.removeListener('forcedMove',forced);
  const landed=await marker('guardprobe health GuardFixture',/GUARD_HEALTH [\d.]+ FALL_EVENTS=\d+ Y=([\d.-]+)/);
  assert(Math.abs(+landed[1]-supported.y)<.01,'Setback must return to supported position');
  pass('flight correction returns player to a clean supported position');
  assert(!/GUARD_PROBE_FAIL|Cannot read world asynchronously|Deferred player check after internal error/.test(output));
  pass('no region ownership errors');
}
(async()=>{
  let failure;
  try {await main()} catch(error) {failure=error;console.error(error)}
  finally {
    if(bot)bot.quit();
    if(server&&!exited){server.stdin.write('stop\n');for(let i=0;i<300&&!exited;i++)await sleep(100);if(!exited){server.kill();failure ||= Error('Server did not stop cleanly')}}
    fs.writeFileSync(path.join(root,'guard-test-output.log'),output);
    fs.writeFileSync(path.join(root,'guard-test-results.json'),JSON.stringify({platform,passed,error:failure?.message||null},null,2));
  }
  if(failure)process.exitCode=1;
})();
