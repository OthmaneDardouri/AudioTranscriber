# Vocale AI

App Android che trascrive e riassume i vocali di WhatsApp **direttamente sul telefono**, con [whisper.cpp](https://github.com/ggml-org/whisper.cpp). L'audio non lascia mai il dispositivo: internet serve solo una volta, per scaricare il modello.

## Come funziona

- **Bolla 🎤**: quando arriva un vocale su WhatsApp, appare una bolla flottante. Toccala → si apre una finestrella sopra WhatsApp con riassunto e trascrizione.
- **Condividi**: in WhatsApp tieni premuto un vocale → Condividi → *Trascrivi vocale*. Funziona anche con vocali vecchi o inoltrati.
- **Lista**: nell'app trovi gli ultimi vocali ricevuti; quelli già trascritti si riaprono all'istante.

Il riassunto è *estrattivo*: l'app sceglie le frasi più importanti del vocale (non le riscrive), più le parole chiave. Sotto le ~45 parole il vocale è già abbastanza corto e mostra solo la trascrizione.

## Ottenere l'APK (GitHub Actions)

1. Crea un repository **privato** su GitHub e carica questi file (il keystore per firmare l'app è incluso, per questo meglio privato).
2. Ogni push su `main` avvia *Actions → Build APK* (~10-15 minuti la prima volta).
3. A build finita trovi `VocaleAI.apk` in **Releases → latest**: aprilo dal telefono e installalo (consenti "installa app sconosciute" al browser).

Le build successive si installano sopra la precedente senza perdere i dati, perché sono firmate sempre con la stessa chiave.

## Primo avvio

1. **Modello**: scarica *Small* (~181 MB, consigliato). *Base* è più veloce ma meno preciso; *Turbo* è il più preciso ma lento.
2. **Cartella vocali**: tocca *Scegli cartella*, si apre già su `WhatsApp Voice Notes` → *Usa questa cartella* → *Consenti*.
3. **Accesso notifiche**: attiva *Vocale AI*. Su Android 13+, se l'interruttore è grigio: Info app → ⋮ → *Consenti impostazioni con restrizioni* (succede a tutte le app installate fuori dal Play Store).
4. **Sovrapposizione**: consenti la visualizzazione sopra altre app per la bolla. Senza, ricevi una notifica al suo posto.

Bolla: tocco = trascrivi · trascina = sposta · pressione lunga = chiudi.

## Struttura

| Percorso | Cosa fa |
|---|---|
| `app/src/main/cpp/` | Ponte JNI verso whisper.cpp (v1.7.4, scaricato in fase di build) |
| `whisper/` | Caricamento modello, download, inferenza |
| `audio/` | Decodifica Opus/Ogg → PCM 16 kHz mono, ricampionamento sinc |
| `detect/` | Listener notifiche WhatsApp, riconoscimento dei vocali |
| `bubble/` | Bolla flottante e notifica di riserva |
| `text/Summarizer.kt` | Riassunto estrattivo offline (IT/EN/AR + darija) |
| `ui/` | Schermata principale e finestra di trascrizione (Jetpack Compose) |

Requisiti: Android 10+ (Ogg/Opus nativo), telefono arm64.
