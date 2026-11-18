'use strict';
const http = require('node:http');
const fs = require('node:fs');
const readline = require('node:readline');
const NAME = /^[a-z][a-z0-9_-]{0,31}$/;
function validate(args) {
  if (!args || typeof args !== 'object' || Array.isArray(args) ||
      Object.keys(args).some(x=>x!=='name'&&x!=='args') || !NAME.test(args.name||'')) throw Error('INVALID_SCRIPT_NAME');
  const list=args.args??[];
  if (!Array.isArray(list)||list.length>4||list.some(x=>typeof x!=='string'||x.length>200||/[\x00-\x1f\x7f]/.test(x))) throw Error('INVALID_ARGUMENTS');
  return {name:args.name,args:list};
}
function secret(path,max) {
  const a=fs.lstatSync(path);
  if (!a.isFile()||a.isSymbolicLink()||a.size<1||a.size>max||(a.mode&0o077)) throw Error('BRIDGE_SECRET_UNSAFE');
  const fd=fs.openSync(path,fs.constants.O_RDONLY|fs.constants.O_NOFOLLOW|fs.constants.O_NONBLOCK);
  try {
    const b=fs.fstatSync(fd);
    if (!b.isFile()||a.dev!==b.dev||a.ino!==b.ino||a.size!==b.size) throw Error('BRIDGE_SECRET_CHANGED');
    const buf=Buffer.alloc(max+1),n=fs.readSync(fd,buf,0,buf.length,0);
    if (n!==b.size) throw Error('BRIDGE_SECRET_CHANGED');
    return buf.subarray(0,n).toString('ascii');
  } finally {fs.closeSync(fd);}
}
function credentials() {
  const port=secret('/root/.dsh/.bridge_port',5);
  const token=secret('/root/.dsh/.bridge_token',129).trim();
  if (!/^[0-9]{1,5}$/.test(port)||+port<1||+port>65535||!/^[A-Za-z0-9_-]{1,128}$/.test(token)) throw Error('INVALID_BRIDGE_CREDENTIAL');
  return {port:+port,token};
}
function bridge(route,signal) {
  const {port,token}=credentials();
  return new Promise((resolve,reject)=>{
    let sent=false,done=false;
    function fail(reason){if(done)return;done=true;const error=Error(reason);error.executionUnknown=sent;reject(error);}
    const req=http.request({hostname:'127.0.0.1',port,path:route,method:'GET',headers:{'X-Token':token},
      agent:false,signal,timeout:30000},res=>{
      const blocks=[];let size=0;
      res.on('data',chunk=>{size+=chunk.length;if(size>220000){res.destroy();fail('BRIDGE_RESPONSE_TOO_LARGE');}else blocks.push(chunk);});
      res.on('error',()=>fail('BRIDGE_RESPONSE_ERROR'));
      res.on('end',()=>{if(done)return;try{
        if(res.statusCode!==200)throw Error('BRIDGE_STATUS');
        const outer=JSON.parse(Buffer.concat(blocks).toString('utf8'));
        if(typeof outer.result!=='string')throw Error('BRIDGE_FORMAT');
        const value=JSON.parse(outer.result);
        if(typeof value.state!=='string')throw Error('BRIDGE_FORMAT');
        done=true;resolve(value);
      }catch{fail('BRIDGE_INVALID_RESPONSE');}});
    });
    req.on('timeout',()=>req.destroy(Error('BRIDGE_TIMEOUT')));
    req.on('error',()=>fail('BRIDGE_DISCONNECTED'));
    sent=true;req.end();
  });
}
const tools=[
 {name:'termux_status',description:'Check whether Termux is installed and RUN_COMMAND permission granted. This does not execute a command or check Termux allow-external-apps.',inputSchema:{type:'object',properties:{},additionalProperties:false}},
 {name:'termux_script',description:'Run a user-installed script ~/.dsha-bridge/scripts/<name>.sh via official Termux RUN_COMMAND. Only for explicitly requested tasks that DSHA Ubuntu cannot handle; no root, no bypass of native device policy. UNKNOWN means execution may have occurred and MUST NOT be retried automatically.',inputSchema:{type:'object',properties:{name:{type:'string',pattern:'^[a-z][a-z0-9_-]{0,31}$'},args:{type:'array',maxItems:4,items:{type:'string',maxLength:200}}},required:['name'],additionalProperties:false}}
];
async function call(name,args={},signal) {
  try {
    let route;
    if(name==='termux_status')route='/app/termux/status';
    else if(name==='termux_script') {
      const v=validate(args),query=new URLSearchParams({script:v.name});
      v.args.forEach((value,i)=>query.set('arg'+i,value));
      route='/app/termux/run?'+query;
    } else throw Error('UNKNOWN_TOOL');
    const result=await bridge(route,signal);
    return {content:[{type:'text',text:JSON.stringify(result)}],isError:!['READY','COMPLETED'].includes(result.state)};
  }catch(error){
    const result={state:error.executionUnknown?'UNKNOWN':'NOT_SENT',reason:error.executionUnknown?'TRANSPORT_ERROR_NO_REPLAY':error.message};
    return {content:[{type:'text',text:JSON.stringify(result)}],isError:true};
  }
}
function start(){
  const input=readline.createInterface({input:process.stdin,crlfDelay:Infinity});
  const reply=(id,result,error)=>process.stdout.write(JSON.stringify({jsonrpc:'2.0',id,...(error?{error}:{result})})+'\n');
  input.on('line',async(line)=>{
    let m;try{m=JSON.parse(line);}catch{return;}
    if(m.id===undefined)return;
    try{
      if(m.method==='initialize')reply(m.id,{protocolVersion:m.params?.protocolVersion||'2025-03-26',
        capabilities:{tools:{listChanged:false}},serverInfo:{name:'DSHA Termux Bridge',version:'0.1.0'},
        instructions:'Only run scripts installed by the user. Never replay UNKNOWN executions.'});
      else if(m.method==='ping')reply(m.id,{});
      else if(m.method==='tools/list')reply(m.id,{tools});
      else if(m.method==='tools/call')reply(m.id,await call(m.params?.name,m.params?.arguments??{}));
      else reply(m.id,null,{code:-32601,message:'Method not found'});
    }catch(e){reply(m.id,{content:[{type:'text',text:String(e.message||'BRIDGE_ERROR')}],isError:true});}
  });
}
if(require.main===module)start();
module.exports={validate,tools,call};
