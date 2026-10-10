'use strict';
// Aggregate only an isolated fixture recording. This is not a capacity benchmark.
const assert=require('node:assert/strict'),path=require('node:path'),fs=require('node:fs');
const {execFileSync}=require('node:child_process');
const [jdkArg,fileArg]=process.argv.slice(2);
assert(jdkArg&&fileArg,'Arguments: JDK directory, isolated native-soak.jfr');
const file=path.resolve(fileArg);
assert(path.basename(path.dirname(file)).startsWith('nordguard-test-')&&path.basename(file)==='native-soak.jfr');
assert(fs.statSync(file).size<=128*1024*1024,'Recording too large for this bounded summary');
const data=JSON.parse(execFileSync(path.join(path.resolve(jdkArg),'bin/jfr.exe'),['print','--json','--events',
  'jdk.ExecutionSample,jdk.GarbageCollection,jdk.GCPhasePause,jdk.GCHeapSummary,jdk.CPULoad',file],
  {encoding:'utf8',windowsHide:true,maxBuffer:128*1024*1024}));
const events=data.recording.events,of=type=>events.filter(e=>e.type===type).map(e=>e.values);
const duration=value=>{const match=/^PT([\d.]+)S$/.exec(value);assert(match,'Unsupported JFR duration: '+value);return +match[1]*1000;};
const samples=of('jdk.ExecutionSample'),pauses=of('jdk.GCPhasePause').map(e=>duration(e.duration));
const loads=of('jdk.CPULoad'),heap=of('jdk.GCHeapSummary').map(e=>Number(e.heapUsed));
const guardSamples=samples.filter(e=>e.stackTrace?.frames?.some(frame=>
  /^dev[./]nordfjell[./]guard[./]/.test(frame.method?.type?.name||''))).length;
console.log(JSON.stringify({scope:'one-player native fixture; opt-in replica/prediction enabled; no baseline or capacity claim',
  executionSamples:samples.length,stacksContainingGuard:guardSamples,collections:of('jdk.GarbageCollection').length,
  gcPauseCount:pauses.length,gcPauseTotalMs:pauses.reduce((sum,n)=>sum+n,0),gcPauseMaxMs:pauses.length?Math.max(...pauses):null,
  cpuSamples:loads.length,meanJvmCpuFraction:loads.length?loads.reduce((sum,e)=>sum+e.jvmUser+e.jvmSystem,0)/loads.length:null,
  meanHostCpuFraction:loads.length?loads.reduce((sum,e)=>sum+e.machineTotal,0)/loads.length:null,
  gcHeapSnapshotCount:heap.length,gcHeapUsedMin:heap.length?Math.min(...heap):null,gcHeapUsedMax:heap.length?Math.max(...heap):null},null,2));
