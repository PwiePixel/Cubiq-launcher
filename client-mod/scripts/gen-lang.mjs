#!/usr/bin/env node
// Erzeugt die Minecraft-Sprachdateien (Namen der Tastenbelegungen im Steuerungsmenü) aus den
// Übersetzungen des Cubiq Clients: common/src/main/resources/assets/cubiqclient/i18n/<code>.json.
//
//   node client-mod/scripts/gen-lang.mjs
//
// JSON (ab 1.13) für fabric, neoforge, forge, forge-mojmap-legacy, forge-1.13.2;
// .lang für legacy (1.8.9–1.12.2, klein geschrieben – der Build benennt sie bis 1.10.2 in xx_XX um)
// und legacy-1.7.10 (xx_XX.lang).
import { readFileSync, writeFileSync, readdirSync, rmSync, mkdirSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

const root = join(dirname(fileURLToPath(import.meta.url)), '..');
const i18nDir = join(root, 'common/src/main/resources/assets/cubiqclient/i18n');

/** Minecraft-Sprachcodes je Cubiq-Sprache (alle Varianten bekommen dieselbe Übersetzung). */
const LOCALES = {
  en: ['en_us', 'en_gb', 'en_ca', 'en_au', 'en_nz'],
  de: ['de_de', 'de_at', 'de_ch'],
  es: ['es_es', 'es_mx', 'es_ar', 'es_cl', 'es_ec', 'es_uy', 'es_ve'],
  fr: ['fr_fr', 'fr_ca'],
  pl: ['pl_pl'],
  'pt-BR': ['pt_br', 'pt_pt'],
  tr: ['tr_tr'],
  nl: ['nl_nl', 'nl_be'],
};

/** Minecraft-Schlüssel ← i18n-Schlüssel. */
const KEYS = [
  ['key.categories.cubiqclient', 'key.cubiqclient.category'],
  ['key.category.cubiqclient.main', 'key.cubiqclient.category'],
  ['key.cubiqclient.menu', 'key.cubiqclient.menu'],
  ['key.cubiqclient.zoom', 'key.cubiqclient.zoom'],
  ['key.cubiqclient.fullbright', 'key.cubiqclient.fullbright'],
  ['key.cubiqclient.gammaCycle', 'key.cubiqclient.gammaCycle'],
  ['key.cubiqclient.autoRefill', 'key.cubiqclient.autoRefill'],
  ['key.cubiqclient.freelook', 'key.cubiqclient.freelook'],
  ['key.cubiqclient.hudProfile', 'key.cubiqclient.hudProfile'],
  ['key.cubiqclient.emoteWheel', 'key.cubiqclient.emoteWheel'],
  ['key.cubiqclient.redstoneOverlay', 'key.cubiqclient.redstoneOverlay'],
  ['key.cubiqclient.saveClip', 'key.cubiqclient.saveClip'],
  ['key.cubiqclient.toggleRecording', 'key.cubiqclient.toggleRecording'],
  ['key.cubiqclient.wardrobe', 'key.cubiqclient.wardrobe'],
  ['key.cubiqclient.worldMap', 'key.cubiqclient.worldMap'],
  ['key.cubiqclient.social', 'key.cubiqclient.social'],
  ['key.cubiqclient.quickReply', 'key.cubiqclient.quickReply'],
  ['key.cubiqclient.panorama', 'key.cubiqclient.panorama'],
  ['key.cubiqclient.notes', 'key.cubiqclient.notes'],
  ['key.cubiqclient.cinematicAdd', 'key.cubiqclient.cinematicAdd'],
  ['key.cubiqclient.cinematicPlay', 'key.cubiqclient.cinematicPlay'],
  ['key.cubiqclient.cinematicRemove', 'key.cubiqclient.cinematicRemove'],
  ['key.cubiqclient.cinematicClear', 'key.cubiqclient.cinematicClear'],
  ['key.cubiqclient.cinematicFreecam', 'key.cubiqclient.cinematicFreecam'],
];

const load = (code) => JSON.parse(readFileSync(join(i18nDir, `${code}.json`), 'utf8'));
const en = load('en');

function entries(code) {
  const table = { ...en, ...load(code) };
  return KEYS.map(([mc, key]) => {
    const value = table[key];
    if (!value) throw new Error(`${code}: ${key} fehlt`);
    return [mc, value];
  });
}

function langDir(project) {
  const dir = join(root, project, 'src/main/resources/assets/cubiqclient/lang');
  mkdirSync(dir, { recursive: true });
  // Alte Dateien weg (auch umbenannte Varianten), dann neu schreiben.
  for (const f of readdirSync(dir)) rmSync(join(dir, f));
  return dir;
}

let count = 0;
for (const project of ['fabric', 'neoforge', 'forge', 'forge-mojmap-legacy', 'forge-1.13.2']) {
  const dir = langDir(project);
  for (const [code, locales] of Object.entries(LOCALES)) {
    const json = '{\n' + entries(code).map(([k, v]) => `\t${JSON.stringify(k)}: ${JSON.stringify(v)}`).join(',\n') + '\n}\n';
    for (const locale of locales) {
      writeFileSync(join(dir, `${locale}.json`), json, 'utf8');
      count++;
    }
  }
}

const lang = (code) => entries(code).map(([k, v]) => `${k}=${v}`).join('\n') + '\n';
const upper = (locale) => locale.slice(0, 3) + locale.slice(3).toUpperCase();
for (const [project, name] of [['legacy', (l) => l], ['legacy-1.7.10', upper]]) {
  const dir = langDir(project);
  for (const [code, locales] of Object.entries(LOCALES)) {
    for (const locale of locales) {
      writeFileSync(join(dir, `${name(locale)}.lang`), lang(code), 'utf8');
      count++;
    }
  }
}
console.log(`${count} Sprachdateien geschrieben`);
