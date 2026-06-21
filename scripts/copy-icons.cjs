const fs = require('fs');
const path = require('path');

const sizes = {
  'mipmap-mdpi':    48,
  'mipmap-hdpi':    72,
  'mipmap-xhdpi':   96,
  'mipmap-xxhdpi':  144,
  'mipmap-xxxhdpi': 192,
};

const androidResDir = path.join(__dirname, '..', 'android', 'app', 'src', 'main', 'res');
const resourcesDir  = path.join(__dirname, '..', 'resources', 'android');

if (!fs.existsSync(androidResDir)) {
  console.log('Android project not found — run npx cap add android first, then npm run copy-icons.');
  process.exit(0);
}

let copied = 0;
for (const [folder] of Object.entries(sizes)) {
  const srcFile = path.join(resourcesDir, folder, 'ic_launcher.png');
  const dstDir  = path.join(androidResDir, folder);
  if (!fs.existsSync(dstDir)) fs.mkdirSync(dstDir, { recursive: true });
  if (fs.existsSync(srcFile)) {
    fs.copyFileSync(srcFile, path.join(dstDir, 'ic_launcher.png'));
    fs.copyFileSync(srcFile, path.join(dstDir, 'ic_launcher_round.png'));
    console.log(`  ✓ ${folder}/ic_launcher.png`);
    copied++;
  } else {
    console.warn(`  ✗ Source not found: ${srcFile}`);
  }
}
// Also copy to drawable as fallback
const drawableDir = path.join(androidResDir, 'drawable');
if (!fs.existsSync(drawableDir)) fs.mkdirSync(drawableDir, { recursive: true });
const xxxhdpiSrc = path.join(resourcesDir, 'mipmap-xxxhdpi', 'ic_launcher.png');
if (fs.existsSync(xxxhdpiSrc)) {
  fs.copyFileSync(xxxhdpiSrc, path.join(drawableDir, 'ic_launcher.png'));
  console.log('  ✓ drawable/ic_launcher.png');
}
console.log(`\nDone. ${copied} mipmap sizes copied.`);
