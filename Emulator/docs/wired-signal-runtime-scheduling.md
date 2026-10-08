# Catene di segnali: consegna e prestazioni

Implementazione generale del 7 ottobre 2026, comune a tutte le stanze. Non richiede nuovi pacchetti, modifiche al client, preset o migrazioni SQL. L'attivazione richiede un nuovo avvio del processo Java: le continuazioni aggiungono stato e collaboratori al motore.

## Consegna

Il vecchio budget della catena poteva cancellare tutti i segnali pendenti al superamento di 250 ms o del limite di eventi. Ora la coda conserva le pubblicazioni accettate e riprende il lavoro dopo una pausa. Le continuazioni mantengono ordine FIFO nella singola catena, payload, origine, timestamp e profondità delle chiamate. Non diventano nuovi eventi con dati ricostruiti.

Un gruppo contiguo di segnali nativi viene ammesso insieme, prima di eseguire gli effetti dei suoi ricevitori. Il gruppo resta indivisibile: spezzare l'ammissione dopo una modifica di turno o stato potrebbe ammettere ricevitori diversi e alterare i giochi. La pausa avviene fra gruppi. Per questo i limiti di tempo/eventi della slice sono obiettivi cooperativi, non un limite assoluto al tempo CPU di un singolo gruppo.

Il budget di usage e la frequenza configurata rinviano il gruppo successivo invece di scartare una parte del gruppo corrente. L'usage registra ancora tutto il costo stimato; non viene azzerato o ridotto artificialmente. Un gruppo indivisibile può superare il budget della finestra, poi la coda aspetta la finestra successiva. Le continuazioni sono lavoro in memoria, non una coda persistente su database.

Un errore in un ricevitore o nella pubblicazione di un frame ritardato viene registrato e non interrompe le altre consegne accettate. Gli effetti parzialmente eseguiti non vengono ritentati automaticamente, perché potrebbero duplicare spostamenti, punteggi o premi.

Restano annullamenti espliciti: stanza scaricata/ricaricata, WIRED disabilitati e arresto del motore. Restano anche condizioni, requisiti dell'utente, limiti di esecuzione impostati nei WIRED e controllo dei passaggi dello stack. La nuova coda non forza effetti che non soddisfano più i requisiti.

Un limite separato contiene le catene di feedback infinite: raggiungerlo rifiuta nuove pubblicazioni, registra un avviso una volta e lascia terminare la coda già accettata. Non è una promessa di esecuzione illimitata o di conservazione attraverso il riavvio del server.

## Ricerca dei ricevitori

I ricevitori standard con antenne selezionate vengono indicizzati per ID dell'antenna. I ricevitori con selettori dinamici e i trigger forniti da plugin mantengono la valutazione a ogni consegna. La lista unita conserva l'ordine originale degli stack; il matcher continua a verificare disponibilità e tipo dell'antenna.

La cache include generazione della stanza, generazione dei WIRED ed epoca di invalidazione. Una modifica durante la costruzione provoca una ricostruzione. Si memorizzano le liste unite soltanto per canali configurati, evitando crescita della cache per ID arbitrari. Anche la cache dei trigger sorgente evita la scansione globale delle chiavi a ogni hit.

Con 100 ricevitori fissi su antenne diverse il test esamina un candidato per segnale, anziché 100. Questo prova la riduzione dei candidati, non una riduzione del 99% del carico complessivo o dell'usage dei giochi. Fan-out reale, selettori, condizioni, effetti e SQL possono restare il costo dominante. Il numero di segnali necessari al gioco non viene ridotto fondendo payload distinti.

## Configurazione

| Chiave | Default | Significato |
| --- | ---: | --- |
| `wired.signal.slice.events` | 256 | Eventi elaborati prima di cedere il worker fra gruppi |
| `wired.signal.slice.ms` | 8 | Tempo della slice, controllato fra gruppi |
| `wired.signal.max.events.per.chain` | 2048 | Vecchia chiave: ora limita superiormente la dimensione della slice |
| `wired.signal.max.chain.ms` | 250 | Vecchia chiave: ora limita superiormente il tempo della slice |
| `wired.signal.max.total.events.per.chain` | 100000 | Ammissioni complessive della catena; rifiuta solo nuove pubblicazioni |

Le vecchie chiavi positive vengono rispettate come limiti superiori delle slice. Le slice restano almeno di un evento e un millisecondo. Un totale non positivo disabilita il limite di ammissione: il default finito è adatto a contenere feedback non terminanti. Le impostazioni esistenti di frequenza, ban e usage della stanza restano separate.

Il motore possiede un task per catena sospesa, con annullamento al shutdown e controllo della generazione della stanza. Oltre 10000 continuazioni pendenti, oppure se l'executor rifiuta il task mentre il motore è ancora attivo, il lavoro già accettato termina sul worker corrente. Questa degradazione rinuncia temporaneamente al pacing; viene segnalata una sola volta per scheduler. L'arresto del motore annulla il lavoro invece di usare il fallback. Una cancellazione fallita dell'executor non consente al callback annullato di eseguire effetti.

Non viene garantito un ordine globale fra catene indipendenti o esclusione reciproca con tutti gli eventi della stanza. La pausa rende possibile l'interleaving con altri eventi. Il controllo dei requisiti dei ricevitori rimane necessario.

## Verifica

Test di catena lunga con 3000 pubblicazioni e confronto completo dell'ordine; completamento attraverso dispatcher ed effetti del motore reale; payload distinti; ammissione atomica dei segnali nativi; usage/frequenza; errori isolati; rifiuto delle sole nuove pubblicazioni; reload, shutdown, cancellazione fallita e fallback dell'executor. I test di regressione coprono anche movimenti, timer, turni, filtri, selettori e variabili.

I risultati numerici e le anomalie del controllo più ampio sono riportati in `D:/ducket/reports/wired-signal-runtime-2026-10-07/report.md`. Le prove automatiche non sostituiscono partite complete dei quattro preset dopo l'attivazione né un confronto CPU/JDBC controllato prima/dopo.
