from pathlib import Path
import subprocess
source = Path("app/src/main/java/com/plus/browser/PlusPro.kt").read_text()
js = source.split('private val scanScript="""', 1)[1].split('"""', 1)[0]
pre = 'const assert=require("assert");\nconst location={href:"https://example.test/a/"};\nfunction el(href,download,text){return {href,dataset:{},innerText:text||"",getAttribute(k){return k==="download"?download:null},hasAttribute(k){return k==="download"&&download!=null}}}\nconst document={querySelectorAll(){return [el("https://example.test/a/Part01.zip",null,""),el("https://example.test/a/Part02.zip",null,""),el("https://example.test/a/Part01.zip",null,""),el("https://example.test/download", "report.pdf", "Download"),el(undefined,null,"Download")]}};\nconst performance={getEntriesByType(){return [{name:"https://example.test/a/Part01.zip",encodedBodySize:1234},{name:"https://example.test/a/video.mp4",encodedBodySize:50}]}};\n'
post = ',4);assert.equal(found[0].size,1234);assert(found.some(x=>x.name==="report.pdf"));assert(!found.some(x=>x.url.includes("undefined"))); console.log("PASS: actual scanner JavaScript detects links and resources, deduplicates, carries observed size and ignores empty URL");\n'
subprocess.run(["node", "-e", pre + "const found=JSON.parse(" + js + ");\nassert.equal(found.length" + post], check=True)
