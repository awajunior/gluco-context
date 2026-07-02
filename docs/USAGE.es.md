# Cómo usar Gluco Context

## Para quién es esta app

Gluco Context está diseñado para usuarios experimentados de AAPS que ya usan Nightscout y quieren razonar sobre el timing del bolo con la ayuda de un asistente de IA (ChatGPT, Claude u otro similar).

No es una herramienta para principiantes. Supone que entendés tu propio manejo de la diabetes, tu configuración de AAPS y cómo interpretar datos glucémicos.

---

## El flujo principal

```
Describí tu comida
       ↓
Tocá "Preparar para IA"
       ↓
La app sincroniza Nightscout, arma un snapshot estructurado, lo copia y abre tu IA
       ↓
Pegá en el chat de la IA — revisá el análisis
       ↓
Decidí: Sin bolo ahora / Copiar dosis final y abrir AAPS
       ↓
Registrá tu decisión (opcional: configurá un recordatorio de reevaluación)
       ↓
Si configuraste reevaluación: recibís el recordatorio → abrís la app → repetís
```

---

## Paso a paso

### 1. Configuración inicial

Ir a **Conexiones** y configurar:

- **URL de Nightscout** — la dirección de tu instancia de Nightscout
- **Token de Nightscout** — tu token de acceso de lectura, si es requerido por tu configuración de Nightscout (opcional para instancias públicas)
- **Destino de IA** — la URL o app que usás para IA (ej: ChatGPT, Claude)
- **Paquete de AAPS** — el nombre del paquete de tu app AAPS (ej: `info.nightscout.androidaps`)

Tu URL de Nightscout y el token se almacenan localmente en tu dispositivo y nunca lo abandonan.

Confirmá que el perfil activo fue importado correctamente antes de continuar.

### 2. Describí tu comida

Ir a la pestaña **Comida** y describir lo que vas a comer o acabás de comer — alimentos, carbohidratos estimados y cualquier observación relevante (ej: alto contenido de grasa, absorción lenta esperada).

### 3. Tocá "Preparar para IA"

En la pestaña **Inicio**, tocá **Preparar para IA**.

La app va a:
- Sincronizar tus datos de Nightscout
- Armar un snapshot estructurado con tu BG actual, tendencia, IOB, FSI e IC activas, edad del sensor, edad del sitio, descripción de la comida y contexto reciente
- Copiar el snapshot al portapapeles
- Abrir el destino de IA configurado

### 4. Pegá en la IA y revisá el análisis

Pegá el snapshot en el chat de tu IA. La IA va a razonar sobre el contexto actual y proporcionar un análisis.

**Ejemplo de snapshot (sesión real):**

> Snapshot 13:32 — Análisis de Contexto | ~44 min post-almuerzo
>
> BG: 99 mg/dL | Tendencia: +0,7 mg/dL/min (estable) | Secuencia: 96→97→98→98→99
> Predicción 30 min: 103 mg/dL | IOB: 1,282 U | Último SMB: 132 min atrás
> FSI activa (11:00): 75 mg/dL/U | IC: 1:9
> Sensor: Día 10,4 ⚠️ | Sitio: 72,4 h ⚠️
> Comida: salmón + arroz/porotos/carne/ensalada, ~70–85 g CHO, ~44 min atrás

**Ejemplo de análisis de la IA para el snapshot anterior:**

> IOB 1,28 U × FSI 75 = ~96 mg/dL de cobertura potencial. Absorción de arroz/porotos/carne/ensalada aún activa — ventana de pico estimada 13:20–14:30 (60–90 min post-comida). Sin bolo ahora. Si BG sube por encima de 150–160 mg/dL con tendencia sostenida → reevaluar. Si BG cae por debajo de 85 mg/dL con tendencia negativa → considerar CHO de rescate preventivo.
>
> Sensor Día 10,4 + sitio 72,4 h activan Combined Risk Rule — confianza reducida en las próximas 2h. Cambio de sitio recomendado hoy.
>
> Próximo snapshot: 30–45 min (ventana de pico esperado de arroz/porotos).
>
> Nivel de confianza: Reducido. La decisión final pertenece al usuario.

### 5. Registrá tu decisión

De vuelta en la app, registrá tu decisión:

- **Sin bolo ahora** — registra la decisión y programa el recordatorio de reevaluación si fue configurado
- **Copiar dosis final y abrir AAPS** — copia la dosis al portapapeles y abre AAPS para entrada manual

Registrar la decisión es lo que activa el recordatorio de reevaluación.

### 6. Reevaluación (opcional)

Si configuraste un recordatorio de reevaluación (15, 30, 45, 60, 90 o 120 min), vas a recibir una notificación cuando llegue el momento.

Abrí la app, tocá **Preparar para IA** nuevamente y repetí el proceso con datos actualizados.

---

## El protocolo de IA

La primera vez que uses Gluco Context con una IA, cargá el protocolo completo:

1. Ir a **Inicio** → tocar **Copiar Protocolo completo para IA**
2. Pegarlo en un chat o proyecto dedicado en tu IA
3. La IA va a confirmar: *"Configuración cargada: Protocolo: Gluco Context v2.x. Listo para recibir: snapshot de la app, foto del plato o captura de AAPS."*

Después de eso, solo necesitás pegar el snapshot en cada comida — el protocolo queda cargado en el mismo chat o proyecto.

**Patrón Personal (opcional):** Si tenés patrones recurrentes (ej: spike tardío con pizza, absorción prolongada con alto contenido de grasa/proteína), ir a **Inicio** → tocar **Copiar Patrón Personal para IA** y pegarlo una vez en el mismo chat. La IA lo va a considerar en los análisis siguientes.

---

## Lo que la app no hace

- No calcula bolos
- No envía comandos a AAPS
- No toma decisiones — organiza contexto para que vos razonés con la IA
- No reemplaza tu criterio clínico ni a tu equipo de salud

La decisión final siempre es tuya.

---

## Consejos para usuarios experimentados

- Usá un **chat o proyecto dedicado** en tu IA para Gluco Context — así el protocolo y el patrón personal quedan cargados sin necesidad de pegarlos de nuevo en cada sesión.
- El snapshot siempre incluye la **FSI e IC activas para el horario actual** — no es necesario consultarlas manualmente.
- **Edad del sensor y del sitio** están en cada snapshot. La IA va a señalar la Combined Risk Rule si ambos superan los límites seguros simultáneamente.
- Si la IA pide aclaraciones, respondé en el mismo chat — el contexto del protocolo se preserva.
- Después de algunas sesiones, el razonamiento de la IA tiende a volverse más consistente a medida que incorpora tus patrones personales.
