# Como instalar o Gluco Context (sideload)

O Gluco Context é distribuído como APK para instalação direta (sideload).  
Não está disponível na Google Play Store.

---

## Antes de instalar

- Faça backup do seu celular ou dos dados do app anterior, se houver versão instalada.
- O APK é assinado pelo desenvolvedor. Verifique o hash SHA-256 antes de instalar (disponível nas notas de cada Release).

---

## Passo a passo

### 1. Baixar o APK

Na página de [Releases](../../releases) deste repositório, baixe o arquivo `.apk` da versão mais recente.

### 2. Verificar o hash (recomendado)

No computador, antes de transferir para o celular:

**Windows:**
```
certutil -hashfile GlucoContext_x_xx_RCx_xx_release.apk SHA256
```

**Linux / macOS:**
```bash
sha256sum GlucoContext_x_xx_RCx_xx_release.apk
```

Compare o resultado com o hash publicado nas notas de release.

### 3. Transferir para o celular

Copie o APK para o celular via cabo USB, Google Drive, ou outro meio de sua preferência.

### 4. Permitir instalação de fontes desconhecidas

No Android:  
**Configurações → Segurança → Instalar apps desconhecidos**  
Habilite para o app que você usará para abrir o APK (geralmente o gerenciador de arquivos).

### 5. Instalar

Abra o APK no celular e confirme a instalação.

---

## Atualização

Ao instalar uma nova versão sobre uma existente, seus dados locais são preservados.  
Mesmo assim, **faça backup antes de atualizar**.

---

## Desinstalação

Desinstale normalmente pelo Android. Os dados locais do app serão removidos junto.

---

## Requisitos

- Android 8.0 (API 26) ou superior
- Integração com AAPS via Nightscout (URL e token configurados pelo próprio usuário dentro do app)
