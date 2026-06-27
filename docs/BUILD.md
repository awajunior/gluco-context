# Como compilar o Gluco Context localmente

## Pré-requisitos

| Ferramenta | Versão mínima |
|---|---|
| Node.js | 18 ou superior |
| Java (JDK) | 21 |
| Android SDK | API 33 ou superior |
| Android Studio | Qualquer versão recente (para o Gradle) |

Defina as variáveis de ambiente antes de compilar:

```
JAVA_HOME=<caminho do JDK 21>
ANDROID_HOME=<caminho do Android SDK>
```

---

## 1. Instalar dependências

```bash
npm install
```

---

## 2. Gerar o bundle web

```bash
npm run build
```

Isso copia `webroot/` → `dist/` sem transpilação.  
**Não edite arquivos dentro de `dist/` diretamente** — eles são sobrescritos a cada build.

---

## 3. Sincronizar com o Android

```bash
npx cap sync android
```

Isso copia `dist/` → `android/app/src/main/assets/public/`.

---

## 4. Compilar o APK de debug

```bash
cd android
./gradlew assembleDebug
```

O APK gerado estará em:
```
android/app/build/outputs/apk/debug/app-debug.apk
```

---

## 5. Compilar o APK de release (assinado)

Você precisará de um keystore próprio. Nunca use o keystore de outra pessoa.

Crie o arquivo `android/key.properties` com o seguinte conteúdo:

```
storeFile=/caminho/completo/para/seu.keystore
storePassword=SUA_SENHA
keyAlias=key0
keyPassword=SUA_SENHA_DA_CHAVE
```

Depois compile:

```bash
cd android
./gradlew assembleRelease
```

O APK assinado estará em:
```
android/app/build/outputs/apk/release/app-release.apk
```

**Remova `android/key.properties` após o build.** Esse arquivo nunca deve ser commitado.

---

## Estrutura relevante do projeto

```
webroot/          ← fonte controlada do bundle web
dist/             ← artefato gerado (não versionar)
reference/        ← versão legível da UI (não compilada diretamente)
android-plugin/   ← cópias de referência dos plugins Kotlin
android/          ← projeto Android (Capacitor)
scripts/          ← scripts de build auxiliares
```

---

## Sobre a interface

A UI é um bundle React pré-compilado em `webroot/assets/index-*.js`.  
A versão legível está em `reference/main.readable.jsx`.  
Patches na UI devem ser aplicados cirurgicamente em `webroot/` — não recompile o bundle a partir do `reference/` sem validação completa.
