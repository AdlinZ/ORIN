const { Presentation, column, text, fill, hug } = await import('@oai/artifact-tool');
const p=Presentation.create({slideSize:{width:1920,height:1080}});
const slides=[]; for (let i=1;i<=2;i++){ const s=p.slides.add(); slides.push(s); s.compose(column({width:fill,height:fill,padding:80},[text('hello '+i,{width:fill,height:hug,style:{fontSize:80}})]),{frame:{left:0,top:0,width:1920,height:1080},baseUnit:8}); console.log('slide', i, Object.keys(s), Object.getOwnPropertyNames(Object.getPrototypeOf(s)), s.id); }
console.log('active', p.getActiveSlide()?.id);
for (const target of [slides[0], slides[1], slides[1].id, 1]) { try { p.setActiveSlide(target); const b=await p.export({format:'png'}); console.log('target', typeof target, target?.id || target, p.getActiveSlide()?.id, b.size); } catch(e){ console.log('ERR target', target?.id||target, e.message); } }
