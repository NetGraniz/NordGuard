'use strict';
const fs=require('node:fs'),path=require('node:path'),{execFileSync}=require('node:child_process');
const [prismArg,javaArg]=process.argv.slice(2);
if(!prismArg||!javaArg)throw Error('Usage: node build.cjs <Prism data directory> <JDK directory>');
const prism=path.resolve(prismArg),jdk=path.resolve(javaArg),out=path.resolve(__dirname,'../build/native-client');
fs.mkdirSync(out,{recursive:true});
const libraries=[];
for(const [component,version] of [['net.minecraft','26.2'],['org.lwjgl3','3.4.1'],['net.fabricmc.fabric-loader','0.19.5']]) {
  const meta=JSON.parse(fs.readFileSync(path.join(prism,'meta',component,version+'.json'),'utf8'));
  for(const lib of [...meta.libraries,...(meta.mainJar?[meta.mainJar]:[])]) {
    if(lib.rules&&!lib.rules.some(r=>r.action==='allow'&&(!r.os||r.os.name==='windows')))continue;
    if(lib.rules?.some(r=>r.os?.name==='windows-arm64'||r.os?.arch==='aarch64'))continue;
    const [group,artifact,ver,classifier]=lib.name.split(':');
    const jar=path.join(prism,'libraries',...group.split('.'),artifact,ver,`${artifact}-${ver}${classifier?'-'+classifier:''}.jar`);
    if(!fs.existsSync(jar))throw Error('Missing installed library: '+jar);
    libraries.push(jar);
  }
}
const api=path.join(prism,'instances/26.2/minecraft/mods/fabric-api-0.161.0+26.2.jar');
execFileSync(path.join(jdk,'bin/jar.exe'),['--extract','--file',api,'--dir',out],{windowsHide:true});
const nested=fs.readdirSync(path.join(out,'META-INF/jars')).filter(n=>n.endsWith('.jar')).map(n=>path.join(out,'META-INF/jars',n));
const classes=path.join(out,'classes');fs.mkdirSync(classes,{recursive:true});
execFileSync(path.join(jdk,'bin/javac.exe'),['-encoding','UTF-8','-cp',[...libraries,...nested].join(';'),'-d',classes,path.join(__dirname,'NativeClientProbe.java')],{windowsHide:true,stdio:'inherit'});
fs.copyFileSync(path.join(__dirname,'fabric.mod.json'),path.join(classes,'fabric.mod.json'));
execFileSync(path.join(jdk,'bin/jar.exe'),['--create','--file',path.join(out,'NativeClientProbe.jar'),'-C',classes,'.'],{windowsHide:true});
fs.writeFileSync(path.join(out,'launch.json'),JSON.stringify({classpath:libraries.join(';'),api,
  wurst:path.join(prism,'instances/26.2/minecraft/mods/Wurst-Client-v7.56-MC26.2.jar'),assets:path.join(prism,'assets')},null,2));
console.log('Native fixture compiled from installed 26.2 libraries; no account files read.');
