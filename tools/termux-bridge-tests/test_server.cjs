'use strict';
const assert = require('node:assert/strict');
const test = require('node:test');
const {validate,tools} = require('../../app/src/main/assets/builtin-plugins/dsh-tool-termux/lib/server.cjs');
test('exposes read-only probe and user-installed script execution',()=>{
  assert.deepEqual(tools.map(x=>x.name),['termux_status','termux_script']);
  assert.deepEqual(validate({name:'probe',args:['abc','1']}),{name:'probe',args:['abc','1']});
});
test('rejects traversal and arbitrary commands',()=>{
  for(const name of ['../x','/bin/sh','x;id','','UPPER']) assert.throws(()=>validate({name}));
  assert.throws(()=>validate({name:'probe',args:['hello\nworld']}));
  assert.throws(()=>validate({name:'probe',args:['a','b','c','d','e']}));
});
