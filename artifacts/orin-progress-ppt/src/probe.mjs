const { Presentation, PresentationFile, column, text, fill, hug } = await import('@oai/artifact-tool');
const p=Presentation.create({slideSize:{width:1920,height:1080}});
const s=p.slides.add();
s.compose(column({width:fill,height:fill,padding:80},[text('hello',{width:fill,height:hug,style:{fontSize:80}})]),{frame:{left:0,top:0,width:1920,height:1080},baseUnit:8});
console.log('export fn len', p.export.length);
console.log('inspect', p.inspect.length);
const out = await p.export({format:'png'}).catch(e=>({err:e.message, stack:e.stack?.slice(0,300)}));
console.log(out);
