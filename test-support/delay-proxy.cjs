'use strict';
const net = require('node:net');
const {performance}=require('node:perf_hooks');
// Test-only FIFO TCP relay. Delays actual traffic in both directions, never a production listener.
class DelayProxy {
  constructor({noDelay=false}={}) { this.noDelay=noDelay; this.delay = 0; this.jitter = false; this.clients = new Set(); this.timers = new Set(); }
  async start() {
    this.server = net.createServer(downstream => {
      const upstream = net.connect({host:'127.0.0.1',port:25659});
      downstream.setNoDelay(this.noDelay);upstream.setNoDelay(this.noDelay);
      this.clients.add(downstream); this.clients.add(upstream);
      const forward = (from, to) => {
        let lastDue=0,sequence=0,buffered=0,queue=[],head=0,timer=null,blocked=false;
        const arm=()=>{
          if(timer!==null||blocked||head===queue.length||from.destroyed||to.destroyed)return;
          timer=setTimeout(flush,Math.max(1,queue[head].due-performance.now()));
          this.timers.add(timer);
        };
        const flush=()=>{
          this.timers.delete(timer);timer=null;
          // Only the queue head may write. Independent per-chunk timers can reorder bytes.
          while(head<queue.length&&queue[head].due<=performance.now()&&!to.destroyed){
            const entry=queue[head++];buffered-=entry.data.length;
            if(!to.write(entry.data)){
              blocked=true;from.pause();to.once('drain',()=>{blocked=false;from.resume();arm();});break;
            }
          }
          if(head===queue.length){queue=[];head=0;}
          else if(head>=64){queue=queue.slice(head);head=0;}
          arm();
        };
        const clear=()=>{
          if(timer!==null){clearTimeout(timer);this.timers.delete(timer);timer=null;}
          queue=[];head=buffered=0;
        };
        from.once('close',clear);to.once('close',clear);
        from.on('data', data => {
          buffered += data.length;
          if (buffered > 8*1024*1024 || queue.length-head>=8192) { from.destroy(Error('Test proxy queue cap')); to.destroy(); return; }
          const extra = this.jitter ? [0,80,0,120][sequence++ & 3] : 0;
          const due=Math.max(lastDue,performance.now()+this.delay+extra);lastDue=due;
          queue.push({data,due});arm();
        });
      };
      forward(downstream,upstream);forward(upstream,downstream);
      for(const s of [downstream,upstream]) {
        s.on('error',()=>{downstream.destroy();upstream.destroy()});
        s.on('close',()=>{this.clients.delete(s);downstream.destroy();upstream.destroy()});
      }
    });
    await new Promise((resolve,reject)=>{this.server.once('error',reject);this.server.listen(25660,'127.0.0.1',resolve)});
  }
  close() {
    for(const timer of this.timers) clearTimeout(timer);
    this.timers.clear();for(const client of this.clients) client.destroy();
    this.server?.close();
  }
}
module.exports=DelayProxy;
