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
| 🫧 **Inserimento in 3 tocchi** | Importo sul tastierino in stile POS, con + − × ÷ per fare i conti mentre scrivi (11,00 × 2 = 22,00). Poi categoria, data, metodo di pagamento, nota e foto dello scontrino. |
| 💳 **Metodi di pagamento tuoi** | Oltre a carta, contanti e gli altri, aggiungi i tuoi (una carta specifica, i buoni pasto...) con nome, icona e colore: dalle impostazioni o al volo mentre inserisci una spesa. |
| 🔁 **Spese fisse e abbonamenti** | Netflix, Spotify, affitto, palestra: logo preimpostato o immagine tua, registrazione automatica alla scadenza e promemoria prima del rinnovo. |
| 🤝 **Para i conti** | Con più persone in casa, il report dice chi deve dare quanto a chi per dividere le spese del mese in parti uguali. Segni i conti come parati e da lì contano solo le spese nuove; una spesa si può escludere quando la paga solo chi l'ha fatta. |
| 📊 **Report del mese** | Pagella da A a F, grafico per categoria, calendario delle spese, trend di 6 mesi, confronto col mese prima, budget. Esporta in PDF o come immagine. |
| 🤖 **Assistente AI** | Con Cloudflare Workers AI: commento del mese con consigli, chat sulle tue spese, movimenti aggiunti scrivendo o a voce ("aggiungi una spesa di 15 € per il pranzo", con conferma prima di salvare), categoria automatica dalla descrizione, resoconto di fine mese. |
| 🎨 **Tuo, davvero** | Tema chiaro o scuro, colore principale a scelta, blocco con impronta, backup in JSON. |
| 🏠 **Dati sul tuo server, se vuoi** | Facoltativo: collega l'app al tuo server CasaOS dove gira la PWA di Money. Telefono e PWA vedono gli stessi dati. |
| ⬆️ **Si aggiorna da sola** | L'app controlla le release di questo repository e installa la nuova versione con un tocco. |

## 📲 Installazione

1. Scarica `Money-x.y.z.apk` dall'[ultima release](https://github.com/carellice/money/releases/latest).
2. Aprilo sul telefono e consenti l'installazione da questa fonte.
3. Dalla volta successiva gli aggiornamenti arrivano dentro l'app: **Impostazioni → Aggiornamenti**.

## 🏠 Collegare il server CasaOS

Facoltativo. Se hai installato la PWA sul tuo server CasaOS (vedi [`PWA/`](PWA/)), vai su **Impostazioni → Server CasaOS**:
- inserisci lo stesso indirizzo con cui apri la PWA (es. `https://casaos.tailXXXX.ts.net`), nome utente e password;
- se sia il telefono che il server hanno già dei dati, scegli quali tenere: quelli del telefono vengono comunque salvati in una copia.

Da lì in poi ogni modifica va al server e quelle fatte dalla PWA o da altri telefoni arrivano nell'app, all'apertura e ogni 30 secondi mentre è aperta. Senza connessione le modifiche si salvano sul telefono e partono da sole appena il server torna raggiungibile (se intanto la stessa cosa è stata cambiata anche dalla PWA, vale l'ultima modifica arrivata). Spese fisse e promemoria li gestisce il server. Scollegando il server puoi tenere i dati attuali o tornare a quelli di prima.

Con il certificato locale (modalità 2 dell'installazione) installa prima il certificato sul telefono; con Tailscale serve l'app Tailscale attiva.

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
