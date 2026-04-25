const mod = await import('@oai/artifact-tool');
console.log(Object.keys(mod.PresentationFile));
console.log(Object.getOwnPropertyNames(mod.PresentationFile));
console.log(Object.getOwnPropertyNames(mod.PresentationFile.prototype || {}));
