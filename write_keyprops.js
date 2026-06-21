const fs = require('fs');

// 1. Escrever key.properties com path normalizado
const keystorePath = process.argv[2].replace(/\\/g, '/');
const storePass   = process.argv[3];
const keyPass     = process.argv[4];
const content = [
  'storeFile=' + keystorePath,
  'storePassword=' + storePass,
  'keyAlias=key0',
  'keyPassword=' + keyPass,
].join('\n') + '\n';
fs.writeFileSync('android/key.properties', content, 'utf8');
console.log('OK: key.properties criado');

// 2. Garantir nomes corretos no strings.xml (pode ser sobrescrito pelo cap sync)
const stringsPath = 'android/app/src/main/res/values/strings.xml';
let xml = fs.readFileSync(stringsPath, 'utf8');
xml = xml.replace(/<string name="app_name">[^<]*<\/string>/,
                  '<string name="app_name">Gluco Context</string>');
xml = xml.replace(/<string name="title_activity_main">[^<]*<\/string>/,
                  '<string name="title_activity_main">Gluco Context</string>');
xml = xml.replace(/<string name="aaps_smart_widget_description">[^<]*<\/string>/,
                  '<string name="aaps_smart_widget_description">Gluco Context</string>');
fs.writeFileSync(stringsPath, xml, 'utf8');
console.log('OK: strings.xml corrigido');
