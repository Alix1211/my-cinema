const {chromium}=require('playwright');
const MODE=process.argv[2]||'perfect', VW=+process.argv[3]||900, VH=+process.argv[4]||420, SHOT=process.argv[5]||'sq';
(async()=>{
const b=await chromium.launch();
const p=await b.newPage({viewport:{width:VW,height:VH}});
const errs=[];p.on('pageerror',e=>errs.push('PE '+e.message));p.on('console',m=>{if(m.type()==='error')errs.push('CE '+m.text())});
await p.addInitScript(()=>{globalThis.__USE_TEST=1;});
await p.goto('file://'+process.cwd()+'/use-cinema.html');
await p.click('[data-act=new]');
await p.click('[data-act=actor]');await p.click('[data-act=place]');await p.click('[data-act=mtype]:not([disabled])');
await p.click('[data-act=toScript]');await p.click('[data-act=writer]');
await p.click('[data-act=toShoot]');await p.waitForTimeout(1700);
await p.click('[data-act=toEdit]');
for(let i=0;i<8;i++){const el=await p.$('[data-act=add]:not([disabled])');if(!el)break;await el.click();}
await p.click('[data-act=toShow]');
await p.waitForSelector('#land',{timeout:5000});
await p.evaluate(mode=>{
 setInterval(()=>{const q=__USE_T.R.sq;if(!q||!q.on)return;const t=(performance.now()-q.T0)/1000;
  for(const c of q.plan){ if(c.done||c._p)continue; const d=c.target-t;
   if(mode==='perfect'&&d<=0.01){c._p=1;document.querySelector(c.side==='L'?'#tzL':'#tzR').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true}));}
   if(mode==='wrong'&&d<=0.01){c._p=1;document.querySelector(c.side==='L'?'#tzR':'#tzL').dispatchEvent(new PointerEvent('pointerdown',{bubbles:true,cancelable:true}));}
  }
 },5);
},MODE);
await p.waitForTimeout(500);
const info=await p.evaluate(()=>{const q=__USE_T.R.sq;return {N:q.plan.length,sides:q.plan.map(c=>c.side).join(''),endT:q.endT,per:q.per,thr:__USE_T.HALLS[__USE_T.S.hall].thr,prod:__USE_T.R.film.res.prod};});
console.log(MODE,JSON.stringify(info));
const t0=Date.now();let shots=0;const T=[3.2,4.7,7.2];
while(Date.now()-t0<90000){
 await p.waitForTimeout(150);
 if(await p.$('#after .main'))break;
 if(MODE==='perfect'&&shots<3){const t=await p.evaluate(()=>{const q=__USE_T.R.sq;return (performance.now()-q.T0)/1000;});
  const want=[ [info.per*5.0+0.19+0.10],[info.per*7+0.19+0.12],[info.per*9+0.19+0.1] ][shots][0];
  if(t>=want){await p.screenshot({path:SHOT+'_'+shots+'.png'});shots++;}}
}
await p.waitForSelector('#after .main',{timeout:30000});await p.waitForTimeout(600);
const res=await p.evaluate(()=>{const r=__USE_T.R.film.res;return {scr:r.scr,prod:+r.prod.toFixed(1),total:r.total,band:r.band};});
console.log('RESULT',JSON.stringify(res));
await p.screenshot({path:SHOT+'_result.png',fullPage:true});
console.log('ERRS',JSON.stringify(errs));
await b.close();
})();
