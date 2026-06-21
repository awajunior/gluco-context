const fs = require('fs');
const path = require('path');
const root = path.join(__dirname, '..');
const manifestPath = path.join(root, 'android', 'app', 'src', 'main', 'AndroidManifest.xml');
if (!fs.existsSync(manifestPath)) {
  console.error('AndroidManifest.xml não encontrado. Rode primeiro: npx cap add android');
  process.exit(1);
}
let xml = fs.readFileSync(manifestPath, 'utf8');
const perms = ['android.permission.INTERNET','android.permission.POST_NOTIFICATIONS','android.permission.SCHEDULE_EXACT_ALARM','android.permission.VIBRATE','android.permission.WAKE_LOCK','android.permission.RECEIVE_BOOT_COMPLETED'];
for (const perm of perms) {
  if (!xml.includes(perm)) {
    xml = xml.replace(/<manifest([^>]*)>/, `<manifest$1>\n    <uses-permission android:name="${perm}" />`);
  }
}
const pkgs = [
  'info.nightscout.androidaps',
  'info.nightscout.aaps',
  'app.aaps',
  'org.nightscout.androidaps'
];
if (!xml.includes('<queries>')) {
  const queries = ['    <queries>', ...pkgs.map(p => `        <package android:name="${p}" />`), '    </queries>'].join('\n');
  xml = xml.replace(/\s*<application/, `\n${queries}\n\n    <application`);
} else {
  for (const p of pkgs) {
    if (!xml.includes(`android:name="${p}"`)) {
      xml = xml.replace('</queries>', `        <package android:name="${p}" />\n    </queries>`);
    }
  }
}
// Apply approved launcher icon. This uses a drawable PNG so it works without extra asset tooling.
const srcIcon = path.join(root, 'src', 'assets', 'aaps-assist-icon.png');
const resDir = path.join(root, 'android', 'app', 'src', 'main', 'res', 'drawable');
const iconName = 'aaps_assist_icon.png';
if (fs.existsSync(srcIcon)) {
  fs.mkdirSync(resDir, { recursive: true });
  fs.copyFileSync(srcIcon, path.join(resDir, iconName));
  xml = xml.replace(/android:icon="@[^"]+"/g, 'android:icon="@drawable/aaps_assist_icon"');
  xml = xml.replace(/android:roundIcon="@[^"]+"/g, 'android:roundIcon="@drawable/aaps_assist_icon"');
  if (!/android:icon="@drawable\/aaps_assist_icon"/.test(xml)) {
    xml = xml.replace(/<application([^>]*)>/, '<application$1 android:icon="@drawable/aaps_assist_icon">');
  }
}

// v1.13.0-beta26.5: aceitar prints compartilhados pelo Android (image/*).
const shareFilter = `
            <intent-filter>
                <action android:name="android.intent.action.SEND" />
                <category android:name="android.intent.category.DEFAULT" />
                <data android:mimeType="image/*" />
            </intent-filter>`;
if (!xml.includes('android.intent.action.SEND')) {
  xml = xml.replace(/(\s*<\/activity>)/, `${shareFilter}
$1`);
}

// Add background sync receiver if not present
const receiverDecl = `
        <receiver
            android:name="com.glucocontext.app.AapsBackgroundReceiver"
            android:enabled="true"
            android:exported="false">
            <intent-filter>
                <action android:name="com.glucocontext.app.BACKGROUND_SYNC" />
                <action android:name="android.intent.action.BOOT_COMPLETED" />
            </intent-filter>
        </receiver>`;
if (!xml.includes('AapsBackgroundReceiver')) {
  xml = xml.replace('</application>', receiverDecl + '\n    </application>');
}
const followUpContextReceiverDecl = `
        <receiver
            android:name="com.glucocontext.app.AapsFollowUpContextReceiver"
            android:enabled="true"
            android:exported="false">
            <intent-filter>
                <action android:name="com.glucocontext.app.FOLLOWUP_CONTEXT_ALARM" />
            </intent-filter>
        </receiver>`;
if (!xml.includes('AapsFollowUpContextReceiver')) {
  xml = xml.replace('</application>', followUpContextReceiverDecl + '\n    </application>');
}


// v1.13.0-beta28.6: ocultar/remover Cover Widget da lista. A tela externa Z Flip5 não é suportada nesta fase.
xml = xml.replace(new RegExp('\n\s*<receiver\s+android:name="br\\.com\\.aapsassist\\.app\\.AapsCoverWidgetProvider|com\\.glucocontext\\.app\\.AapsCoverWidgetProvider"[\s\S]*?<\/receiver>\s*\n', 'g'), '\n');

// v1.13.0-beta28: widget inteligente somente leitura.
const widgetReceiverDecl = `
        <receiver
            android:name="com.glucocontext.app.AapsSmartWidgetProvider"
            android:enabled="true"
            android:exported="true">
            <intent-filter>
                <action android:name="android.appwidget.action.APPWIDGET_UPDATE" />
                <action android:name="com.glucocontext.app.WIDGET_SYNC" />
                <action android:name="com.glucocontext.app.WIDGET_OPEN" />
            </intent-filter>
            <meta-data
                android:name="android.appwidget.provider"
                android:resource="@xml/aaps_smart_widget_info" />
        </receiver>`;
if (!xml.includes('AapsSmartWidgetProvider')) {
  xml = xml.replace('</application>', widgetReceiverDecl + '\n    </application>');
}

// Garantir registro do plugin do widget no MainActivity após sync/patch.
const mainActivityPath = path.join(root, 'android', 'app', 'src', 'main', 'java', 'com', 'glucocontext', 'app', 'MainActivity.java');
if (fs.existsSync(mainActivityPath)) {
  let mainActivity = fs.readFileSync(mainActivityPath, 'utf8');
  if (!mainActivity.includes('AapsSmartWidgetPlugin.class')) {
    mainActivity = mainActivity.replace('registerPlugin(AapsOcrPlugin.class);', 'registerPlugin(AapsOcrPlugin.class);\n        registerPlugin(AapsSmartWidgetPlugin.class);');
    fs.writeFileSync(mainActivityPath, mainActivity);
  }
}

fs.writeFileSync(manifestPath, xml);
console.log('AndroidManifest.xml atualizado: permissões + queries AAPS + ícone launcher + receiver Follow-up contextual + WorkManager.');

// v1.11.4.18: instalar WorkManager experimental de Alertas.
require('./install-monitor-worker.cjs');
