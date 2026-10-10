'use strict';
const net = require('node:net');
// Test-only FIFO TCP relay. Delays actual traffic in both directions, never a production listener.
class DelayProxy {
  constructor({noDelay=false}={}) { this.noDelay=noDelay; this.delay = 0; this.jitter = false; this.clients = new Set(); this.timers = new Set(); }
  async start() {
    this.server = net.createServer(downstream => {
      const upstream = net.connect({host:'127.0.0.1',port:25659});
      downstream.setNoDelay(this.noDelay);upstream.setNoDelay(this.noDelay);
      this.clients.add(downstream); this.clients.add(upstream);
      const forward = (from, to) => {
        let lastDue = 0, sequence = 0, buffered = 0;
        from.on('data', data => {
          buffered += data.length;
          if (buffered > 8*1024*1024) { from.destroy(Error('Test proxy queue cap')); to.destroy(); return; }
          const extra = this.jitter ? [0,80,0,120][sequence++ & 3] : 0;
          const due = Math.max(lastDue, Date.now()+this.delay+extra); lastDue=due;
          const timer=setTimeout(() => {
            this.timers.delete(timer); buffered-=data.length;
            if(!to.destroyed && !to.write(data)) { from.pause(); to.once('drain',()=>from.resume()); }
          },Math.max(0,due-Date.now()));
          this.timers.add(timer);
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
