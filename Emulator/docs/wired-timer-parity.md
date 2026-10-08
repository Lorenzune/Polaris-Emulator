# WIRED timer e contatori: campi SWF e runtime Polaris

## Ambito

Questo blocco allinea 11 interazioni. L'importatore rimane fuori scope.
Le sorgenti sono i controller decompilati locali in `D:/ducket/decomp0909/scripts/com/sulake/habbo/roomevents/wired_setup`, i relativi slider converter, `uibuilder/presets/sections/SliderSection.as`, `uibuilder/presets/NumberInputPreset.as` e i testi ufficiali locali.
Lo SWF descrive UI e formato dei campi; non contiene l'implementazione del server Habbo. I comportamenti server sotto sono implementati e verificati in Polaris, non certificati contro un server Habbo ufficiale.

| Interazione | Codice SWF / locale | Int specifici SWF | Campi comuni e UI |
|---|---|---|---|
| `wf_trg_at_given_time` | 3 / 3 | `[pulsazioni]`, 1..1200, 500 ms per pulsazione | Nessuna selezione; slider e input secondi 0,5..600 |
| `wf_trg_periodically` | 6 / 6 | `[pulsazioni]`, 1..120, 500 ms per pulsazione | Nessuna selezione; slider e input secondi 0,5..60 |
| `wf_trg_period_long` | 12 / 12 | `[passi]`, 1..120, 5 s per passo | Nessuna selezione; slider e input secondi 5..600 |
| `wf_trg_period_short` | 19 / 17 | `[passi]`, 1..10, 50 ms per passo | Nessuna selezione; solo slider, come il controller SWF; default 1 = 50 ms |
| `wf_act_reset_timers` | 1 / 1 | `[]` | Ritardo comune 0..20 pulsazioni, senza duplicarlo negli int specifici |
| `wf_cnd_time_more_than` | 3 / 3 | `[slider + 1]`, 2..1201 | Slider e input 0,5..600 s; sottrarre 1 al salvataggio nel formato locale |
| `wf_cnd_time_less_than` | 4 / 4 | `[slider + 1]`, 2..1201 | Come sopra |
| `wf_trg_clock_counter` | 15 / 22 | `[secondi interi, minuti, mezzo secondo]` | Secondi 0..59, minuti 0..99, mezzo secondo 0/1; origine Furni 100/200 separata |
| `wf_act_control_clock` | 28 / 41 | `[comando]` | 0 Start, 1 Stop, 2 Reset, 3 Pausa, 4 Riprendi; origine e ritardo separati |
| `wf_act_adjust_clock` | 38 / 42 | `[secondi interi, minuti, mezzo secondo, operatore]` | 0 Aumenta, 1 Diminuisci, 2 Imposta; origine e ritardo separati |
| `wf_cnd_counter_time_matches` | 35 / 27 | `[secondi interi, minuti, mezzo secondo, confronto]` | 0 Inferiore, 1 Uguale, 2 Superiore; origine e quantificatore tutti/uno separati |

Controller: `triggerconfs/TriggerOnce.as`, `triggerconfs/§_-A1z§.as`, `triggerconfs/§_-45§.as`, `triggerconfs/§_-wP§.as`, `triggerconfs/ClockReachTime.as`, `actiontypes/§_-817§.as`, `actiontypes/§_-g1U§.as`, `actiontypes/§_-d1Q§.as`, `conditions/§_-H3§.as`, `conditions/§_-v1x§.as`, `conditions/ClockTimeMatches.as`.

## Trasporto e compatibilità

`getNativeVariableSettings` in renderer crea campi e origini separati usando il marker opt-in già esistente `-2026100401`. I codici dell'envelope continuano a essere quelli Polaris: codice SWF 19 non va confuso con locale 17, né controllo clock SWF 28 con selettore area locale 28.
`WiredNativeTimerSettings` in emulatore valida il nuovo formato e lo traduce nei campi persistiti già esistenti. Nessuna migrazione DB o importatore è richiesto.
Le risposte continuano nel formato Nitro locale letto dalle card; un successivo salvataggio lo converte nuovamente nel formato SWF. Non è un nuovo endpoint compatibile senza adattamento con un client SWF originale.

JSON e vecchi salvataggi senza marker restano leggibili. I millisecondi di timer già validi e gli int locali dei contatori non vengono reinterpretati come unità SWF. I vecchi intervalli oltre il range SWF sono mantenuti dall'emulatore, ma le nuove card limitano la configurazione al range ufficiale quando vengono salvate.
I nuovi timer e i valori mancanti/invalidi usano un passo come default; questo corregge i precedenti default 5/50/0,5 secondi e il fallback 10/100 secondi. Il trigger breve carica anche dati malformati senza interrompere il caricamento della stanza.
Le selezioni e i campi dei clock vengono confermati solo dopo la validazione completa. Un salvataggio rifiutato lascia inalterata la configurazione precedente.
Le origini dinamiche non mostrano le istruzioni generiche di selezione manuale; tornando a 100 si può selezionare il contatore. Le cinque operazioni conservano il supporto locale ai game timer discendenti; regolazione, confronto e clock trigger usano i contatori crescenti.

## Runtime

- `WiredTimerClock` conta i tick trascorsi dalla registrazione/reset del singolo timer. Una modifica dell'intervallo ricomincia il conteggio. Un reset di stanza usa il medesimo tick di riferimento per i timer registrati.
- Il reset dei periodici funziona realmente. I periodici non dipendono più da un multiplo esatto del contatore globale del server.
- Quando il tick non divide l'intervallo, la prima esecuzione avviene al tick che raggiunge la scadenza; il resto viene mantenuto per la cadenza successiva. Tick saltati vengono conteggiati senza riprodurre una raffica di esecuzioni arretrate.
- Il timer a esecuzione singola scatta una volta e attende reset. Se l'accessorio di limite esecuzioni lo blocca, conserva la scadenza e può scattare una volta quando il limite lo consente.
- Gli effetti ritardati mantengono la conversione di 500 ms per pulsazione e lo snapshot del contesto. I controlli su attore, furni e generazione della stanza impediscono esecuzioni obsolete; l'arresto dello scheduler annulla i pendenti e libera il conteggio. Questi percorsi sono coperti dai test di regressione esistenti.
- Il reset dei timer non cancella gli effetti già ritardati: nessun campo del relativo controller SWF richiede quella cancellazione.
- Le condizioni sul tempo dalla partenza/reset della stanza usano millisecondi, non timestamp arrotondati ai secondi. Il confronto resta stretto (`>` / `<`), con soglia locale di 500 ms per passo.
- Il contatore crescente avanza e notifica i trigger ogni 500 ms, quindi anche la componente mezzo secondo è raggiungibile. A Start da zero emette anche il valore zero. Pausa e ripresa conservano il valore.
- La precisione dei timer periodici è limitata da `wired.tick.interval.ms` (default 50 ms); non si promette precisione di 50 ms con un tick configurato a 500 ms.

## Verifica automatica

- `WiredTimerClockTest`: fase di registrazione/reset, tick non divisibili, tick saltati, nessuna duplicazione, esecuzione singola e limite esecuzioni.
- `WiredNativeTimerParityTest`: conversioni SWF, salvataggio e reload, range rifiutati senza mutazione, contatori a mezzo secondo, confronto, cinque comandi clock.
- `WiredTimerInputGuardTest`, `WiredEffectResetTimersTest`, `WiredDelayedSchedulerTest`, `WiredDelayedExecutionSnapshotTest`: regressioni su ingressi, reset e ritardi/cancellazione.
- Renderer `WiredNativeTimerSettings.test.ts`: formato dei pacchetti, origini separate, offset delle condizioni, assenza di campi specifici nel reset e assenza di collisioni con selettori/variabili.
- Client `WiredTimerParity.test.tsx`: input in secondi sincronizzati con le unità, restore/save delle card, timer breve senza input aggiunto, selezione manuale/dinamica dei clock.

## Prova in stanza

1. Ripetitore normale a 1,5 s + effetto visibile: osservare la cadenza; premere un furni che esegue Reset timer e verificare che la successiva esecuzione aspetti nuovamente 1,5 s.
2. Timer singolo a 1,5 s: deve eseguire una volta sola. Reset lo riabilita; riaprire la card conserva 1,5 s.
3. Ripetitore lungo a 15 s e breve a 150 ms: verificare le due conversioni e riaprire le card. Evitare effetti di chat ad alta frequenza per questa prova.
4. Contatore crescente: Start, Pausa, Riprendi, Stop, Reset su cinque pulsanti. Pausa deve fermare il valore, Riprendi ripartire da quello stesso valore.
5. Regola clock -> Imposta 0 minuti e 3,5 s; confronta clock Uguale a 3,5 s; poi clock trigger a 0,5 s e Start da zero. Il trigger a mezzo secondo deve ora scattare.
6. Ripetere la selezione con origine 200 (selettore): le card non devono richiedere la selezione manuale, e devono agire sul contatore scelto dal selettore.
7. Salva e riapri le card, poi scarica/ricarica la stanza per verificare il ripristino dal JSON.
8. Effetto con ritardo 1 s: togli l'effetto prima della scadenza oppure scarica/ricarica la stanza; il lavoro della vecchia configurazione/stanza non deve eseguire. Un normale reset timer, invece, non annulla quell'effetto.

La prova live sopra non è stata eseguita in questa sessione. Non sono stati riavviati servizi, distribuiti file né effettuati commit/push.
