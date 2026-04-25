const { Presentation, column, text, fill, hug } = await import('@oai/artifact-tool');
const p=Presentation.create({slideSize:{width:1920,height:1080}});
for (let i=1;i<=2;i++){ const s=p.slides.add(); s.compose(column({width:fill,height:fill,padding:80},[text('hello '+i,{width:fill,height:hug,style:{fontSize:80}})]),{frame:{left:0,top:0,width:1920,height:1080},baseUnit:8}); }
for (const opts of [{format:'png'},{format:'png', slideIndex:0},{format:'png', slideIndex:1},{format:'png', slide:1},{mimeType:'image/png'}]) {
 try { const b= await p.export(opts); console.log(JSON.stringify(opts), b.size, b.type, Object.getOwnPropertyNames(Object.getPrototypeOf(b))); }
 catch(e){ console.log('ERR', JSON.stringify(opts), e.message); }
}
