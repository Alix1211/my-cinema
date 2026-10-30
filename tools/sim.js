const {chromium}=require('playwright');
(async()=>{
const b=await chromium.launch();const p=await b.newPage();
const errs=[];p.on('pageerror',e=>errs.push(e.message));
await p.addInitScript(()=>{globalThis.__USE_TEST=1;});
await p.goto('file://'+process.cwd()+'/use-cinema.html');
await p.click('[data-act=new]');
const out=await p.evaluate(()=>{
 const T=__USE_T,S=T.S,R=T.R,CUTS=T.CUTS();
 const types=['대기','구강','삽입','로맨스','유혹','희롱','괴한','수치'];
 const places=['호텔'];
 let id=0;
 for(const a of ['A3','A4','A5','초선']){for(const t of types)for(let k=0;k<4;k++)CUTS.push({id:'f'+(id++),actor:a,place:'호텔',type:t,no:id,kind:'img',src:null});}
 // 로지로프 호텔 컷 보강
 for(const t of types)for(let k=0;k<4;k++)CUTS.push({id:'f'+(id++),actor:'로지로프',place:'호텔',type:t,no:id,kind:'img',src:null});
 const shuffle=a=>{a=a.slice();for(let i=a.length-1;i>0;i--){const j=Math.floor(Math.random()*(i+1));[a[i],a[j]]=[a[j],a[i]];}return a;};
 const tier=c=>({대기:1,구강:1,삽입:1,로맨스:2,유혹:2,희롱:2,괴한:3,수치:3}[c.type]||0);
 function arrange(cs){const n=cs.length;if(n<3)return cs;const mx=cs.reduce((a,c)=>tier(c)>=tier(a)?c:a);const rest=cs.filter(c=>c!==mx).sort((a,b)=>tier(a)-tier(b));const head=rest.slice(0,n-2);return [...head,mx,rest[n-2]];}
 function trial(strategy,hall){
  S.hall=hall;S.usage={};S.combos=[];S.dignity=100;
  S.actors={};Object.values(S.actors);
  // 배우 Lv을 감독 Lv 해금에 맞게 높임
  for(const a of ['로지로프','초선','A3','A4','A5']){(S.actors[a]={exp:560,uses:{}});}
  S.dirExp=130;
  T.newFilm();const F=R.film;
  F.actor='로지로프';F.place='호텔';F.mainType='구강';F.cast=[];
  const avail=['로지로프','초선','A3','A4','A5'];
  if(strategy==='skill'){
   const cnt={};F.judges.filter(j=>j.pref.kind==='actor'&&avail.includes(j.pref.value)).forEach(j=>cnt[j.pref.value]=(cnt[j.pref.value]||0)+1);
   const top=Object.keys(cnt).sort((x,y)=>cnt[y]-cnt[x])[0];if(top)F.actor=top;
   const ty=F.judges.find(j=>j.pref.kind==='type'&&['구강','삽입','대기','로맨스','유혹','희롱'].includes(j.pref.value));if(ty)F.mainType=ty.pref.value;
   const want=F.judges.filter(j=>j.pref.kind==='actor').map(j=>j.pref.value).filter(a=>a!==F.actor&&avail.includes(a));
   F.cast=[...new Set(want)].slice(0,2);
  }
  F.writer=strategy==='skill'?'mid':strategy==='decent'?'rookie':'self';
  T.doShoot();
  let pool=F.cands.slice(),pick=[];
  const n=F.slots;
  if(strategy==='care'){pick=shuffle(pool).slice(0,n);}
  else{
   const take=c=>{if(pick.length<n&&!pick.includes(c))pick.push(c);};
   if(strategy==='skill'){
    for(const j of F.judges){const k=Math.ceil(n/3);
     if(j.pref.kind==='actor')shuffle(pool.filter(c=>c.actor===j.pref.value)).slice(0,k).forEach(take);
     else if(j.pref.kind==='type')shuffle(pool.filter(c=>c.type===j.pref.value)).slice(0,k).forEach(take);}
   }
   shuffle(pool.filter(c=>c.type===F.mainType)).slice(0,F.req).forEach(take);
   shuffle(pool).forEach(take);
  }
  F.edit=strategy==='skill'?arrange(pick):shuffle(pick);
  const r=T.scoreFilm(F);return r.total;
 }
 const res={};
 for(const hall of [0,1,2])for(const st of ['care','decent','skill']){
  const v=[];for(let i=0;i<400;i++)v.push(trial(st,hall));v.sort((a,b)=>a-b);
  const q=x=>v[Math.floor(x*(v.length-1))];
  const thr=[64,69,72][hall],o={mean:Math.round(v.reduce((a,b)=>a+b,0)/v.length)};
  for(const sc of [3,5,7,9]){for(const dt of [0,-6,-10]){o['s'+sc+'/t'+(thr+dt)]=Math.round(100*v.filter(x=>x+sc>=thr+dt).length/v.length);}}
  res[hall+'/'+st]=o;
 }
 return res;
});
console.log(JSON.stringify(out,null,0).replace(/\},/g,'},\n'));console.log('ERRS',errs);
await b.close();})();
