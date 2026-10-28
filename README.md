<p align="center">
  <img src="docs/logo.png" alt="Money" width="160" />
</p>

<h1 align="center">Money</h1>

<p align="center">
  Spese ed entrate, senza fatica e con un po' di colore.<br/>
  App Android nativa, tutta in Kotlin e Jetpack Compose.
</p>

<p align="center">
  <a href="https://github.com/carellice/money/releases/latest"><img alt="Ultima versione" src="https://img.shields.io/github/v/release/carellice/money?style=for-the-badge&color=B8A6FF&labelColor=15121F" /></a>
  <img alt="Android 11+" src="https://img.shields.io/badge/Android-11%2B-8FE3C0?style=for-the-badge&labelColor=15121F" />
  <img alt="Kotlin" src="https://img.shields.io/badge/Kotlin-Compose-FF9EC4?style=for-the-badge&labelColor=15121F" />
</p>

---

## ✨ Cosa fa

| | |
|---|---|
| 🫧 **Inserimento in 3 tocchi** | Importo sul tastierino, categoria e sottocategoria, poi data, metodo di pagamento, nota e foto dello scontrino. |
| 🔁 **Spese fisse e abbonamenti** | Netflix, Spotify, affitto, palestra: logo preimpostato o immagine tua, registrazione automatica alla scadenza e promemoria prima del rinnovo. |
| 📊 **Report del mese** | Pagella da A a F, grafico per categoria, calendario delle spese, trend di 6 mesi, confronto col mese prima, budget. Esporta in PDF o come immagine. |
| 🤖 **Assistente AI** | Con Cloudflare Workers AI: commento del mese con consigli, chat sulle tue spese, categoria automatica dalla descrizione, resoconto di fine mese. |
| 🎨 **Tuo, davvero** | Tema chiaro o scuro, colore principale a scelta, blocco con impronta, backup in JSON. |
| ⬆️ **Si aggiorna da sola** | L'app controlla le release di questo repository e installa la nuova versione con un tocco. |

## 📲 Installazione

1. Scarica `Money-x.y.z.apk` dall'[ultima release](https://github.com/carellice/money/releases/latest).
2. Aprilo sul telefono e consenti l'installazione da questa fonte.
3. Dalla volta successiva gli aggiornamenti arrivano dentro l'app: **Impostazioni → Aggiornamenti**.

## 🤖 Collegare l'AI

**Impostazioni → Assistente AI**, poi inserisci:
- l'**Account ID** Cloudflare (dalla dashboard);
- un **API token** con permesso *Workers AI* ([crea token](https://dash.cloudflare.com/profile/api-tokens)).

Il token resta cifrato sul telefono (Android Keystore) e non finisce nei backup. All'AI vengono inviati totali e movimenti (data, importo, categoria, nota, metodo), mai le foto.

## 🛠️ Sviluppo

Requisiti: JDK 17+, Android SDK 36.

```bash
./gradlew assembleDebug
```

### Rilasciare una nuova versione

```bat
release.bat            :: 1.2.3 -> 1.2.4
release.bat minor      :: 1.2.3 -> 1.3.0
release.bat major      :: 1.2.3 -> 2.0.0
```

Lo script aumenta versione e `versionCode` in `version.properties`, compila l'APK firmato, crea commit e tag, fa il push e pubblica la release su GitHub con l'APK allegato. Serve [GitHub CLI](https://cli.github.com) autenticata.

La chiave di firma sta in `signing/` ed è esclusa da git: **conservane una copia**. Senza la stessa chiave gli aggiornamenti dall'app non si installano.

### Struttura

```
app/src/main/java/com/money/app/
├── data/     Room, DataStore, backup
├── domain/   formattazione, ricorrenze, report e pagella
├── ai/       client Cloudflare Workers AI e prompt
├── update/   controllo e installazione aggiornamenti
├── work/     registrazione automatica e notifiche
└── ui/       schermate Compose e tema a blob animati
```

## 📄 Crediti

- Font [Bricolage Grotesque](https://github.com/ateliertriay/bricolage) (SIL OFL).
- Loghi dei servizi da [Simple Icons](https://simpleicons.org) (CC0). I marchi appartengono ai rispettivi proprietari.
