const mod = await import('@oai/artifact-tool');
for (const k of ['Presentation','PresentationFile']) { console.log(k, Object.getOwnPropertyNames(mod[k]), Object.getOwnPropertyNames(mod[k].prototype||{})); }
const pres = mod.Presentation.create({slideSize:{width:1920,height:1080}});
console.log('pres proto', Object.getOwnPropertyNames(Object.getPrototypeOf(pres)));
console.log('slides', pres.slides && Object.getOwnPropertyNames(Object.getPrototypeOf(pres.slides)), Object.keys(pres.slides));
