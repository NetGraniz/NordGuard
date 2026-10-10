'use strict';
const assert=require('node:assert/strict'),fs=require('node:fs'),path=require('node:path'),net=require('node:net');
const {spawn}=require('node:child_process'),DelayProxy=require('../delay-proxy.cjs');
const [rootArg,jdkArg,seedArg,platform]=process.argv.slice(2);
assert(rootArg&&jdkArg&&seedArg&&['Paper','Folia'].includes(platform),'Arguments: fresh directory, JDK, seed, Paper/Folia');
const root=path.resolve(rootArg),jdk=path.resolve(jdkArg),seed=path.resolve(seedArg),project=path.resolve(__dirname,'../..');
assert(path.basename(root).startsWith('nordguard-test-')&&!fs.existsSync(root),'Fresh isolated test directory required');
const version=fs.readFileSync(path.join(project,'src/main/resources/plugin.yml'),'utf8').match(/^version: (.+)$/m)[1].trim();
const build=path.resolve(__dirname,'../build/native-client'),launch=JSON.parse(fs.readFileSync(path.join(build,'launch.json'),'utf8'));
const noDelay=process.env.NORD_NATIVE_NAGLE!=='1',ordinaryOnly=process.env.NORD_NATIVE_ORDINARY_ONLY==='1';
const ordinaryDuration=Number(process.env.NORD_NATIVE_DURATION_MS||2000);
const extended=process.env.NORD_NATIVE_EXTENDED==='1';
const profile=process.env.NORD_NATIVE_PROFILE==='1';
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
function state(){try{return JSON.parse(fs.readFileSync(path.join(root,'client/state.json'),'utf8'));}catch{return {};}}
async function control(value){const file=path.join(root,'client/control.json');fs.writeFileSync(file+'.tmp',JSON.stringify(value));
  for(let attempt=0;;attempt++){try{fs.renameSync(file+'.tmp',file);return}catch(error){
    if(!['EPERM','EBUSY','EACCES'].includes(error.code)||attempt>=30)throw error;
    await sleep(10); // Windows may briefly lock the destination while the native fixture reads it.
  }}
}
async function mode(next){const id=++sequence;await control({id,mode:next,
  patrol:ordinaryDuration>2000&&['walk','sprint','jump','sneak'].includes(next)});
  await until(()=>{if(fs.existsSync(path.join(root,'client/failure.txt')))throw Error(fs.readFileSync(path.join(root,'client/failure.txt'),'utf8'));
    if(next!=='stop'&&state().screen==='DeathScreen')throw Error('Native fixture player died; case is invalid');
    if(clientExit)throw Error('Native client exited: '+clientOutput.slice(-2000));return state().id===id;},'client mode '+next,20000);}
async function stats(){const output=await marker('nordguard status',/PLACERATE: \d+/);const result={};
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
  const dir=path.join(root,'client');fs.mkdirSync(path.join(dir,'mods'),{recursive:true});
  fs.copyFileSync(path.join(__dirname,'options.txt'),path.join(dir,'options.txt'));
  fs.copyFileSync(launch.api,path.join(dir,'mods',path.basename(launch.api)));
  fs.copyFileSync(path.join(build,'NativeClientProbe.jar'),path.join(dir,'mods/NativeClientProbe.jar'));
  if(wurst)fs.copyFileSync(launch.wurst,path.join(dir,'mods',path.basename(launch.wurst)));
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
  pass(wurst?'actual Wurst 7.56 client connected':'actual Minecraft 26.2 Fabric client connected without Wurst');
}
async function stopClient(){if(client&&!clientExit){try{await mode('stop')}catch{}
  for(let i=0;i<100&&!clientExit;i++)await sleep(100);if(!clientExit)client.kill();}
  if(!fs.existsSync(root))return;
  fs.writeFileSync(path.join(root,'native-client-'+(fs.existsSync(path.join(root,'client/mods',path.basename(launch.wurst)))?'wurst':'ordinary')+'.log'),clientOutput);
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
  await launchClient(false);
  await prepare();await marker('guardprobe supportfloor GuardFixture',/GUARD_SUPPORT_FLOOR_PASS/);
  pass('native server floor support is separate from actual ground contact');
  for(const delay of [0,100,300]){
    proxy.delay=delay;proxy.jitter=delay===300;
    for(const action of ['walk','sprint','jump','sneak']){
      await prepare();const before=await stats(),origin=state();
      if(action==='jump'||action==='sprint'&&delay===100)await marker('guardprobe nativetrace GuardFixture',/GUARD_TRACE_STARTED/);
      await mode(action);await sleep(ordinaryDuration);const activeEnd=state();await mode('idle');await sleep(1200);
      const after=await stats(),end=state(),distance=Math.hypot(end.x-origin.x,end.z-origin.z);
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
  assert(predictionCoverage.some(p=>p.action==='walk'&&p.delay===0&&p.seeds>0&&p.accepted>0&&p.trials>0),
    'Native ordinary prediction must actually seed and accept frames; zero coverage is not a pass');
  pass('native ordinary predictor seeded and accepted real client frames without forced chunk resends');
  if(extended){
    proxy.delay=100;proxy.jitter=true;
    for(const [scenario,action,terrain] of [['slabs','walk'],['ice','sprint'],['honey','walk'],['slime','jump'],
      ['soul-sand','walk'],['speed-effect','sprint'],['jump-effect','jump'],['knockback','walk'],
      ['web','walk','noweb'],['ladder','walk','climb'],['soak','jump']]){
      await prepare(terrain);
      if(!terrain&&scenario!=='soak'&&scenario!=='knockback')await marker('guardprobe ordinaryscenario GuardFixture '+scenario,/GUARD_ORDINARY_SCENARIO/);
      await sleep(1000);const before=await stats(),origin=state();
      if(scenario==='soak'&&profile)await recording('JFR.start');
      await mode(action);
      if(scenario==='knockback'){await sleep(1000);await marker('guardprobe ordinaryscenario GuardFixture knockback',/GUARD_ORDINARY_SCENARIO/);}
      await sleep(scenario==='soak'?120000:scenario==='ladder'?2500:12000);const activeEnd=state();await mode('idle');await sleep(1500);
      const after=await stats(),end=state(),issues=[];
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
      await prepare(terrain);const before=await stats(),origin=state();await mode(action);const enabled=state();
      assert(enabled.moduleEnabled,'Actual Wurst module must be enabled: '+action);
      await sleep(5000);await mode('idle');await sleep(1200);
      const after=await stats(),end=state();measurements.push({kind:'wurst',delay,jitter:proxy.jitter,action,terrain,before,after,origin,enabled,end});
      if(after[check]<=before[check]||after.corrections<=before.corrections){const issue=`Wurst ${action}, ${delay}ms: own evidence or completed correction missing`;failures.push(issue);console.log('FAIL: '+issue);}
      else pass(`actual Wurst ${action}, ${delay}ms detected and corrected`);
    }
  }
  }
  assert(!/Deferred player check after internal error|Cannot read world asynchronously|Packet diagnostics disabled for session|IllegalAccessError|GUARD_PROBE_FAIL/.test(serverOutput));
  pass('no NordGuard internal or region-access errors');
  if(failures.length)throw Error(failures.join('; '));
}
(async()=>{let failure;try{await main()}catch(error){failure=error;console.error(error)}finally{
  await stopClient();proxy.close();if(server&&!serverExit){server.stdin.write('stop\n');for(let i=0;i<300&&!serverExit;i++)await sleep(100);if(!serverExit){server.kill();failure ||= Error('Server did not stop cleanly')}}
  if(fs.existsSync(root)){
    fs.writeFileSync(path.join(root,'native-server-output.log'),serverOutput);
    fs.writeFileSync(path.join(root,'native-results.json'),JSON.stringify({platform,version,noDelay,ordinaryOnly,ordinaryDuration,extended,profile,passed,failures,predictionCoverage,measurements,error:failure?.message||null},null,2));
  }
}if(failure)process.exitCode=1;})();
