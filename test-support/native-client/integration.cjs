'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),net=require('node:net');
const {spawn}=require('node:child_process'),DelayProxy=require('../delay-proxy.cjs');
const [rootArg,jdkArg,seedArg,platform]=process.argv.slice(2);
assert(rootArg&&jdkArg&&seedArg&&['Paper','Folia'].includes(platform),'Arguments: fresh directory, JDK, seed, Paper/Folia');
const root=path.resolve(rootArg),jdk=path.resolve(jdkArg),seed=path.resolve(seedArg),project=path.resolve(__dirname,'../..');
assert(path.basename(root).startsWith('nordguard-test-')&&!fs.existsSync(root),'Fresh isolated test directory required');
const version=fs.readFileSync(path.join(project,'src/main/resources/plugin.yml'),'utf8').match(/^version: (.+)$/m)[1].trim();
const build=path.resolve(__dirname,'../build/native-client'),launch=JSON.parse(fs.readFileSync(path.join(build,'launch.json'),'utf8'));
const wurstPath=path.resolve(process.env.NORD_NATIVE_WURST_PATH||launch.wurst);
assert(fs.statSync(wurstPath).isFile(),'Wurst fixture JAR missing');
const wurstVersion=path.basename(wurstPath).replace(/^Wurst-Client-v/,'').replace(/-MC.*$/,'');
const wurstSha256=require('node:crypto').createHash('sha256').update(fs.readFileSync(wurstPath)).digest('hex');
const pluginSha256=require('node:crypto').createHash('sha256').update(fs.readFileSync(path.join(project,`target/NordGuard-${version}.jar`))).digest('hex');
const noDelay=process.env.NORD_NATIVE_NAGLE!=='1',ordinaryOnly=process.env.NORD_NATIVE_ORDINARY_ONLY==='1';
const ordinaryDuration=Number(process.env.NORD_NATIVE_DURATION_MS||2000);
const extended=process.env.NORD_NATIVE_EXTENDED==='1';
const profile=process.env.NORD_NATIVE_PROFILE==='1';
const downstepOnly=process.env.NORD_NATIVE_DOWNSTEP_ONLY==='1';
const combatOnly=process.env.NORD_NATIVE_COMBAT_ONLY==='1';
const boatsOnly=process.env.NORD_NATIVE_BOATS_ONLY==='1';
const miningOnly=process.env.NORD_NATIVE_MINING_ONLY==='1';
const combatDelays=(process.env.NORD_NATIVE_COMBAT_DELAYS||'0,100,300').split(',').map(Number);
assert(combatDelays.length>0&&combatDelays.every(delay=>[0,100,300].includes(delay)),'Invalid combat delays');
const downstepDelays=(process.env.NORD_NATIVE_DOWNSTEP_DELAYS||'0,100,300').split(',').map(Number);
assert(downstepDelays.length>0&&downstepDelays.every(delay=>[0,100,300].includes(delay)),'Invalid downstep delays');
assert(Number.isInteger(ordinaryDuration)&&ordinaryDuration>=2000&&ordinaryDuration<=30000,'Ordinary duration must be 2000..30000 ms');
const proxy=new DelayProxy({noDelay}),passed=[],measurements=[],failures=[],predictionCoverage=[];let server,client,serverOutput='',clientOutput='',serverExit=false,clientExit=true,sequence=0;
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
async function until(fn,label,timeout=60000){const start=Date.now();while(!fn()){
  if(Date.now()-start>timeout||serverExit||serverOutput.includes('GUARD_PROBE_FAIL'))throw Error('Timeout/server failure: '+label);
  await sleep(100);
}}
async function marker(command,pattern){const offset=serverOutput.length;server.stdin.write(command+'\n');
  await until(()=>pattern.test(serverOutput.slice(offset)),command);return serverOutput.slice(offset);}
function pass(label){passed.push(label);console.log('PASS: '+label);}
let lastState={},lastStateTime=0;
function state(){try{const file=path.join(root,'client/state.json');lastState=JSON.parse(fs.readFileSync(file,'utf8'));lastStateTime=fs.statSync(file).mtimeMs;}
  catch{/* A native write may briefly expose a partial file; never use a snapshot older than one second. */}
  return Date.now()-lastStateTime<=1000?lastState:{};}
async function activeState(expectedMode){let snapshot;
  await until(()=>{const next=state();if(next.screen==='DeathScreen')throw Error('Native fixture player died');
    if(clientExit)throw Error('Native client exited');
    if(next.id===sequence&&next.mode===expectedMode&&next.connected){snapshot=next;return true;}return false;
  },'fresh native state for '+expectedMode,5000);return snapshot;
}
async function control(value){const file=path.join(root,'client/control.json');fs.writeFileSync(file+'.tmp',JSON.stringify(value));
  for(let attempt=0;;attempt++){try{fs.renameSync(file+'.tmp',file);return}catch(error){
    if(!['EPERM','EBUSY','EACCES'].includes(error.code)||attempt>=30)throw error;
    await sleep(10); // Windows may briefly lock the destination while the native fixture reads it.
  }}
}
async function mode(next,options={}){const id=++sequence;await control({id,mode:next,...options,
  patrol:ordinaryDuration>2000&&['walk','sprint','jump','sneak'].includes(next)});
  await until(()=>{if(fs.existsSync(path.join(root,'client/failure.txt')))throw Error(fs.readFileSync(path.join(root,'client/failure.txt'),'utf8'));
    if(next!=='stop'&&state().screen==='DeathScreen')throw Error('Native fixture player died; case is invalid');
    if(clientExit)throw Error('Native client exited: '+clientOutput.slice(-2000));return state().id===id;},'client mode '+next,20000);}
async function stats(){const output=await marker('nordguard status',/BOATFLIGHT: \d+/);const result={};
  for(const match of output.matchAll(/\b([A-Z]+): (\d+)/g))result[match[1]]=+match[2];
  result.corrections=+output.match(/corrections=(\d+)/)[1];return result;}
async function recording(operation){
  const args=[String(server.pid),operation];
  if(operation==='JFR.start')args.push('name=NordGuardFixture','settings=profile');
  else args.push('name=NordGuardFixture','filename='+path.join(root,'native-soak.jfr'));
  const child=spawn(path.join(jdk,'bin/jcmd.exe'),args,{windowsHide:true,stdio:['ignore','pipe','pipe']});
  let output='',code;for(const stream of [child.stdout,child.stderr])stream.on('data',chunk=>output+=chunk.toString());
  child.on('error',error=>{output+=error.message;code=-1;});child.on('exit',value=>code=value);
  await until(()=>code!==undefined,'test-server JFR '+operation,15000);assert.equal(code,0,output);
  fs.appendFileSync(path.join(root,'native-soak-recording.log'),output);
}
async function prepare(terrain){await mode('idle');await marker('guardprobe prepare GuardFixture',/GUARD_PREPARED/);
  if(terrain)await marker('guardprobe terrain GuardFixture '+terrain,new RegExp('GUARD_TERRAIN '+terrain));
  await sleep(4000);
}
async function launchClient(wurst){
  lastState={};lastStateTime=0;
  const dir=path.join(root,'client');fs.mkdirSync(path.join(dir,'mods'),{recursive:true});
  fs.copyFileSync(path.join(__dirname,'options.txt'),path.join(dir,'options.txt'));
  fs.copyFileSync(launch.api,path.join(dir,'mods',path.basename(launch.api)));
  fs.copyFileSync(path.join(build,'NativeClientProbe.jar'),path.join(dir,'mods/NativeClientProbe.jar'));
  if(wurst)fs.copyFileSync(wurstPath,path.join(dir,'mods',path.basename(wurstPath)));
  const launchId=++sequence;
  await control({id:launchId,mode:'idle'});
  clientOutput='';clientExit=false;
  client=spawn(path.join(jdk,'bin/java.exe'),['-Xms256M','-Xmx1600M','--enable-native-access=ALL-UNNAMED',
    '-Dnordguard.fixture='+dir,'-cp',launch.classpath,'net.fabricmc.loader.impl.launch.knot.KnotClient',
    '--username','GuardFixture','--version','26.2','--gameDir',dir,'--assetsDir',launch.assets,'--assetIndex','32',
    '--uuid','ed78e24929c2384c8258a0c732e2c04a','--accessToken','0','--versionType','release',
    '--width','640','--height','480','--quickPlayMultiplayer','127.0.0.1:25660'],{cwd:dir,windowsHide:true,stdio:['ignore','pipe','pipe']});
  for(const stream of [client.stdout,client.stderr])stream.on('data',chunk=>clientOutput+=chunk.toString());
  client.on('exit',()=>{clientExit=true});
  await until(()=>{if(clientExit)throw Error('Native startup failed: '+clientOutput.slice(-3500));const s=state();return s.connected&&s.id===launchId;},'native Minecraft login',180000);
  pass(wurst?`actual Wurst ${wurstVersion} client connected`:'actual Minecraft 26.2 Fabric client connected without Wurst');
}
async function stopClient(){if(client&&!clientExit){try{await mode('stop')}catch{}
  for(let i=0;i<100&&!clientExit;i++)await sleep(100);if(!clientExit)client.kill();}
  if(!fs.existsSync(root))return;
  fs.writeFileSync(path.join(root,'native-client-'+(fs.existsSync(path.join(root,'client/mods',path.basename(wurstPath)))?'wurst':'ordinary')+'.log'),clientOutput);
}
function combatJson(output,kind) {
  const match=output.match(new RegExp('GUARD_COMBAT_'+kind+' (\\{[^\\r\\n]+\\})'));
  assert(match,'Missing combat JSON: '+kind);return JSON.parse(match[1]);
}
async function combatMatrix(original,config) {
  await launchClient(true);
  async function trial(delay,terrain,action,policyMode,controlMode='none',face=true,options={}) {
    await mode('idle');proxy.delay=delay;proxy.jitter=delay===300;
    let nextConfig=original.replace(/^(\s*)(reach|wallhit|criticals): OBSERVE$/gm,`$1$2: ${policyMode}`);
    if(options.packets===false)nextConfig=nextConfig.replace('  enabled: true','  enabled: false');
    fs.writeFileSync(config,nextConfig);
    await marker('nordguard reload',/configuration reloaded/);
    const ready=combatJson(await marker(`guardprobe combatprepare GuardFixture ${terrain}`,/GUARD_COMBAT_READY/),'READY');
    if(controlMode!=='none')await marker('guardprobe combatcontrol GuardFixture '+controlMode,/GUARD_COMBAT_CONTROL/);
    if(options.weapon)await marker('guardprobe combatweapon GuardFixture '+options.weapon,/GUARD_COMBAT_WEAPON/);
    await sleep(3000);const before=await stats();
    await mode(action,{targetId:ready.targetId,face});await sleep(5500);const input=await activeState(action);
    await mode('idle');await sleep(1500);
    const result=combatJson(await marker('guardprobe combatresult GuardFixture',/GUARD_COMBAT_RESULT/),'RESULT');
    const after=await stats();assert(input.attackCalls>=2,'Native attack input did not attack the fixture');
    assert(result.preAttackCalls>=2,'Server did not receive actual fixture attacks');
    if(action.includes('critical'))assert(input.enabledModules.includes('criticalsHack'),'Criticals module did not enable');
    if(action.includes('aura'))assert(input.enabledModules.includes('killauraHack'),'KillAura module did not enable');
    const data={kind:'combat',delay,jitter:proxy.jitter,terrain,action,policyMode,controlMode,face,options,input,result,before,after};
    measurements.push(data);return data;
  }
  for(const delay of combatDelays) {
    const normal=await trial(delay,'open','attack','CORRECT');
    assert(normal.result.actualDamage>0&&normal.result.preAttackCancelled===0&&normal.result.cancelledDamageEvents===0
      &&normal.after.CRITICALS===normal.before.CRITICALS,'Ordinary attacks were suppressed or flagged');
    pass(`native ordinary melee causes real damage, ${delay}ms`);
    const baseline=await trial(delay,'open','critical','OBSERVE');
    assert(baseline.result.successfulCriticalEvents>0&&baseline.result.actualDamage>0,'Packet Criticals exploit-positive control did not produce real critical damage');
    const avg=t=>t.result.actualDamage/t.result.successfulDamageEvents;
    assert(avg(baseline)>avg(normal)*1.2,'Critical baseline did not increase real damage per hit');
    pass(`actual Wurst Packet Criticals positive control increases real damage, ${delay}ms`);
    const protectedCrit=await trial(delay,'open','critical','CORRECT');
    assert(protectedCrit.after.CRITICALS>protectedCrit.before.CRITICALS,'Own Criticals evidence missing');
    assert(protectedCrit.result.actualDamage===0&&protectedCrit.result.successfulCriticalEvents===0&&protectedCrit.result.cancelledDamageEvents>0,'Packet Criticals still harms the target');
    pass(`actual Wurst Packet Criticals causes zero target damage in CORRECT, ${delay}ms`);
    const legalCrit=await trial(delay,'open','crit-jump','CORRECT');
    assert(legalCrit.result.actualDamage>0&&legalCrit.result.successfulCriticalEvents>0&&legalCrit.result.cancelledDamageEvents===0
      &&legalCrit.after.CRITICALS===legalCrit.before.CRITICALS&&legalCrit.result.playerMaxRise>.1,'Legitimate descending critical was suppressed, flagged or not exercised');
    pass(`native descending jump critical remains damaging, ${delay}ms`);
    const wallObserve=await trial(delay,'wall','killaura','OBSERVE','none',false);
    assert(wallObserve.result.actualDamage>0,'Wall exploit-positive control did not harm target');
    pass(`actual Wurst KillAura through-wall positive control, ${delay}ms`);
    const wall=await trial(delay,'wall','killaura','CORRECT','none',false);
    assert(wall.after.WALLHIT>wall.before.WALLHIT&&wall.result.preAttackCancelled>=2,'Stable wall did not cancel real KillAura attacks at one attack/sec');
    assert(wall.result.successfulDamageEvents<=1&&wall.result.actualDamage<wallObserve.result.actualDamage,'KillAura kept damaging target after wall confirmation');
    pass(`stable wall blocks subsequent real KillAura damage at ordinary cooldown, ${delay}ms`);
    const partial=await trial(delay,'partial','attack','CORRECT');
    assert(partial.result.actualDamage>0&&partial.result.preAttackCancelled===0,'Visible target above a partial wall was suppressed');
    pass(`native visible partial-wall attack remains damaging, ${delay}ms`);
    for(const terrain of ['ceiling','slab-floor']) {
      const legal=await trial(delay,terrain,'crit-jump','CORRECT');
      assert(legal.result.actualDamage>0&&legal.result.playerMaxRise>0&&legal.result.preAttackCancelled===0
        &&legal.result.cancelledDamageEvents===0&&legal.after.CRITICALS===legal.before.CRITICALS,
        `Legal ${terrain} jumping attack was suppressed, flagged or not exercised`);
      pass(`native ${terrain} jumping attacks remain damaging without Criticals evidence, ${delay}ms`);
    }
    const enchanted=await trial(delay,'open','crit-jump','CORRECT','none',true,{weapon:'sharpness'});
    assert(enchanted.result.sharpnessLevel===5&&enchanted.result.successfulCriticalEvents>0
      &&enchanted.result.actualDamage>0&&enchanted.result.cancelledDamageEvents===0
      &&enchanted.after.CRITICALS===enchanted.before.CRITICALS,'Legal enchanted critical was suppressed or not exercised');
    pass(`native Sharpness V legitimate critical remains damaging, ${delay}ms`);
    const extra=await trial(delay,'open','crit-jump','CORRECT','extra');
    assert(extra.result.successfulCriticalEvents>0&&extra.result.actualDamage>0&&extra.result.cancelledDamageEvents===0
      &&extra.after.CRITICALS===extra.before.CRITICALS&&avg(extra)>avg(legalCrit),'Other-plugin legitimate critical damage was suppressed');
    pass(`native legitimate critical retains other-plugin extra damage, ${delay}ms`);
    const cancelled=await trial(delay,'open','critical','CORRECT','cancel');
    assert(cancelled.result.actualDamage===0&&cancelled.result.cancelledDamageEvents>0
      &&cancelled.after.CRITICALS===cancelled.before.CRITICALS,'Prior other-plugin cancellation was overwritten or counted');
    pass(`prior other-plugin cancellation stays cancelled without extra Criticals evidence, ${delay}ms`);
    const combo=await trial(delay,'open','aura-critical','CORRECT');
    assert(combo.after.CRITICALS>combo.before.CRITICALS&&combo.result.actualDamage===0
      &&combo.result.cancelledDamageEvents>0,'Combined real KillAura/Packet Criticals still harms the target');
    pass(`combined actual KillAura and Packet Criticals causes zero real target damage, ${delay}ms`);
    const noObserver=await trial(delay,'wall','killaura','CORRECT','none',false,{packets:false});
    assert(noObserver.after.WALLHIT>noObserver.before.WALLHIT&&noObserver.result.preAttackCancelled>=2
      &&noObserver.result.successfulDamageEvents<=1,'WallHit owner-only fallback did not cancel later hits');
    pass(`wall check still cancels subsequent attacks with packet observer disabled, ${delay}ms`);
  }
  await mode('idle');await marker('guardprobe combatclear GuardFixture',/GUARD_COMBAT_CLEARED/);
  assert(!/Cannot read world asynchronously|IllegalAccessError|GUARD_PROBE_FAIL|Deferred player check after internal error/.test(serverOutput),'Internal/ownership error in combat');
  pass('combat matrix has no internal/ownership errors');
}
async function boatMatrix(original,config) {
  await launchClient(true);
  async function trial(terrain,action,policyMode,impulse=false) {
    await mode('idle');proxy.delay=0;proxy.jitter=false;
    fs.writeFileSync(config,original.replace(/^(\s*)boatflight: OBSERVE$/m,`$1boatflight: ${policyMode}`));
    await marker('nordguard reload',/configuration reloaded/);
    const ready=combatJson((await marker(`guardprobe boatprepare GuardFixture ${terrain}`,/GUARD_BOAT_READY/)).replaceAll('GUARD_BOAT_','GUARD_COMBAT_'),'READY');
    await sleep(4000);const before=await stats();
    if(impulse)await marker('guardprobe boatimpulse GuardFixture',/GUARD_BOAT_IMPULSE/);
    await mode(action);await sleep(5000);const input=await activeState(action);await mode('idle');await sleep(500);
    const result=combatJson((await marker('guardprobe boatresult GuardFixture',/GUARD_BOAT_RESULT/)).replaceAll('GUARD_BOAT_','GUARD_COMBAT_'),'RESULT');
    const after=await stats();assert(input.riding&&input.vehicleId===ready.boatId,'Native client is not controlling the fixture boat');
    if(action==='boatfly')assert(input.enabledModules.includes('boatFlyHack'),'Actual Wurst BoatFly did not enable');
    assert(result.sameBoat&&result.passengerCount===2&&result.cowStillRiding,'Boat/passenger tree was lost');
    const data={kind:'boat',terrain,action,policyMode,impulse,ready,input,result,before,after};measurements.push(data);
    await marker('guardprobe boatclear GuardFixture',/GUARD_BOAT_CLEARED/);return data;
  }
  for(const terrain of ['land','water','bubble']) {
    const ordinary=await trial(terrain,'boat','CORRECT');
    assert(ordinary.after.BOATFLIGHT===ordinary.before.BOATFLIGHT&&ordinary.after.corrections===ordinary.before.corrections,'Ordinary boat was flagged or corrected: '+terrain);
    if(terrain==='bubble')assert(ordinary.result.maxY>ordinary.result.startY+1,'Real bubble launch was not exercised');
    pass(`native two-passenger ${terrain} boat remains unflagged`);
  }
  const impulse=await trial('land','boat','CORRECT',true);
  assert(impulse.result.maxY>impulse.result.startY+1&&impulse.after.BOATFLIGHT===impulse.before.BOATFLIGHT,'Server-authorized boat impulse was flagged or unexercised');
  pass('native server-authorized boat impulse remains unflagged');
  const positive=await trial('land','boatfly','OBSERVE');
  assert(positive.result.maxY>positive.result.startY+5,'Actual Wurst BoatFly positive control did not ascend');
  pass('actual Wurst BoatFly has server-observed ascent in OBSERVE');
  const protectedBoat=await trial('land','boatfly','CORRECT');
  assert(protectedBoat.after.BOATFLIGHT>protectedBoat.before.BOATFLIGHT&&protectedBoat.after.corrections>protectedBoat.before.corrections,'BoatFly was not corrected');
  assert(protectedBoat.result.maxY<positive.result.maxY-3,'BoatFly ascent was not bounded by actual vehicle correction');
  pass('actual Wurst BoatFly is corrected with the original two-passenger boat intact');
}
async function miningMatrix(original,config) {
  await launchClient(true);
  for(const profile of ['dirt','stone','instant','creative'])for(const action of ['mine','mine-fast']) {
    await mode('idle');proxy.delay=0;proxy.jitter=false;
    fs.writeFileSync(config,original.replace(/^(\s*)(fastbreak|breakrate|blockreach): OBSERVE$/gm,'$1$2: CORRECT'));
    await marker('nordguard reload',/configuration reloaded/);
    const ready=combatJson((await marker(`guardprobe miningprepare GuardFixture ${profile}`,/GUARD_MINING_READY/)).replaceAll('GUARD_MINING_','GUARD_COMBAT_'),'READY');
    if(profile==='instant')assert(ready.targets.every(target=>target.breakSpeed>=1),'Legal instamine profile is not instant on the server');
    await sleep(3500);const before=await stats();
    await mode(action,{targets:ready.targets});
    await until(()=>state().miningIndex===ready.targets.length,'native mining finished '+profile+' '+action,30000);
    const input=await activeState(action);assert(input.miningCalls>0,'No native mining input');
    if(action==='mine-fast')assert(input.enabledModules.includes('fastBreakHack'),'Actual Wurst FastBreak did not enable');
    await mode('idle');await sleep(1000);
    const result=combatJson((await marker('guardprobe miningresult GuardFixture',/GUARD_MINING_RESULT/)).replaceAll('GUARD_MINING_','GUARD_COMBAT_'),'RESULT');
    const after=await stats();measurements.push({kind:'mining',profile,action,ready,input,result,before,after});
    assert(result.primaryAir===ready.targets.length&&result.acceptedBreaks===ready.targets.length,'Native mining did not produce the expected server blocks');
    assert(result.belowChanged===0,'Unrequested block below was changed');
    if(action==='mine')assert(result.cancelledBreaks===0&&after.FASTBREAK===before.FASTBREAK,'Legal mining was rejected or flagged');
    pass(`actual server mining measured: ${profile}, ${action}, ${result.acceptedBreaks} blocks, ${result.droppedItems} dropped items`);
    await marker('guardprobe miningclear GuardFixture',/GUARD_MINING_CLEARED/);
  }
}
async function main(){
  for(const port of [25659,25660])await new Promise((resolve,reject)=>{const s=net.connect({host:'127.0.0.1',port});
    s.on('connect',()=>{s.destroy();reject(Error('Test port occupied '+port))});s.on('error',resolve);});
  fs.mkdirSync(path.join(root,'plugins'),{recursive:true});
  for(const name of ['server.jar','cache','libraries','versions','eula.txt'])if(fs.existsSync(path.join(seed,name)))fs.cpSync(path.join(seed,name),path.join(root,name),{recursive:true});
  assert(/eula=true/i.test(fs.readFileSync(path.join(root,'eula.txt'),'utf8')),'Previously accepted test EULA required');
  fs.copyFileSync(path.join(project,'test-support/fixtures/server.properties'),path.join(root,'server.properties'));
  fs.copyFileSync(path.join(project,`target/NordGuard-${version}.jar`),path.join(root,`plugins/NordGuard-${version}.jar`));
  fs.copyFileSync(path.join(project,'test-support/build/GuardProbe.jar'),path.join(root,'plugins/GuardProbe.jar'));
  server=spawn(path.join(jdk,'bin/java.exe'),['-Dterminal.jline=false','-Dterminal.ansi=false','-Xms256M','-Xmx1400M','-jar','server.jar','nogui'],{cwd:root,windowsHide:true,stdio:['pipe','pipe','pipe']});
  for(const stream of [server.stdout,server.stderr])stream.on('data',chunk=>serverOutput+=chunk.toString());
  server.on('exit',()=>serverExit=true);await until(()=>/Done \(/.test(serverOutput),'server start',180000);pass(platform+' isolated startup');
  const config=path.join(root,'plugins/NordGuard/config.yml'),original=fs.readFileSync(config,'utf8');
  fs.writeFileSync(config,original.replace(/(flight|speed|spider|waterwalk|climb|noweb|noslow): OBSERVE/g,'$1: CORRECT')
    .replace('world-replica: false','world-replica: true').replace('  enabled: false','  enabled: true'));
  await marker('nordguard reload',/configuration reloaded/);await proxy.start();
  if(combatOnly){await combatMatrix(original,config);return;}
  if(boatsOnly){await boatMatrix(original,config);return;}
  if(miningOnly){await miningMatrix(original,config);return;}
  await launchClient(false);
  await prepare();await marker('guardprobe supportfloor GuardFixture',/GUARD_SUPPORT_FLOOR_PASS/);
  pass('native server floor support is separate from actual ground contact');
  for(const delay of downstepOnly?[]:[0,100,300]){
    proxy.delay=delay;proxy.jitter=delay===300;
    for(const action of ['walk','sprint','jump','sneak']){
      await prepare();const before=await stats(),origin=await activeState('idle');
      if(action==='jump'||action==='sprint'&&delay===100)await marker('guardprobe nativetrace GuardFixture',/GUARD_TRACE_STARTED/);
      await mode(action);await sleep(ordinaryDuration);const activeEnd=await activeState(action);await mode('idle');await sleep(1200);
      const after=await stats(),end=await activeState('idle'),distance=Math.hypot(end.x-origin.x,end.z-origin.z);
      measurements.push({kind:'ordinary',delay,jitter:proxy.jitter,action,distance,travel:activeEnd.travel,before,after,clientTicks:end.ticks-origin.ticks});
      const issues=[];
      if(!Number.isFinite(activeEnd.travel)||activeEnd.travel<=.5)issues.push('native input did not move');
      for(const check of ['FLIGHT','SPEED','SPIDER','HIGHJUMP','WATERWALK','CLIMB','NOWEB','NOSLOW','NOFALL','NOCLIP'])if(after[check]!==before[check])issues.push('false positive '+check);
      if(after.corrections!==before.corrections)issues.push('unexpected correction');
      if(issues.length){const issue=`native ${action}, ${delay}ms: ${issues.join(', ')}`;failures.push(issue);console.log('FAIL: '+issue);}
      else pass(`native ${action}, ${delay}ms per direction${proxy.jitter?' + jitter':''}`);
      const inspect=await marker('nordguard inspect GuardFixture',/Prediction observe-only/);
      const prediction=inspect.match(/seeds=(\d+), accepted=(\d+), mismatched=(\d+), deferred=(\d+), trials=(\d+)/);
      assert(prediction,'Prediction diagnostic missing');
      predictionCoverage.push({action,delay,seeds:+prediction[1],accepted:+prediction[2],mismatched:+prediction[3],deferred:+prediction[4],trials:+prediction[5]});
      fs.appendFileSync(path.join(root,'native-prediction.log'),inspect+'\n');
    }
  }
  for(const delay of downstepDelays) {
    proxy.delay=delay;proxy.jitter=delay===300;await prepare();
    await marker('guardprobe nativetrace GuardFixture',/GUARD_TRACE_STARTED/);
    const before=await stats();await mode('jump');await sleep(12000);await mode('idle');await sleep(1200);
    await marker('guardprobe ordinaryscenario GuardFixture downstep',/GUARD_ORDINARY_SCENARIO/);
    const origin=await activeState('idle');await mode('jump');await sleep(12000);
    const activeEnd=await activeState('jump');await mode('idle');await sleep(1200);const after=await stats();
    const issues=[];
    if(activeEnd.travel<12)issues.push('insufficient native travel across step');
    if(!Number.isFinite(activeEnd.minY)||activeEnd.minY>origin.y-.9)issues.push('one-block descent was not observed');
    for(const check of ['FLIGHT','SPEED','SPIDER','HIGHJUMP','WATERWALK','CLIMB','NOWEB','NOSLOW','NOFALL','NOCLIP'])
      if(after[check]!==before[check])issues.push('false positive '+check);
    if(after.corrections!==before.corrections)issues.push('unexpected correction');
    measurements.push({kind:'downstep',delay,jitter:proxy.jitter,before,after,origin,end:activeEnd});
    if(issues.length) {const issue=`native sprint-jump one-block downstep, ${delay}ms: ${issues.join(', ')}`;failures.push(issue);console.log('FAIL: '+issue);}
    else pass(`native sprint-jump one-block downstep after warmup, ${delay}ms per direction`);
  }
  if(!downstepOnly) {
  if(predictionCoverage.some(p=>p.action==='walk'&&p.delay===0&&p.seeds>0&&p.accepted>0&&p.trials>0))
    pass('native ordinary predictor seeded and accepted real client frames without forced chunk resends');
  else {
    const issue='Native ordinary prediction must actually seed and accept frames; zero coverage is not a pass';
    failures.push(issue);console.log('FAIL: '+issue);
  }
  }
  if(extended){
    proxy.delay=100;proxy.jitter=true;
    for(const [scenario,action,terrain] of [['slabs','walk'],['ice','sprint'],['honey','walk'],['slime','jump'],
      ['soul-sand','walk'],['speed-effect','sprint'],['jump-effect','jump'],['knockback','walk'],
      ['web','walk','noweb'],['ladder','walk','climb'],['soak','jump']]){
      await prepare(terrain);
      if(!terrain&&scenario!=='soak'&&scenario!=='knockback')await marker('guardprobe ordinaryscenario GuardFixture '+scenario,/GUARD_ORDINARY_SCENARIO/);
      await sleep(1000);const before=await stats(),origin=await activeState('idle');
      if(scenario==='soak'&&profile)await recording('JFR.start');
      await mode(action);
      if(scenario==='knockback'){await sleep(1000);await marker('guardprobe ordinaryscenario GuardFixture knockback',/GUARD_ORDINARY_SCENARIO/);}
      await sleep(scenario==='soak'?120000:scenario==='ladder'?2500:12000);const activeEnd=await activeState(action);await mode('idle');await sleep(1500);
      const after=await stats(),end=await activeState('idle'),issues=[];
      if(scenario==='soak'&&profile)await recording('JFR.stop');
      const vertical=activeEnd.y-origin.y;
      if(scenario==='ladder' ? !(vertical>1) : !(activeEnd.travel>.1))issues.push('scenario did not produce native movement');
      for(const check of ['FLIGHT','SPEED','SPIDER','HIGHJUMP','WATERWALK','CLIMB','NOWEB','NOSLOW','NOFALL','NOCLIP'])if(after[check]!==before[check])issues.push('false positive '+check);
      if(after.corrections!==before.corrections)issues.push('unexpected correction');
      measurements.push({kind:'extended',scenario,action,terrain,delay:100,jitter:true,travel:activeEnd.travel,vertical,before,after,origin,end});
      if(issues.length){const issue=`extended ${scenario}: ${issues.join(', ')}`;failures.push(issue);console.log('FAIL: '+issue);}
      else pass(`extended native ${scenario}, 100ms + jitter`);
      fs.appendFileSync(path.join(root,'native-prediction.log'),await marker('nordguard inspect GuardFixture',/Prediction observe-only/)+'\n');
    }
  }
  if(!ordinaryOnly){
  proxy.delay=0;proxy.jitter=false;await stopClient();await sleep(1500);await launchClient(true);
  for(const delay of [0,100,300]){
    proxy.delay=delay;proxy.jitter=delay===300;
    for(const [action,terrain,check] of [['flight',null,'FLIGHT'],['speed',null,'SPEED'],['spider','spider','SPIDER'],['water','waterwalk','WATERWALK']]){
      await prepare(terrain);const before=await stats(),origin=await activeState('idle');await mode(action);const enabled=await activeState(action);
      assert(enabled.moduleEnabled,'Actual Wurst module must be enabled: '+action);
      await sleep(5000);await mode('idle');await sleep(1200);
      const after=await stats(),end=await activeState('idle');measurements.push({kind:'wurst',delay,jitter:proxy.jitter,action,terrain,before,after,origin,enabled,end});
      if(after[check]<=before[check]||after.corrections<=before.corrections){const issue=`Wurst ${action}, ${delay}ms: own evidence or completed correction missing`;failures.push(issue);console.log('FAIL: '+issue);}
      else pass(`actual Wurst ${action}, ${delay}ms detected and corrected`);
    }
  }
  }
  proxy.delay=0;proxy.jitter=false;await prepare();
  for(let cycle=1;cycle<=2;cycle++) {
    await marker('guardprobe disableguard GuardFixture',/GUARD_PACKET_CLEANUP_PASS/);
    await marker('guardprobe enableguard GuardFixture',/GUARD_PLUGIN_REENABLED/);
    await sleep(1500);
    const initial=await marker('nordguard status',/Samples=\d+/);
    assert(/sessions=1\b/.test(initial),'Re-enabled plugin must attach the existing player');
    const samples=+initial.match(/Samples=(\d+)/)[1];
    await marker('nordguard inspect GuardFixture',/Barrier RTT ms=\d+, acks=[1-9]\d*/);
    await sleep(200);const progressed=await marker('nordguard status',/Samples=\d+/);
    assert(+progressed.match(/Samples=(\d+)/)[1]>samples,'Re-enabled plugin must resume sampling');
    pass(`disable/enable same plugin instance resumes sessions and native acknowledgements, cycle ${cycle}`);
  }
  assert(!/Deferred player check after internal error|Cannot read world asynchronously|Packet diagnostics disabled for session|IllegalAccessError|GUARD_PROBE_FAIL/.test(serverOutput));
  pass('no NordGuard internal or region-access errors');
  if(failures.length)throw Error(failures.join('; '));
}
(async()=>{let failure;try{await main()}catch(error){failure=error;console.error(error)}finally{
  await stopClient();proxy.close();if(server&&!serverExit){server.stdin.write('stop\n');for(let i=0;i<300&&!serverExit;i++)await sleep(100);if(!serverExit){server.kill();failure ||= Error('Server did not stop cleanly')}}
  if(fs.existsSync(root)){
    fs.writeFileSync(path.join(root,'native-server-output.log'),serverOutput);
    fs.writeFileSync(path.join(root,'native-results.json'),JSON.stringify({platform,version,pluginSha256,noDelay,ordinaryOnly,ordinaryDuration,extended,profile,downstepOnly,downstepDelays,combatOnly,combatDelays,boatsOnly,miningOnly,wurstVersion,wurstSha256,passed,failures,predictionCoverage,measurements,error:failure?.message||null},null,2));
  }
}if(failure)process.exitCode=1;})();
