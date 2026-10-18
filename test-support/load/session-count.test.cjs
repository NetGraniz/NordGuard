'use strict';
const {test}=require('node:test');
const assert=require('node:assert/strict');
const check=require('./session-count.cjs');
for(const order of ['baseline-first','guard-first'])for(const count of [10,25,50]) {
  test(`${order}: stopped stage at ${count} validates actual count`,()=>check(`NordGuard | sessions=${count} | modes={}`,count));
}
test('counts cannot match a substring or missing status',()=>{
  assert.throws(()=>check('sessions=50',5));assert.throws(()=>check('sessions=5',50));
  assert.throws(()=>check('no observer status',10));assert.throws(()=>check('sessions=25',10));
});
