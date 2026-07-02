# Usando o Gluco Context

## Para quem é este app

O Gluco Context foi desenvolvido para usuários experientes de AAPS que já usam Nightscout e querem raciocinar sobre o timing do bolus com o apoio de um assistente de IA (ChatGPT, Claude ou similar).

Não é uma ferramenta para iniciantes. Pressupõe que você entende seu próprio manejo do diabetes, suas configurações do AAPS e como interpretar dados glicêmicos.

---

## O fluxo principal

```
Descreva sua refeição
       ↓
Toque em "Preparar para IA"
       ↓
O app sincroniza o Nightscout, monta um snapshot estruturado, copia e abre sua IA
       ↓
Cole no chat da IA — revise a análise
       ↓
Decida: Sem bolus agora / Copiar dose final e abrir AAPS
       ↓
Registre sua decisão (opcional: defina um lembrete de reavaliação)
       ↓
Se reavaliação definida: receba o lembrete → abra o app → repita
```

---

## Passo a passo

### 1. Configuração inicial

Vá em **Conexões** e configure:

- **URL do Nightscout** — o endereço da sua instância Nightscout
- **Token do Nightscout** — seu token de acesso de leitura, se exigido pela sua configuração do Nightscout (opcional para instâncias públicas)
- **Destino da IA** — a URL ou app que você usa para IA (ex: ChatGPT, Claude)
- **Pacote do AAPS** — o nome do pacote do seu app AAPS (ex: `info.nightscout.androidaps`)

Sua URL do Nightscout e o token são armazenados localmente no seu dispositivo e nunca saem dele.

Confirme que o perfil ativo foi importado corretamente antes de continuar.

### 2. Descreva sua refeição

Vá na aba **Refeição** e descreva o que você vai comer ou acabou de comer — alimentos, carboidratos estimados e qualquer observação relevante (ex: alto teor de gordura, absorção lenta esperada).

### 3. Toque em "Preparar para IA"

Na aba **Início**, toque em **Preparar para IA**.

O app vai:
- Sincronizar seus dados do Nightscout
- Montar um snapshot estruturado com seu BG atual, tendência, IOB, FSI e IC ativos, idade do sensor, idade do sítio, descrição da refeição e contexto recente
- Copiar o snapshot para a área de transferência
- Abrir o destino de IA configurado

### 4. Cole na IA e revise a análise

Cole o snapshot no chat da sua IA. A IA vai raciocinar sobre o contexto atual e fornecer uma análise.

**Exemplo de snapshot (sessão real):**

> Snapshot 13:32 — Análise de Contexto | ~44 min pós-almoço
>
> BG: 99 mg/dL | Tendência: +0,7 mg/dL/min (estável) | Sequência: 96→97→98→98→99
> Predição 30 min: 103 mg/dL | IOB: 1,282 U | Último SMB: 132 min atrás
> FSI ativa (11:00): 75 mg/dL/U | IC: 1:9
> Sensor: Dia 10,4 ⚠️ | Sítio: 72,4 h ⚠️
> Refeição: salmão + arroz/feijão/carne/salada, ~70–85 g CHO, ~44 min atrás

**Exemplo de análise da IA para o snapshot acima:**

> IOB 1,28 U × FSI 75 = ~96 mg/dL de cobertura potencial. Absorção de arroz/feijão/carne/salada ainda em curso — janela de pico estimada 13:20–14:30 (60–90 min pós-refeição). Sem bolus agora. Se BG subir acima de 150–160 mg/dL com tendência mantida → reavaliar. Se BG cair abaixo de 85 mg/dL com tendência negativa → considerar CHO de resgate preventivo.
>
> Sensor Dia 10,4 + sítio 72,4 h ativam Combined Risk Rule — confiança reduzida nas próximas 2h. Troca de sítio recomendada hoje.
>
> Próximo snapshot: 30–45 min (janela do pico esperado de arroz/feijão).
>
> Nível de confiança: Reduzido. Decisão final pertence ao usuário.

### 5. Registre sua decisão

De volta ao app, registre sua decisão:

- **Sem bolus agora** — registra a decisão e agenda o lembrete de reavaliação se um foi definido
- **Copiar dose final e abrir AAPS** — copia a dose para a área de transferência e abre o AAPS para entrada manual

Registrar a decisão é o que ativa o lembrete de reavaliação.

### 6. Reavaliação (opcional)

Se você definiu um lembrete de reavaliação (15, 30, 45, 60, 90 ou 120 min), receberá uma notificação no momento certo.

Abra o app, toque em **Preparar para IA** novamente e repita o processo com dados atualizados.

---

## O protocolo da IA

Na primeira vez que usar o Gluco Context com uma IA, carregue o protocolo completo:

1. Vá em **Início** → toque em **Copiar Protocolo completo para IA**
2. Cole em um chat ou projeto dedicado na sua IA
3. A IA vai confirmar: *"Configuração carregada: Protocolo: Gluco Context v2.x. Pronto para receber: snapshot do app, foto do prato ou print do AAPS."*

Depois disso, basta colar o snapshot a cada refeição — o protocolo fica carregado no mesmo chat ou projeto.

**Padrão Pessoal (opcional):** Se você tem padrões recorrentes (ex: spike tardio com pizza, absorção prolongada com alto teor de gordura/proteína), vá em **Início** → toque em **Copiar Padrão Pessoal para IA** e cole uma vez no mesmo chat. A IA vai considerá-lo nas análises seguintes.

---

## O que o app não faz

- Não calcula bolus
- Não envia comandos para o AAPS
- Não toma decisões — organiza contexto para você raciocinar com a IA
- Não substitui seu julgamento clínico nem sua equipe de saúde

A decisão final é sempre sua.

---

## Dicas para usuários experientes

- Use um **chat ou projeto dedicado** na sua IA para o Gluco Context — assim o protocolo e o padrão pessoal ficam carregados sem precisar colar novamente a cada sessão.
- O snapshot sempre inclui a **FSI e IC ativas para o horário atual** — não é necessário consultá-las manualmente.
- **Idade do sensor e do sítio** estão em todo snapshot. A IA vai sinalizar a Combined Risk Rule se ambos ultrapassarem os limites seguros simultaneamente.
- Se a IA pedir esclarecimentos, responda no mesmo chat — o contexto do protocolo é preservado.
- Após algumas sessões, o raciocínio da IA tende a ficar mais consistente conforme ela incorpora seus padrões pessoais.
