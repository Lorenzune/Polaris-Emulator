# Integrazione upstream approvata — 10 ottobre 2026

Snapshot iniziale pubblicato: `2c61b947`. Branch: `backup/2026-10-08`.

Integrati: separazione save profilo/rank/wallet con API legacy persistite nel ledger; reload permessi con snapshot completi e rank aggiornato al reconnect; spawn server-authoritative; caricamento batch di ownership/Builders Club; registrar TCP/WS anticipato; shutdown networking con attese limitate; A* con array riutilizzabili e tile nascoste; query marketplace aggregate e indice catalogo invalidabile; contenimento percorsi badge e limiti velocità chat bot. Corretta anche la coerenza equals/hashCode delle tile usate nella cache.

Interfacce plugin e personalizzazioni locali conservate. Non introdotti Redis/PostgreSQL o nuovi comportamenti dello scheduling WIRED. Il pathfinding verso una destinazione occupata conserva il default locale.

La migrazione `V20261010130000__marketplace_query_indexes.sql` aggiunge indici solo dove manca un indice compatibile: non è stata applicata a un DB di produzione. Prima del rilascio eseguire gli IT con MariaDB isolato e verificare EXPLAIN/costi su dati rappresentativi. Gli IT dipendenti da Docker sono saltati in questo ambiente; non considerarli superati.

Compilazione, unit test, packaging e controllo Spotless verificati. `verify` resta bloccato dai cinque finding SpotBugs riprodotti sullo snapshot iniziale: WiredEffectChangeVariableValue (random convertito a zero), WiredEffectUserFurniBase e WiredEffectUserToFurni (null-check), WiredExtraMoveNoAnimation (dead store), WiredTriggerRepeaterShort (possibile null dereference). I gate non sono stati disabilitati.

Prove di staging ancora necessarie: login TCP/WS e reconnect dopo demozione, errori/concorrenza SQL del ledger, teletrasporti, Builders Club, avatar/pet e pile WIRED. Nessun dato percentuale di riduzione della RAM/CPU di produzione è disponibile. L'indice catalogo e le mappe permessi aumentano la memoria trattenuta in cambio di meno scansioni/stati parziali; le API wallet legacy ora pagano una transazione immediata.
