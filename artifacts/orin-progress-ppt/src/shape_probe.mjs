const { Presentation, layers, shape, text, fill, fixed, hug } = await import('@oai/artifact-tool');
const p=Presentation.create({slideSize:{width:1920,height:1080}});
const s=p.slides.add();
s.compose(layers({width:fill,height:fill}, [shape({name:'bg', width:fill, height:fill, fill:'#F8FAFC'}), text('Hi',{width:fixed(400),height:hug,style:{fontSize:80,color:'#111'}})]), {frame:{left:0,top:0,width:1920,height:1080},baseUnit:8});
const b=await PresentationFile.exportPptx(p);
