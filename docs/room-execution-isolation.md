# Isolamento dell'esecuzione delle stanze

Implementazione dell'11 ottobre 2026, limitata all'emulatore.

## Cicli stanza

`RoomCycleService` non assegna più permanentemente tutte le stanze dello stesso gruppo a un solo thread. Ogni stanza mantiene un singolo turno pianificato, con scadenze fixed-rate di 500 ms. Il prossimo turno viene pianificato dopo la fine del precedente: nessuna sovrapposizione sulla stessa pianificazione e nessun ciclo scartato. `Room.run()` mantiene anche il proprio lock esistente.

I turni normali usano un pool condiviso. Dopo un turno oltre `room.cycle.slow_ms`, i successivi passano a un pool separato fino alla cancellazione della pianificazione. Lo spostamento avviene tra due turni e conserva la scadenza, compreso il recupero dei cicli arretrati. Non preempta un ciclo già in esecuzione.

La cancellazione continua a restituire un `ScheduledFuture`: annulla i turni successivi, rimuove il riferimento alla stanza e, con `cancel(false)`, lascia terminare il turno già iniziato. La chiusura dispone di un budget complessivo di cinque secondi per i worker.

## Clock WIRED

`RoomWiredTickDispatcher` gestisce una lane per stanza. In ogni lane esiste al massimo un turno di callback attivo; richieste del clock aggiornano il contatore richiesto senza creare una coda di callback per ogni tick. Ogni turno completa la visita degli stessi tickable prima di cedere il worker, quindi conserva la sequenza interna. Le stanze lente passano al pool separato dopo un turno oltre 50 ms.

La regola preesistente per recuperare il clock in ritardo resta identica, ma viene applicata per stanza: se il divario supera cinque tick, riparte dagli ultimi sei. Si tratta di notifiche del clock, non dello scarto di pubblicazioni, catene o effetti accettati. Il comportamento dei repeaters basato sul tempo resta affidato agli stessi callback originali.

Lo scaricamento ritira la lane. Se una stanza viene registrata nuovamente mentre un turno precedente termina, la lane viene riutilizzata per evitare esecuzioni sovrapposte, ripartendo dal clock corrente senza riprodurre tick della registrazione precedente. Il callback controlla ancora stanza caricata, occupazione e appartenenza del tickable.

## Compatibilità WIRED

Non sono cambiati selettori, condizioni, effetti, parametri, serializzazione, variabili, priorità delle pile, limiti antiabuso o scheduler delle continuazioni/delay. I callback sincroni da movimento, uso e pacchetti restano sincroni: convertirli in lavoro asincrono cambierebbe la relazione tra l'azione e le condizioni valutate.

I callback WIRED del clock e il ciclo stanza rimangono due sistemi distinti, come prima; questa modifica non li serializza fra loro. Nessun cambiamento richiesto a client, renderer, CMS o database.

## Configurazione

| Chiave | Default | Significato |
|---|---|---|
| `room.cycle.room.isolation` | `true` | Cicli indipendenti; `false` ripristina il precedente scheduler a gruppi |
| `room.cycle.workers` | automatico, 2–16 | Worker normali; isolamento attivo minimo 2, massimo 64 |
| `room.cycle.slow.workers` | `2` | Worker per cicli lenti, da 1 a 64 |
| `room.cycle.slow_ms` | `250` | Soglia di classificazione lenta, in millisecondi |
| `wired.tick.room.isolation` | `true` | Turni WIRED indipendenti; `false` ripristina i gruppi precedenti |
| `wired.tick.workers` | automatico, 2–8 | Worker normali; isolamento attivo minimo 2, massimo 32 |
| `wired.tick.slow.workers` | `2` | Worker per turni WIRED lenti, da 1 a 32 |
| `wired.tick.interval.ms` | `50` | Clock WIRED invariato, intervallo consentito 10–500 ms |
| `wired.tick.debug` | `false` | Include conteggi diagnostici di lane attive e stanze classificate lente |

Le opzioni vengono lette all'avvio dei servizi: richiedono riavvio dell'emulatore. Non serve una migrazione SQL. Per rollback impostare entrambe le chiavi `.room.isolation` a `false`.

## Costi e limiti

I pool lenti aggiungono fino a quattro thread ai default precedenti, due per i cicli e due per il clock WIRED, creati quando vengono utilizzati. Nessun thread permanente per stanza. I vecchi executor di rollback non avviano thread nell'esecuzione isolata.

La memoria dello scheduler cresce con le stanze registrate, senza una nuova voce di coda per ogni tick perso. L'isolamento richiede più submission: a 50 ms circa 20 turni per secondo per stanza con tickable WIRED registrati; con 100 stanze circa 2.000 turni/s, più circa 200 cicli stanza/s a 500 ms. Sono conteggi teorici, non un benchmark CPU. Prima i task dei gruppi visitavano più stanze nello stesso turno.

Una stanza bloccata può occupare un worker durante il primo turno lento; viene classificata solo quando il turno termina. Più stanze bloccate contemporaneamente possono saturare un pool. CPU, memoria, garbage collector, database e lock globali rimangono risorse condivise: non è isolamento di processo né una garanzia contro qualsiasi forma di lag globale.

Le continuazioni WIRED, gli effetti ritardati e i pacchetti conservano i percorsi esistenti. Non sono inclusi nella garanzia di separazione dei worker del clock e dei cicli. Una loro eventuale modifica richiede una revisione separata per preservare la semantica.

## Verifica

Le regressioni dedicate coprono stanze dello stesso vecchio gruppo, stanza bloccata, pool lento bloccato mentre il normale procede, ordine, turni duplicati, recupero clock, migrazione senza sovrapposizione, scaricamento/riapertura, eccezioni, retry dopo rifiuto dell'executor, cancellazione e rollback configurabile.

La suite completa include i contratti esistenti per API/plugin, esecuzione WIRED, persistenza e pacchetti. Non sono stati eseguiti benchmark sul server live; la build non costituisce una prova completa dell'interazione con tutti i plugin di produzione.

Esito della build finale: `mvn clean package` — **BUILD SUCCESS**. 1865 test, 0 failure, 0 errori, 11 saltati. I 18 test dedicati a ciclo stanza e dispatcher passano. I contratti WIRED e le soglie architetturali passano senza aggiornamenti dei fixture dovuti all'isolamento.

Log finale: [room-isolation-build-2026-10-11.log](D:/ducket/reports/room-isolation-build-2026-10-11.log).
