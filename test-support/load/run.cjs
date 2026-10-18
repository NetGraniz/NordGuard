'use strict';
// Deliberately Paper-only, loopback-only, fresh synthetic world, bounded stages.
const fs=require('node:fs'), path=require('node:path'), net=require('node:net');
const assert=require('node:assert/strict');
const checkSessions=require('./session-count.cjs');
const {spawn}=require('node:child_process');
const os=require('node:os');
const {createHash}=require('node:crypto');
const {monitorEventLoopDelay}=require('node:perf_hooks');
const [rootArg,java,seedArg,modulesArg]=process.argv.slice(2);
assert(rootArg && java && seedArg && modulesArg,'root java seed modules required');
const root=path.resolve(rootArg), seed=path.resolve(seedArg), project=path.resolve(__dirname,'../..');
assert(path.basename(root).startsWith('nordguard-test-') && !fs.existsSync(root),'Fresh isolated root required');
const mineflayer=require(path.join(path.resolve(modulesArg),'mineflayer'));
const version=fs.readFileSync(path.join(project,'src/main/resources/plugin.yml'),'utf8').match(/^version: (.+)$/m)[1].trim();
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
const results=[];
const candidateSha256=createHash('sha256').update(fs.readFileSync(path.join(project,`target/NordGuard-${version}.jar`))).digest('hex');
let server,output='',exited=true,bots=[],fatal=null,resourceTimer;
async function until(fn,label,timeout=60000) {
  const end=Date.now()+timeout;
  while(!fn()) { if(fatal)throw fatal; if(exited || Date.now()>end)throw Error(label+' timeout/exit'); await sleep(100); }
}
async function command(line,regex) {
  const offset=output.length; server.stdin.write(line+'\n');
  await until(()=>regex.test(output.slice(offset)),line);
  return output.slice(offset).match(regex);
}
function freeMiB() {
  return Math.floor(os.freemem()/1048576);
}
async function stop() {
  clearInterval(resourceTimer);resourceTimer=null;
  for(const bot of bots) {bot.clearControlStates();bot.quit();}bots=[];
  if(server && !exited) {
    server.stdin.write('stop\n');
    const end=Date.now()+30000;while(!exited && Date.now()<end)await sleep(100);
    if(!exited) {server.kill();await sleep(1000);}
    if(!exited)throw Error('Own test server failed to stop');
  }
}
async function run(mode) {
  const dir=path.join(root,mode);fs.mkdirSync(path.join(dir,'plugins'),{recursive:true});
  for(const name of ['server.jar','cache','libraries','versions','eula.txt']) {
    if(fs.existsSync(path.join(seed,name)))fs.cpSync(path.join(seed,name),path.join(dir,name),{recursive:true});
  }
  assert(/eula=true/i.test(fs.readFileSync(path.join(dir,'eula.txt'),'utf8')),'Previously accepted EULA required');
  const properties=fs.readFileSync(path.join(project,'test-support/fixtures/server.properties'),'utf8').replace('max-players=3','max-players=50');
  fs.writeFileSync(path.join(dir,'server.properties'),properties+'\nlevel-seed=731116\nspawn-monsters=false\nspawn-animals=false\n');
  fs.copyFileSync(path.join(project,'test-support/build/LoadProbe.jar'),path.join(dir,'plugins/LoadProbe.jar'));
  if(mode==='guard') {
    fs.copyFileSync(path.join(project,`target/NordGuard-${version}.jar`),path.join(dir,`plugins/NordGuard-${version}.jar`));
    fs.mkdirSync(path.join(dir,'plugins/NordGuard'));
    fs.copyFileSync(path.join(project,'src/main/resources/config.yml'),path.join(dir,'plugins/NordGuard/config.yml'));
  }
  output='';exited=false;fatal=null;
  server=spawn(java,['-Dterminal.jline=false','-Dterminal.ansi=false','-Xms512M','-Xmx2G','-jar','server.jar','nogui'],{cwd:dir,windowsHide:true,stdio:['pipe','pipe','pipe']});
  server.on('error',e=>{fatal=e;exited=true});server.on('exit',()=>{exited=true});
  for(const stream of [server.stdout,server.stderr])stream.on('data',data=>{output+=data.toString();});
  try {
    await until(()=>/Done \(/.test(output),'startup',180000);
    assert(!/Could not load|Error occurred while enabling|Exception in server tick loop/.test(output),'Server/plugin startup failure');
    if(mode==='guard')assert(output.includes(`NordGuard ${version} enabled`));
    // Remove collision and hunger variation; both arms use identical settings.
    await command('difficulty peaceful',/difficulty|already set/i);
    server.stdin.write('gamerule minecraft:spawn_mobs false\n');
    server.stdin.write('gamerule minecraft:max_entity_cramming 0\n');
    resourceTimer=setInterval(()=>{
      try { const free=freeMiB();if(free<3072)fatal=Error('Resource stop: free RAM below 3 GiB'); }
      catch(e) {fatal=e;}
    },10000);
    resourceTimer.unref();
    for(const count of [10,25,50]) {
      console.log(mode+': joining '+count);
      while(bots.length<count) {
        if(fatal)throw fatal;
        const index=bots.length;
        const bot=mineflayer.createBot({host:'127.0.0.1',port:25659,username:'Load'+String(index).padStart(3,'0'),version:'26.2',auth:'offline',viewDistance:'tiny'});
        bots.push(bot);
        await new Promise((resolve,reject)=>{
          const timer=setTimeout(()=>reject(Error('Bot spawn timeout')),30000);
          bot.once('spawn',()=>{clearTimeout(timer);resolve();});
          bot.once('error',e=>{clearTimeout(timer);reject(e);});
          bot.once('kicked',reason=>{clearTimeout(timer);reject(Error('Bot kicked: '+JSON.stringify(reason)));});
        });
        bot.on('error',e=>{fatal=e;});
        bot.on('end',()=>{if(!exited && resourceTimer)fatal=Error('Test bot disconnected');});
        // Separate parallel lanes in already-generated spawn area, straight walking.
        server.stdin.write(`tp ${bot.username} ${index*2} -60 0\n`);
        await sleep(150);
      }
      // Start all bots together at the same lane origin for every stage.
      bots.forEach((bot,index)=>{bot.clearControlStates();server.stdin.write(`tp ${bot.username} ${index*2} -60 0\n`);});
      await sleep(3000);
      for(const bot of bots) {await bot.look(0,0,true);bot.setControlState('forward',true);}
      // Patrol north/south, keeping the measured world bounded.
      let turn=0;
      const patrol=setInterval(()=>{turn++;for(const bot of bots)bot.look(turn%2?Math.PI:0,0,true).catch(e=>{fatal=e});},5000);
      try {
        await sleep(15000);
        await command('loadprobe reset',/LOAD_RESET/);
        const generatorStart=process.cpuUsage(), generatorTime=performance.now();
        const loop=monitorEventLoopDelay({resolution:20});loop.enable();
        const start=bots.map(b=>b.entity.position.clone());
        try {await sleep(30000);} finally {loop.disable();}
        if(fatal)throw fatal;
        const report=JSON.parse((await command('loadprobe report',/LOAD_REPORT (\{[^\r\n]+\})/))[1]);
        assert.equal(report.players,count);assert(report.ticks>=500,'Server below ~17 TPS: stop increasing load');
        assert(report.moves>=count*200,'Movement workload missing');
        assert(report.msptP95<45,'Tick-time safety stop');
        const generatorCpu=process.cpuUsage(generatorStart);
        const guardStatus=mode==='guard'?(await command('nordguard status',/Samples=\d+[^\r\n]+/))[0]:null;
        const movementEventsPerPlayerSecond=report.moves/count/report.seconds;
        const generatorLoopP95Millis=loop.percentile(95)/1e6;
        const workloadQualified=movementEventsPerPlayerSecond>=18 && generatorLoopP95Millis<100;
        results.push({mode,count,...report,movementEventsPerPlayerSecond,workloadQualified,generatorCpuCores:(generatorCpu.user+generatorCpu.system)/1000/(performance.now()-generatorTime),generatorLoopP95Millis,guardStatus,freeMemoryMiB:freeMiB(),positions:bots.slice(0,3).map((b,i)=>({start:start[i],end:b.entity.position}))});
        console.log(JSON.stringify(results.at(-1)));
        if(!workloadQualified) {console.log(mode+': generator/cadence limited; no larger stage');break;}
      } finally {clearInterval(patrol);bots.forEach(b=>b.clearControlStates());}
    }
    if(mode==='guard') {
      const status=await command('nordguard status',/\bsessions=(\d+)\b/);
      checkSessions(status[0],bots.length);
    }
  } finally {await stop();fs.writeFileSync(path.join(dir,'load-output.log'),output);}
}
(async()=>{
  const available=await new Promise(resolve=>{const listener=net.createServer();listener.once('error',()=>resolve(false));listener.listen(25659,'127.0.0.1',()=>listener.close(()=>resolve(true)));});
  assert(available,'Loopback test port occupied');assert(freeMiB()>5120,'At least 5 GiB free RAM required');
  fs.mkdirSync(root);
  let error=null;
  const order=process.env.NORD_LOAD_REVERSE==='1'?['guard','baseline']:['baseline','guard'];
  try {for(const mode of order)await run(mode);}
  catch(e){error=e.stack;console.error(error);process.exitCode=1;}
  finally {await stop();fs.writeFileSync(path.join(root,'load-results.json'),JSON.stringify({platform:'Paper',guardVersion:version,candidateSha256,order,stages:[10,25,50],measurementSeconds:30,defaultObserve:true,replica:false,prediction:false,results,error},null,2));}
})();
