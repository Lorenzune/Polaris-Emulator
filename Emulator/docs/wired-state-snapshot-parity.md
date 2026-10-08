# Stati, snapshot e movimenti: modifiche del 5 ottobre 2026

Il blocco corregge difetti riprodotti nell'emulatore e traduce i relativi campi del SWF nel formato nativo opt-in. Non certifica la parità completa di tutti i Wired né il comportamento del server Habbo.

## Configurazioni allineate

| Interazione | Campi nuovi e comportamento |
| --- | --- |
| `wf_act_match_to_sshot` | Quattro flag specifici; origine nel campo comune. Gli ID catturati rimangono i modelli snapshot anche per origini dinamiche. Posizione, rotazione e quota vengono ripristinate insieme; funziona anche una variazione di sola quota. |
| `wf_cnd_match_snapshot`, `wf_cnd_not_match_snap` | Quattro flag, origine e quantificatore separati. Il nuovo JSON conserva esplicitamente origine trigger 0; il fallback 0→100 resta per i dati legacy. Salvataggi rifiutati non modificano i flag o gli snapshot. |
| `wf_trg_stuff_state` | Nessun intero specifico; sola origine comune. La nuova card non presenta la modalità stato salvato. Evento separato di uso del furni da parte dell'utente. |
| `wf_trg_state_changed` | Un intero modalità 0/1; origine comune. Evento di modifica effettiva dello stato; può funzionare senza attore e ricevere cambi automatici. |
| `wf_act_rel_mov` | Due distanze con segno, più origine comune. Conversione ai campi persistiti precedenti e salvataggio atomico rispetto agli errori. |
| `wf_cnd_valid_moves` | Mantiene zero campi specifici; ora controlla più effetti e rifiuta stanze prive di layout. |

Gli effetti snapshot e i trigger stato caricano NULL, stringhe vuote, JSON e dati legacy malformati senza interrompere il caricamento della stanza. I formati precedenti dei pacchetti restano accettati. Le configurazioni dei trigger precedenti conservano gli eventi storici finché non vengono salvate nel formato nativo.

## Eventi

Il percorso `ToggleFloorItemEvent` conserva l'attore durante l'interazione e produce l'evento di uso se l'interazione termina normalmente. `HabboItem.setExtradata` registra una modifica effettiva; `RoomItemOperations.updateItemState` la consuma e pubblica l'evento di stato per un item vivo nella stanza. Aggiornamenti dello stesso valore e item staccati non producono eventi. Gli eventi legacy e nativi sono filtrati separatamente per evitare doppie attivazioni.

La protezione di rientro distingue tipo evento e item: un trigger uso può cambiare lo stesso furni e attivare stato cambiato, ma una catena ricorsiva di cambi stato non rientra sullo stesso item. Le interazioni che inviano pacchetti direttamente senza il percorso centrale di aggiornamento non sono state tutte ricertificate.

## Validazione dei movimenti

Snapshot, movimento relativo, gruppo, quota, furni-verso-furni, furni-verso-utente e cambio direzione implementano una simulazione. Le simulazioni di rotazione, avvicinamento e allontanamento usano il controllo comune di collisione/fisica senza generare trigger collisione. La sola rotazione verifica ora l'ingombro finale.

Il movimento verso un furni applica nella simulazione la scelta casuale del bersaglio e gli offset di spaziatura; l'esecuzione conserva il bersaglio scelto. Le scelte di rotazione/direzione e bersaglio sono isolate per contesto e non alterano la configurazione persistita durante la sola verifica.

### Limiti concreti

- Le posizioni virtuali si accumulano, ma l'occupazione e le altezze di stack restano quelle reali: non è una simulazione completa delle caselle liberate/occupate da tutta la pila. Gruppi ravvicinati possono essere rifiutati conservativamente.
- Nessuna atomicità garantita per pile ritardate o stanze che cambiano dopo la verifica.
- Movimenti utente/bot e percorsi alternativi casuali di inseguimento richiedono ancora una ricertificazione specifica: non vengono dichiarati tutti equivalenti al SWF.
- La scelta del modello per bersagli dinamici rimane la politica locale precedente. Il SWF mostra i campi, non la logica privata del server ufficiale.
- La politica precedente della condizione snapshot non configurata (vero anche con quantificatore uno qualsiasi) è mantenuta.

## Verifiche e uso

Verifica finale: **64 test Java, 11 test renderer e 3 test client passati**, controlli TypeScript client/renderer e build renderer riusciti. Nessuna prova live eseguita.

I log `state-fix-tests.log`, `state-fix-room-regressions.log`, `state-fix-renderer-tests.log`, `state-fix-client-tests.log` e `state-fix-renderer-build.log` contengono i controlli. Sono test isolati e build; nessun riavvio, deploy, commit o push eseguito.

Per verificare in stanza dopo aver caricato questa versione:

1. Salva uno snapshot con rotazione+quota e poi con posizione+quota; cambia solo la quota e verifica il ripristino.
2. Salva/riapri un confronto snapshot con origine dinamica e entrambi i quantificatori; verifica che origine e modelli restino invariati.
3. Risalva i due trigger stato nella nuova card. Usa il furni: deve attivarsi uso. Cambia poi lo stato con un effetto Wired: deve attivarsi stato cambiato, senza attivare uso. Modalità stato salvato deve filtrare il valore catturato.
4. Aggiungi tutti i movimenti validi: prova una rotazione di un furni rettangolare contro un ostacolo e un movimento relativo verso una casella mancante; la pila deve essere rifiutata.
5. Prova movimenti relativi positivi/negativi, riapertura della card e ricaricamento JSON. Un salvataggio non valido non deve cambiare la configurazione precedente.
