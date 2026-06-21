export type MonitorSeverity = 'info' | 'attention' | 'urgent-context';
export type MonitorConfidence = 'low' | 'moderate' | 'high';

export type GlucoseEntry = {
  sgv?: number | string | null;
  date?: number | string | null;
  dateString?: string | null;
};

export type PredictiveMonitorContext = {
  bg: number | null;
  trendSpeed: number | null;
  prediction30: number | null;
  iob: number | null;
  cob: number | null;
  lastBolusMin: number | null;
  recentBolusMax20Min?: number | null;
  recentBolusSum20Min?: number | null;
  recentBolusCount20Min?: number | null;
  lastMealMin: number | null;
  target: number;
  bgEntries: GlucoseEntry[];
  lastSyncAt?: string | null;
  lastSyncOk?: boolean;
  clinicalValid?: boolean;
  siteAgeHours?: number | null;
  sensorAgeDays?: number | null;
};

export type PredictiveAlert = {
  id: string;
  severity: MonitorSeverity;
  title: string;
  summary: string;
  reason: string;
  shouldNotify: boolean;
  suppressReason: string | null;
  confidence: MonitorConfidence;
  metrics: Record<string, string | number | null>;
};

function finite(value: any): number | null {
  const n = Number(String(value ?? '').replace(',', '.'));
  return Number.isFinite(n) ? n : null;
}

function entryTimeMs(entry: GlucoseEntry): number | null {
  const dateNum = finite(entry?.date);
  if (dateNum !== null && dateNum > 0) return dateNum;
  const parsed = Date.parse(String(entry?.dateString ?? ''));
  return Number.isFinite(parsed) ? parsed : null;
}

function sortedEntries(entries: GlucoseEntry[]) {
  return (entries ?? [])
    .map((entry) => ({ entry, time: entryTimeMs(entry), bg: finite(entry?.sgv) }))
    .filter((item) => item.time !== null && item.bg !== null)
    .sort((a, b) => Number(b.time) - Number(a.time));
}

export function estimateDelta(entries: GlucoseEntry[], minutes: number): number | null {
  const sorted = sortedEntries(entries);
  if (sorted.length < 2) return null;
  const latest = sorted[0];
  const targetMs = Number(latest.time) - minutes * 60_000;
  let best = sorted[1];
  let bestDistance = Math.abs(Number(best.time) - targetMs);
  for (const item of sorted.slice(1)) {
    const distance = Math.abs(Number(item.time) - targetMs);
    if (distance < bestDistance) {
      best = item;
      bestDistance = distance;
    }
  }
  return Number(latest.bg) - Number(best.bg);
}

function plateauStats(entries: GlucoseEntry[], windowMin = 30) {
  const sorted = sortedEntries(entries);
  if (sorted.length < 1) return { count: 0, avg: null as number | null, max: null as number | null, dropFromMax: null as number | null };
  const latest = sorted[0];
  const minMs = Number(latest.time) - windowMin * 60_000;
  const recent = sorted.filter((item) => Number(item.time) >= minMs && Number(item.time) <= Number(latest.time));
  if (recent.length < 1) return { count: 0, avg: null as number | null, max: null as number | null, dropFromMax: null as number | null };
  const values = recent.map((item) => Number(item.bg));
  const avg = values.reduce((sum, v) => sum + v, 0) / values.length;
  const max = Math.max(...values);
  return {
    count: recent.length,
    avg: Math.round(avg * 10) / 10,
    max,
    dropFromMax: Math.round((max - Number(latest.bg)) * 10) / 10,
  };
}

function syncAgeMinutes(value?: string | null): number | null {
  if (!value) return null;
  const ms = Date.parse(value);
  if (!Number.isFinite(ms)) return null;
  return Math.max(0, Math.round((Date.now() - ms) / 60_000));
}

function baseSuppression(ctx: PredictiveMonitorContext): string | null {
  if (!ctx.lastSyncOk || ctx.clinicalValid === false) return 'sincronização clínica inválida ou parcial';
  const age = syncAgeMinutes(ctx.lastSyncAt);
  if (age !== null && age > 20) return `dados antigos (${age} min)`;
  if (ctx.bg === null) return 'BG ausente';
  if (ctx.trendSpeed === null) return 'tendência ausente';
  return null;
}

function makeAlert(alert: Omit<PredictiveAlert, 'shouldNotify'>, suppressedBy: string | null): PredictiveAlert {
  const localSuppression = alert.suppressReason ?? suppressedBy;
  return {
    ...alert,
    suppressReason: localSuppression,
    shouldNotify: !localSuppression && alert.severity !== 'info',
  };
}

export function evaluatePredictiveRules(ctx: PredictiveMonitorContext): PredictiveAlert[] {
  const alerts: PredictiveAlert[] = [];
  const suppressedBy = baseSuppression(ctx);
  const bg = ctx.bg;
  const trend = ctx.trendSpeed;
  const iob = ctx.iob ?? 0;
  const cob = ctx.cob ?? 0;
  const prediction30 = ctx.prediction30;
  const lastBolusMin = ctx.lastBolusMin;
  const recentBolusMax20Min = ctx.recentBolusMax20Min ?? null;
  const recentBolusSum20Min = ctx.recentBolusSum20Min ?? null;
  const recentBolusCount20Min = ctx.recentBolusCount20Min ?? null;
  const relevantBolusRecent = (recentBolusMax20Min !== null && recentBolusMax20Min >= 0.30) || (recentBolusSum20Min !== null && recentBolusSum20Min >= 0.50);
  const lastMealMin = ctx.lastMealMin;
  const mealActiveWindow = lastMealMin !== null && lastMealMin < 120;
  const mealSequentialWindow = lastMealMin !== null && lastMealMin >= 120 && lastMealMin <= 240;
  const mealRelevantByTime = mealActiveWindow || mealSequentialWindow;
  const mealResidualEvidence = mealSequentialWindow && (cob >= 5 || iob >= 0.7);
  const delta15 = estimateDelta(ctx.bgEntries, 15);
  const delta40 = estimateDelta(ctx.bgEntries, 40);
  const target = Number.isFinite(ctx.target) ? ctx.target : 100;
  const siteAgeHours = ctx.siteAgeHours ?? null;
  const sensorAgeDays = ctx.sensorAgeDays ?? null;

  if (bg !== null && trend !== null) {
    const dropRisk = bg <= 110 && trend <= -1.5 && (delta15 === null || delta15 <= -12) && iob >= 0.7 && cob <= 8;
    if (dropRisk) {
      alerts.push(makeAlert({
        id: 'drop-risk-iob',
        severity: bg < 90 || trend <= -2.5 ? 'urgent-context' : 'attention',
        title: 'Queda rápida com IOB ativo',
        summary: 'Possível risco de queda nos próximos 30–45 min.',
        reason: 'BG próximo do alvo/baixo, tendência de queda, IOB relevante e COB baixo ou ausente.',
        suppressReason: null,
        confidence: delta15 !== null ? 'high' : 'moderate',
        metrics: { bg, trend, delta15, iob, cob, prediction30 },
      }, suppressedBy));
    }

    const rapidRisePostMeal = bg >= 130 && trend >= 2 && (delta15 === null || delta15 >= 20) && mealActiveWindow;
    if (rapidRisePostMeal) {
      alerts.push(makeAlert({
        id: 'rapid-rise-post-meal',
        severity: 'attention',
        title: 'Subida rápida pós-refeição',
        summary: 'Refeição <2h com BG subindo forte. Abrir o app para revisar contexto.',
        reason: 'Critério: refeição ativa, BG ≥130 mg/dL e tendência ≥+2,0 mg/dL/min; alerta apenas contextual, sem sugestão de bolus.',
        suppressReason: null,
        confidence: delta15 !== null ? 'high' : 'moderate',
        metrics: { bg, trend, delta15, iob, cob, lastMealMin, mealWindow: '<2h ativa' },
      }, suppressedBy));
    }

    const windowTwoToFourHours = bg >= 140 && trend >= 0.5 && mealSequentialWindow;
    if (windowTwoToFourHours) {
      alerts.push(makeAlert({
        id: 'meal-window-2-4h-review',
        severity: 'attention',
        title: 'Janela 2–4h: avaliar',
        summary: 'Refeição recente, BG acima do alvo e ainda subindo. Revisar contexto no app.',
        reason: 'Critério: refeição entre 2–4h, BG ≥140 mg/dL e tendência ≥+0,5 mg/dL/min; pode indicar absorção tardia/sequencial.',
        suppressReason: null,
        confidence: 'moderate',
        metrics: { bg, trend, delta15, delta40, iob, cob, lastMealMin, mealWindow: '2–4h avaliar' },
      }, suppressedBy));
    }

    const reviewCorrectionContext = bg >= target + 50 && trend >= 1 && iob < 0.3 && cob <= 5 && (lastBolusMin === null || lastBolusMin >= 90 || !relevantBolusRecent) && !mealActiveWindow;
    if (reviewCorrectionContext) {
      alerts.push(makeAlert({
        id: 'review-correction-context',
        severity: 'attention',
        title: 'Revisar contexto de correção no AAPS',
        summary: 'BG alto/subindo com pouca insulina ativa.',
        reason: 'Pode valer abrir o AAPS e revisar se há correção pendente; este app não recomenda nem executa bolus.',
        suppressReason: null,
        confidence: 'moderate',
        metrics: { bg, trend, iob, cob, lastBolusMin, prediction30 },
      }, suppressedBy));
    }
  }

  const siteOrSensorAttention = (siteAgeHours !== null && siteAgeHours > 72) || (sensorAgeDays !== null && sensorAgeDays > 10);
  if (siteOrSensorAttention) {
    const reasons = [
      siteAgeHours !== null && siteAgeHours > 72 ? `site ${Math.round(siteAgeHours)}h` : null,
      sensorAgeDays !== null && sensorAgeDays > 10 ? `sensor ${Math.round(sensorAgeDays * 10) / 10}d` : null,
    ].filter(Boolean).join(' · ');
    alerts.push(makeAlert({
      id: 'sensor-site-attention',
      severity: 'attention',
      title: 'Atenção sensor/site',
      summary: 'Sensor ou local de aplicação em faixa que reduz confiança. Revisar antes de decisões automáticas.',
      reason: `Critério: ${reasons}. Alerta raro, sem recomendação terapêutica.`,
      suppressReason: null,
      confidence: 'moderate',
      metrics: { bg, trend, siteAgeHours, sensorAgeDays, prediction30 },
    }, null));
  }

  if (!alerts.length) {
    alerts.push({
      id: 'no-context-alert',
      severity: 'info',
      title: 'Sem alerta contextual ativo',
      summary: 'Nenhuma regra preditiva foi acionada com os dados atuais.',
      reason: suppressedBy ? `A avaliação está limitada por: ${suppressedBy}.` : 'BG, tendência, IOB, COB e predição não acionaram critérios de atenção.',
      shouldNotify: false,
      suppressReason: suppressedBy,
      confidence: suppressedBy ? 'low' : 'moderate',
      metrics: { bg, trend, delta15, delta40, iob, cob, prediction30, lastBolusMin, lastMealMin, siteAgeHours, sensorAgeDays, mealWindow: lastMealMin === null ? 'desconhecida' : lastMealMin < 120 ? '<2h ativa' : lastMealMin <= 240 ? '2–4h avaliar' : '>4h não assumir' },
    });
  }

  return alerts;
}
