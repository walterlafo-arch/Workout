# Log di bordo — Android 3.1

Questa è la prima conversione dell'attuale `workout-tracker-13.html` in una vera app Android basata su WebView, mantenendo l'interfaccia HTML/JS esistente.

## Migliorie già incluse
- dati mantenuti nell'app tramite localStorage WebView (chiave `logdabordo_v1`, storage isolato dal browser: i dati del Chrome del telefono NON sono condivisi automaticamente con l'app — per portarli dentro serve un export/import manuale col backup);
- backup completo, non più solo delle sessioni;
- backup JSON versionato (`logdabordo-backup`, versione 2);
- import compatibile sia con il nuovo backup completo sia con i vecchi JSON-array di sessioni;
- salvataggio/ripristino del file JSON tramite il selettore file Android;
- nessuna dipendenza da Chrome per l'uso quotidiano;
- progetto Android pronto per Android Studio / GitHub Actions.

## Build
Il progetto usa Android Gradle Plugin 9.2.0, Gradle 9.4.1, compile/target SDK 36 e min SDK 26.

In Android Studio: apri questa cartella e avvia la build.

Su GitHub: il workflow `.github/workflows/android.yml` produce automaticamente un APK debug come artifact.

## Passo successivo consigliato
Prima della pubblicazione sul Play Store conviene aggiungere database SQLite/Room o un layer di persistenza più robusto, backup automatico e sincronizzazione cloud opzionale. Il JSON resta esclusivamente un formato di import/export.
