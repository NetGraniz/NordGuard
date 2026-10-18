'use strict';
const assert=require('node:assert/strict');
module.exports=(status,expected)=>{
  const count=/\bsessions=(\d+)\b/.exec(status);
  assert(count,'Missing sessions status');
  assert.equal(+count[1],expected,'Active sessions must match actual connected test clients');
};
