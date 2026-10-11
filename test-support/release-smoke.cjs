'use strict';
// Release packaging check, not a replacement for the native/runtime matrices.
const fs=require('node:fs'),path=require('node:path'),net=require('node:net');
const assert=require('node:assert/strict');
const {spawn}=require('node:child_process');
const [rootArg,java,seedArg,platform,modules]=process.argv.slice(2);
assert(rootArg && java && seedArg && modules && ['Paper','Folia'].includes(platform));
const root=path.resolve(rootArg),seed=path.resolve(seedArg),project=path.resolve(__dirname,'..');
assert(path.basename(root).startsWith('nordguard-test-') && !fs.existsSync(root));
const version=fs.readFileSync(path.join(project,'src/main/resources/plugin.yml'),'utf8').match(/^version: (.+)$/m)[1].trim();
const mineflayer=require(path.join(path.resolve(modules),'mineflayer'));
const sleep=ms=>new Promise(r=>setTimeout(r,ms));
let server,bot,output='',exited=false,failure=null;
async function until(fn,label,timeout=60000) {
  const deadline=Date.now()+timeout;
  while(!fn()) {if(exited || failure || Date.now()>deadline)throw failure||Error(label+' timeout/exit');await sleep(100);}
}
async function command(line,re) {
  const offset=output.length;server.stdin.write(line+'\n');
  await until(()=>re.test(output.slice(offset)),line);
}
(async()=>{
  const free=await new Promise(resolve=>{const s=net.createServer();s.once('error',()=>resolve(false));s.listen(25659,'127.0.0.1',()=>s.close(()=>resolve(true)));});
  assert(free,'Test port occupied');
  fs.mkdirSync(path.join(root,'plugins'),{recursive:true});
  for(const name of ['server.jar','cache','libraries','versions','eula.txt']) {
    if(fs.existsSync(path.join(seed,name)))fs.cpSync(path.join(seed,name),path.join(root,name),{recursive:true});
  }
  assert(/eula=true/i.test(fs.readFileSync(path.join(root,'eula.txt'),'utf8')));
  fs.copyFileSync(path.join(__dirname,'fixtures/server.properties'),path.join(root,'server.properties'));
  fs.copyFileSync(path.join(project,`target/NordGuard-${version}.jar`),path.join(root,`plugins/NordGuard-${version}.jar`));
  let error=null;
  try {
    server=spawn(java,['-Dterminal.jline=false','-Dterminal.ansi=false','-Xms256M','-Xmx1400M','-jar','server.jar','nogui'],{cwd:root,windowsHide:true,stdio:['pipe','pipe','pipe']});
    server.on('exit',()=>{exited=true;});server.on('error',e=>{failure=e;exited=true;});
    for(const stream of [server.stdout,server.stderr])stream.on('data',data=>{output+=data.toString();});
    await until(()=>/Done \(/.test(output),'startup',180000);
    assert(output.includes(`NordGuard ${version} enabled`));
    assert(!/Error occurred while enabling|Could not load.*NordGuard/.test(output));
    bot=mineflayer.createBot({host:'127.0.0.1',port:25659,username:'ReleaseFixture',version:'26.2',auth:'offline'});
    bot.on('error',e=>{failure=e;});bot.on('kicked',reason=>{failure=Error('kicked '+JSON.stringify(reason));});
    await until(()=>bot.entity,'client spawn');
    await sleep(4000);await bot.look(0,0,true);bot.setControlState('forward',true);
    await sleep(6000);bot.clearControlStates();if(failure)throw failure;
    await command('nordguard status',/sessions=1/);
    await command('nordguard inspect ReleaseFixture',/Barrier RTT ms=\d+, acks=[1-9]\d*/);
    await command('nordguard reload',/configuration reloaded/);
    const config=fs.readFileSync(path.join(root,'plugins/NordGuard/config.yml'),'utf8');
    assert.equal((config.match(/: OBSERVE/g)||[]).length,17);
    assert(!config.includes(': CORRECT'));assert(/corrections=0/.test(output));
    console.log(platform+' '+version+' startup/client/native Ping-Pong/reload/defaults PASS');
  } catch(e) {error=e.stack;process.exitCode=1;console.error(error);}
  finally {
    if(bot)bot.quit();
    if(server && !exited) {
      server.stdin.write('stop\n');
      const deadline=Date.now()+30000;while(!exited && Date.now()<deadline)await sleep(100);
      if(!exited) {server.kill();error=error||'Forced test-server stop';process.exitCode=1;await sleep(1000);}
    }
    fs.writeFileSync(path.join(root,'release-smoke.log'),output);
    fs.writeFileSync(path.join(root,'release-smoke.json'),JSON.stringify({version,platform,error,exited},null,2));
  }
})();
