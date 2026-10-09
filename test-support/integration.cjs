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
const version = fs.readFileSync(path.join(project,'src/main/resources/plugin.yml'),'utf8').match(/^version: (.+)$/m)[1].trim();
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
async function completedCorrection(baseline, label) {
  // Folia can deliver the position packet before the entity-scheduled completion runs.
  const deadline=Date.now()+3000;
  do {
    const status=await marker('nordguard status',/corrections=(\d+)/);
    if(+status[1]>baseline) return;
    await sleep(100);
  } while(Date.now()<deadline);
  assert.fail(label+' must complete a NordGuard correction, not merely receive a vanilla teleport');
}
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
  fs.copyFileSync(path.join(project,`target/NordGuard-${version}.jar`),path.join(root,`plugins/NordGuard-${version}.jar`));
  fs.copyFileSync(path.join(__dirname,'build/GuardProbe.jar'),path.join(root,'plugins/GuardProbe.jar'));
  server = spawn(java,['-Dterminal.jline=false','-Dterminal.ansi=false','-Xms256M','-Xmx1400M','-jar','server.jar','nogui'],
    {cwd:root,windowsHide:true,stdio:['pipe','pipe','pipe']});
  for (const stream of [server.stdout,server.stderr]) stream.on('data', chunk => { output += chunk.toString(); });
  server.on('exit',()=>{exited=true});
  await until(()=>/Done \(/.test(output), 'startup',180000);
  assert(output.includes(`NordGuard ${version} enabled`)); pass(platform+' startup');
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
  await new Promise((resolve,reject)=>{
    const timer=setTimeout(()=>reject(Error('bot spawn timeout')),60000);
    bot.once('spawn',()=>{clearTimeout(timer);resolve()});
    bot.once('error',error=>{clearTimeout(timer);reject(error)});
  });
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
  for(let attempt=2;attempt<=3;attempt++) {
    setback=false;
    bot.on('forcedMove',forced);
    for(let i=0;i<100&&!setback;i++) {
      bot.entity.position.set(supported.x,supported.y+4,supported.z);
      bot.entity.onGround=true;
      bot._client.write('position',{x:supported.x,y:supported.y+4,z:supported.z,
        flags:{onGround:true,hasHorizontalCollision:false}});
      await sleep(50);
    }
    bot.removeListener('forcedMove',forced);
    assert(setback,`Immediate repeated flight attempt ${attempt} must also be corrected`);
    pass(`immediate repeated flight attempt ${attempt} corrected`);
  }
  await marker('guardprobe origin GuardFixture',/GUARD_ORIGIN_RESET/);
  pass('external teleport discards the previous return anchor');
  await sleep(2000);
  const newOrigin=bot.entity.position.clone();
  setback=false;
  bot.on('forcedMove',forced);
  for(let i=0;i<100&&!setback;i++) {
    bot.entity.position.set(newOrigin.x,newOrigin.y+4,newOrigin.z);
    bot.entity.onGround=true;
    bot._client.write('position',{x:newOrigin.x,y:newOrigin.y+4,z:newOrigin.z,
      flags:{onGround:true,hasHorizontalCollision:false}});
    await sleep(50);
  }
  bot.removeListener('forcedMove',forced);
  assert(setback&&bot.entity.position.distanceTo(newOrigin)<.01,'Correction must use new supported origin');
  pass('correction after external teleport uses the new supported origin');
  await marker('guardprobe prepare GuardFixture',/GUARD_PREPARED/);
  await marker('guardprobe speed GuardFixture',/GUARD_SPEED_READY/);
  await sleep(4000);
  const walkBaseline=await marker('nordguard status',/corrections=(\d+)/);
  for(let i=0;i<10;i++) {
    const p=bot.entity.position;
    p.set(p.x+.15,p.y,p.z);
    bot.entity.onGround=true;
    bot._client.write('position',{x:p.x,y:p.y,z:p.z,flags:{onGround:true,hasHorizontalCollision:false}});
    await sleep(50);
  }
  const walkResult=await marker('nordguard status',/corrections=(\d+)/);
  assert.equal(+walkResult[1],+walkBaseline[1],'Ordinary walking must not cause correction');
  pass('ordinary walking is not corrected');
  await sleep(250);
  const speedOrigin=bot.entity.position.clone();
  const speedBaseline=await marker('nordguard status',/corrections=(\d+)/);
  let speedSetbacks=0;
  const speedTargets=[];
  const speedForced=()=>{speedSetbacks++;speedTargets.push(bot.entity.position.clone())};
  bot.on('forcedMove',speedForced);
  for(let i=0;i<120&&speedSetbacks<3;i++) {
    const p=bot.entity.position;
    p.set(p.x+1.5,p.y,p.z);
    bot.entity.onGround=true;
    bot._client.write('position',{x:p.x,y:p.y,z:p.z,flags:{onGround:true,hasHorizontalCollision:false}});
    await sleep(50);
  }
  bot.removeListener('forcedMove',speedForced);
  await sleep(200);
  const speedResult=await marker('nordguard status',/corrections=(\d+)/);
  assert(+speedResult[1]-+speedBaseline[1]>=3,'Sustained speed must receive at least three NordGuard corrections');
  assert(speedTargets.every(p=>p.distanceTo(speedOrigin)<.01),'Repeated speed must not advance the saved return position');
  pass('sustained speed receives repeated NordGuard corrections');
  await marker('guardprobe prepare GuardFixture',/GUARD_PREPARED/);
  await marker('guardprobe observe GuardFixture',/GUARD_MODE observe/);
  await sleep(4000);
  const miniOrigin=bot.entity.position.clone();
  const miniBaseline=await marker('nordguard status',/SPEED: (\d+)/);
  for(let i=1;i<=65;i++) {
    bot.entity.position.set(miniOrigin.x+i*.5,miniOrigin.y+(i%3===1?.1:0),miniOrigin.z);
    bot.entity.onGround=i%3!==1;
    const p=bot.entity.position;
    bot._client.write('position',{x:p.x,y:p.y,z:p.z,flags:{onGround:bot.entity.onGround,hasHorizontalCollision:false}});
    await sleep(50);
  }
  const miniResult=await marker('nordguard status',/SPEED: (\d+)/);
  assert(+miniResult[1]>+miniBaseline[1],'Wurst-like .5 block micro-hop speed must be detected');
  pass('moderate micro-hop speed detected below the previous allowance');
  await marker('guardprobe prepare GuardFixture',/GUARD_PREPARED/);
  await marker('guardprobe observe GuardFixture',/GUARD_MODE observe/);
  await sleep(4000);
  const legitStart=await marker('nordguard status',/SPEED: (\d+)/);
  const legitFlight=await marker('nordguard status',/FLIGHT: (\d+)/);
  bot.physicsEnabled=true;
  await bot.look(-Math.PI/2,0,true);
  bot.setControlState('forward',true); bot.setControlState('sprint',true); bot.setControlState('jump',true);
  await sleep(3000);
  bot.clearControlStates();
  await sleep(600);
  bot.physicsEnabled=false;
  const legitEnd=await marker('nordguard status',/SPEED: (\d+)/);
  const legitFlightEnd=await marker('nordguard status',/FLIGHT: (\d+)/);
  assert.equal(+legitEnd[1],+legitStart[1],'Ordinary client-physics sprint jumps must not flag speed');
  assert.equal(+legitFlightEnd[1],+legitFlight[1],'Ordinary client-physics sprint jumps must not flag flight');
  pass('ordinary client-physics repeated sprint jumps do not flag speed or flight');
  for(const kind of ['climb','noweb','waterwalk']) {
    await marker('guardprobe prepare GuardFixture',/GUARD_PREPARED/);
    await marker(`guardprobe terrain GuardFixture ${kind}`,new RegExp('GUARD_TERRAIN '+kind));
    await marker('guardprobe observe GuardFixture',/GUARD_MODE observe/);
    await sleep(4000);
    const origin=bot.entity.position.clone();
    const baseline=await marker('nordguard status',new RegExp(kind.toUpperCase()+': (\\d+)'));
    for(let i=1;i<=28;i++) {
      const dx=kind==='climb'?0:kind==='noweb'?2+i*.03:3+i*.08;
      const dy=kind==='climb'?i*.2:kind==='waterwalk'?-.6:0;
      bot.entity.position.set(origin.x+dx,origin.y+dy,origin.z); bot.entity.onGround=kind==='noweb';
      const p=bot.entity.position;
      bot._client.write('position',{x:p.x,y:p.y,z:p.z,flags:{onGround:bot.entity.onGround,hasHorizontalCollision:kind==='climb'}});
      await sleep(50);
    }
    const result=await marker('nordguard status',new RegExp(kind.toUpperCase()+': (\\d+)'));
    assert.equal(+result[1],+baseline[1],kind+' normal-context movement must not flag');
    pass(kind+' normal-context constructed movement is not flagged');
  }
  for(const kind of ['spider','waterwalk','climb','noweb']) {
    await marker('guardprobe prepare GuardFixture',/GUARD_PREPARED/);
    await marker(`guardprobe terrain GuardFixture ${kind}`,new RegExp('GUARD_TERRAIN '+kind));
    await marker(`guardprobe ${kind} GuardFixture`,new RegExp('GUARD_MODE '+kind));
    await sleep(4000);
    const origin=bot.entity.position.clone();
    const correctionBaseline=await marker('nordguard status',/corrections=(\d+)/);
    const checkBaseline=await marker('nordguard status',new RegExp(kind.toUpperCase()+': (\\d+)'));
    let returned=false, height=0;
    const listener=()=>{returned=true};
    bot.on('forcedMove',listener);
    for(let i=1;i<=90&&!returned;i++) {
      const dx=kind==='waterwalk'?i*.12:kind==='noweb'?i*.25:0;
      const dy=kind==='spider'?i*.2:kind==='climb'?i*.2872:kind==='waterwalk'&&dx>2?(i%4===0?-.05:.05):0;
      height=Math.max(height,dy);
      bot.entity.position.set(origin.x+dx,origin.y+dy,origin.z);
      bot.entity.onGround=kind==='noweb';
      const p=bot.entity.position;
      bot._client.write('position',{x:p.x,y:p.y,z:p.z,flags:{onGround:bot.entity.onGround,hasHorizontalCollision:kind==='spider'||kind==='climb'}});
      await sleep(50);
    }
    bot.removeListener('forcedMove',listener);
    await marker('guardprobe environment GuardFixture',/GUARD_ENV/);
    assert(returned,kind+' must receive a movement correction');
    if(kind==='spider') assert(height<=1.4,'Spider must be stopped well before four blocks');
    const checkResult=await marker('nordguard status',new RegExp(kind.toUpperCase()+': (\\d+)'));
    assert(+checkResult[1]>+checkBaseline[1],kind+' must add its own violation evidence');
    await completedCorrection(+correctionBaseline[1],kind);
    pass(kind+' detected and corrected in actual terrain'+(kind==='spider'?'; attempted height='+height.toFixed(2):''));
  }
  for(const action of ['item','itemcustom']) {
    await marker('guardprobe prepare GuardFixture',/GUARD_PREPARED/);
    await marker('guardprobe observe GuardFixture',/GUARD_MODE observe/);
    await sleep(4000);
    await marker(`guardprobe ${action} GuardFixture`,new RegExp('GUARD_ITEM '+action));
    await sleep(650);
    await marker('guardprobe using GuardFixture',/GUARD_USING/);
    const baseline=await marker('nordguard status',/NOSLOW: (\d+)/);
    for(let i=0;i<25;i++) {
      const p=bot.entity.position; p.set(p.x+(action==='item'?.05:.25),p.y,p.z);
      bot.entity.onGround=true;
      bot._client.write('position',{x:p.x,y:p.y,z:p.z,flags:{onGround:true,hasHorizontalCollision:false}});
      await sleep(50);
    }
    const result=await marker('nordguard status',/NOSLOW: (\d+)/);
    assert.equal(+result[1],+baseline[1],action+' permitted item-use movement must not flag');
    pass(action+' permitted server-side item-use multiplier respected');
  }
  await marker('guardprobe prepare GuardFixture',/GUARD_PREPARED/);
  await marker('guardprobe noslow GuardFixture',/GUARD_MODE noslow/);
  await sleep(4000);
  await marker('guardprobe item GuardFixture',/GUARD_ITEM item/);
  await sleep(650);
  await marker('guardprobe using GuardFixture',/GUARD_USING/);
  const useBaseline=await marker('nordguard status',/corrections=(\d+)/);
  let useReturned=false;
  const useListener=()=>{useReturned=true}; bot.on('forcedMove',useListener);
  for(let i=0;i<60&&!useReturned;i++) {
    const p=bot.entity.position; p.set(p.x+.25,p.y,p.z); bot.entity.onGround=true;
    bot._client.write('position',{x:p.x,y:p.y,z:p.z,flags:{onGround:true,hasHorizontalCollision:false}});
    await sleep(50);
  }
  bot.removeListener('forcedMove',useListener);
  assert(useReturned,'Ignoring shield slowdown must be corrected');
  await completedCorrection(+useBaseline[1],'item-use');
  await marker('nordguard status',/NOSLOW: [1-9]\d*/);
  pass('ignored shield slowdown detected and corrected');
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
