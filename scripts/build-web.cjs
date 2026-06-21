/* Build web do AAPS Assist (rebaseado no 2.31 real).
 * A fonte web é o build ORIGINAL do 2.31 (pasta webroot/), embarcado verbatim.
 * Isso garante paridade exata com o app real (sem re-transpile, sem risco).
 * Para editar a UI, veja reference/main.readable.jsx e peça patches cirúrgicos. */
const fs = require('fs');
const path = require('path');

const ROOT = path.resolve(__dirname, '..');
const WEBROOT = path.join(ROOT, 'webroot');
const DIST = path.join(ROOT, 'dist');

function copyDir(src, dst){
  fs.mkdirSync(dst, {recursive:true});
  for (const e of fs.readdirSync(src, {withFileTypes:true})){
    const s = path.join(src, e.name), d = path.join(dst, e.name);
    if (e.isDirectory()) copyDir(s, d);
    else fs.copyFileSync(s, d);
  }
}

console.log('[build-web] copiando webroot/ -> dist/ (build 2.31 real, verbatim)...');
fs.rmSync(DIST, {recursive:true, force:true});
copyDir(WEBROOT, DIST);
console.log('[build-web] dist/ pronto.');
