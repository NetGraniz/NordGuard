'use strict';
const test=require('node:test'),assert=require('node:assert/strict'),net=require('node:net');
const DelayProxy=require('./delay-proxy.cjs');
const sleep=ms=>new Promise(resolve=>setTimeout(resolve,ms));
test('jitter preserves every byte in both directions',{timeout:60000},async()=>{
  const expected=Buffer.alloc(1500*4);for(let i=0;i<1500;i++)expected.writeUInt32BE(i,i*4);
  const received=[],echoed=[];let count=0;
  const server=net.createServer(socket=>socket.on('data',data=>{received.push(data);socket.write(data);}));
  const proxy=new DelayProxy({noDelay:true});proxy.delay=30;proxy.jitter=true;let client;
  await new Promise((resolve,reject)=>{server.once('error',reject);server.listen(25659,'127.0.0.1',resolve);});
  try {
    await proxy.start();client=net.connect({host:'127.0.0.1',port:25660});client.setNoDelay(true);
    client.on('data',data=>{echoed.push(data);count+=data.length;});
    await new Promise((resolve,reject)=>{client.once('connect',resolve);client.once('error',reject);});
    for(let i=0;i<1500;i++){client.write(expected.subarray(i*4,i*4+4));await sleep(1);}
    const deadline=Date.now()+5000;while(count<expected.length&&Date.now()<deadline)await sleep(10);
    assert.equal(count,expected.length,'Every byte must arrive');
    assert.ok(Buffer.concat(received).equals(expected),'Upstream FIFO byte order');
    assert.ok(Buffer.concat(echoed).equals(expected),'Downstream FIFO byte order');
  } finally {
    client?.destroy();proxy.close();await new Promise(resolve=>server.close(resolve));
  }
});
