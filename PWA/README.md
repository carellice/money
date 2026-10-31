# Money · PWA

La versione web di Money, da installare sul proprio server CasaOS. Stessa grafica, stesse animazioni e stesse
funzioni dell'app Android, con i dati condivisi da tutta la famiglia su un server di casa.

L'app Android nella cartella `app/` resta indipendente: questa cartella non la tocca.

## Installazione su CasaOS

Apri un terminale sul server (SSH, oppure il terminale web di CasaOS) e lancia:

```bash
curl -fsSL https://raw.githubusercontent.com/carellice/money/main/PWA/casaos/installa.sh | sudo bash
```

Lo script ti chiede:

1. la cartella dei dati (predefinita `/DATA/AppData/money`);
2. la porta (predefinita `8095`);
3. una password per la famiglia (consigliata);
4. il fuso orario;
5. come aprire Money dal telefono in HTTPS:
   - **Tailscale** (consigliato): HTTPS vero anche fuori casa. Funziona sia con Tailscale installato sul sistema sia con l'app Tailscale di CasaOS;
   - **HTTPS in casa**: Money crea un suo certificato. Su ogni telefono lo installi una volta da `http://<server>:<porta>/certificato`;
   - **solo HTTP**: se hai già un reverse proxy o un Cloudflare Tunnel con HTTPS.

Poi costruisce l'immagine Docker, avvia il container e lo registra tra le app della dashboard di CasaOS.

Per aggiornare Money rilancia lo stesso comando. I dati restano dove sono.

Senza HTTPS Money funziona lo stesso nel browser. Però non si installa come app, non riceve i promemoria e non può usare
il blocco con impronta.

## Installarla sul telefono

- **Android (Chrome)**: apri l'indirizzo, menu ⋮ → *Installa app*.
- **iPhone (Safari)**: Condividi → *Aggiungi alla schermata Home*.

## Dall'app Android alla PWA

Il backup JSON ha lo stesso formato nelle due versioni:

1. sull'app Android: Impostazioni → *Esporta backup*;
2. su Money web: Impostazioni → *Importa backup*.

Le foto degli scontrini e i loghi personalizzati non fanno parte del backup, come su Android.

## Differenze rispetto all'app Android

| App Android | PWA |
|---|---|
| Dati sul telefono | Dati sul server, condivisi tra i telefoni di casa |
| Notifiche di sistema | Notifiche Web Push, inviate dal server |
| Blocco con impronta (BiometricPrompt) | Blocco con impronta tramite passkey del telefono (serve HTTPS con un nome, non un indirizzo IP) |
| Token AI cifrato sul telefono | Token AI salvato solo sul server, mai rimandato al browser |
| Aggiornamenti dalle release GitHub | Aggiornamenti rilanciando lo script; la PWA si aggiorna da sola |
| Widget dei rinnovi | Non disponibile sul web |

## Struttura

```
PWA/
├── shared/   modello dati, date, formati e report (usati da server e web)
├── server/   Node 24 senza framework: SQLite integrato, API, spese fisse, Web Push, proxy AI
├── web/      React + Vite + framer-motion: tutte le schermate dell'app
├── casaos/   installa.sh (installazione guidata) e docker-entrypoint.sh (certificati HTTPS)
└── Dockerfile
```

## Sviluppo

```bash
cd PWA/server && npm install && npm run dev      # API su http://localhost:8080, dati in server/data
cd PWA/web && npm install && npm run dev         # app su http://localhost:5173, con proxy verso le API
```

`npm run build` in `web/` compila l'app dentro `server/public`.
