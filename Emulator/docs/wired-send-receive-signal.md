# WIRED Send Signal, Receive Signal e Antenna

Questo documento descrive il comportamento runtime atteso e attuale dei WIRED `Send Signal`, `Receive Signal` e dei furni `antenna` nell'emulatore.

I riferimenti principali nel codice sono:

- `WiredEffectSendSignal`
- `WiredTriggerReceiveSignal`
- `InteractionAntenna`
- `WiredEvent`
- `WiredContext`
- `WiredSourceUtil`
- `WiredTriggerSourceUtil`
- `WiredManager.dispatchEffectTriggeredEvent`
- `WiredEngine` / `WiredStackExecutor`

## Concetto generale

Il `Send Signal` non esegue direttamente lo stack del `Receive Signal`.

Il `Send Signal` crea uno o più eventi `WiredEvent.Type.SIGNAL_RECEIVED`. Ogni evento viene poi processato dal normale motore wired, esattamente come un evento di camminata, click, timer, ecc.

Il `Receive Signal` è un trigger. Uno stack con `Receive Signal` si attiva quando riceve un evento `SIGNAL_RECEIVED` compatibile con la sua antenna/canale.

Flusso base:

```text
trigger iniziale
  -> stack con Send Signal
      -> risolve antenna/e
      -> risolve furni/users da inoltrare
      -> crea evento/i SIGNAL_RECEIVED
          -> WiredEngine trova gli stack Receive Signal compatibili
          -> selector
          -> condition
          -> effects
```

## Antenna

Un'antenna è un furni con interaction type `antenna`.

L'antenna ha due ruoli:

1. È il target fisico del `Send Signal`.
2. Il suo `item id` è usato come `signalChannel`.

Quindi il canale reale di un segnale non è un numero arbitrario separato: nel runtime attuale è l'id dell'antenna.

Esempio:

```text
antenna id = 1503
SIGNAL_RECEIVED.signalChannel = 1503
```

### Pulse grafico antenna

Quando un `Send Signal` invia un segnale a un'antenna, chiama `pulseAntenna`.

Il pulse:

- controlla che il furni sia valido;
- controlla che abbia almeno 2 stati;
- alterna `extradata` tra `"0"` e `"1"`;
- invia `room.updateItemState(antenna)`.

Questo serve solo all'animazione/stato visuale dell'antenna. Non determina quali stack vengono eseguiti.

## Click manuale sull'antenna

`InteractionAntenna.onClick` genera direttamente un evento `SIGNAL_RECEIVED`.

Campi principali dell'evento creato dal click:

```text
type              = SIGNAL_RECEIVED
tile              = tile dell'antenna cliccata
signalChannel     = id dell'antenna cliccata
signalUserCount   = 1 se c'è un actor, altrimenti 0
signalFurniCount  = 1
sourceItem        = antenna cliccata
actor             = utente che ha cliccato, se presente
originActor       = stesso utente che ha cliccato, se presente
```

Il click antenna non imposta:

- `signalPerFurni`
- `signalPerUser`
- `signalSourceItems`
- `signalSourceUsers`

Quindi un click manuale viene visto come un singolo segnale semplice, con `sourceItem = antenna`.

## Send Signal: configurazione salvata

`WiredEffectSendSignal` salva questi campi nel JSON `wired_data`:

```json
{
  "delay": 0,
  "itemIds": [],
  "forwardItemIds": [],
  "antennaSource": 0,
  "furniForward": 0,
  "userForward": 0,
  "signalPerFurni": false,
  "signalPerUser": false,
  "channel": 0
}
```

### `itemIds`

Lista delle antenne selezionate nel box `Send Signal`.

Questi item devono essere furni di tipo `antenna`.

### `antennaSource`

Indica da dove il `Send Signal` prende l'antenna a cui inviare il segnale.

Valori interni rilevanti:

```text
0 = ANTENNA_PICKED
1 = ANTENNA_TRIGGER
>1 = id diretto di una antenna
```

Comportamento:

- `ANTENNA_TRIGGER`: usa `ctx.sourceItem()` come antenna.
- `ANTENNA_PICKED`: usa `itemIds`.
- `> 1`: prova a usare quel numero come id antenna diretto.

Alla fine, le antenne risolte vengono filtrate da `isAntennaItem`. Se nessuna antenna valida resta, il `Send Signal` non invia nulla.

### `furniForward`

Indica quali furni vengono trasportati nel segnale.

Valori comuni da `WiredSourceUtil`:

```text
0   = SOURCE_TRIGGER
100 = SOURCE_SELECTED
200 = SOURCE_SELECTOR
201 = SOURCE_SIGNAL
```

Il `Send Signal` usa `WiredSourceUtil.resolveItemsRaw(ctx, furniForward, forwardItems)`.

Nota importante: usa `resolveItemsRaw`, quindi non applica il filtro selector finale `WiredSelectionFilterSupport`. Questo serve a inoltrare esattamente la sorgente richiesta.

### `forwardItemIds`

Seconda selezione furni del `Send Signal`.

Viene usata solo quando:

```text
furniForward == SOURCE_SELECTED
```

Questa è la lista dei furni da mettere nel payload del segnale.

### `userForward`

Indica quali utenti vengono trasportati nel segnale.

Valori comuni:

```text
0   = SOURCE_TRIGGER
11  = SOURCE_CLICKED_USER
100 = SOURCE_SELECTED
200 = SOURCE_SELECTOR
201 = SOURCE_SIGNAL
```

Il `Send Signal` usa `WiredSourceUtil.resolveUsersRaw(ctx, userForward)`.

### `signalPerFurni`

Se `false`, il `Send Signal` genera un solo evento per la lista furni inoltrata.

Se `true`, il `Send Signal` genera una esecuzione/evento separato per ogni furni inoltrato.

Questo è il punto centrale della raffica.

### `signalPerUser`

Se `false`, il `Send Signal` genera eventi usando l'utente origine della catena.

Se `true`, il `Send Signal` genera una esecuzione/evento separato per ogni utente inoltrato.

Quando `signalPerUser=true`, l'utente origine viene inserito nella lista insieme agli utenti inoltrati, evitando duplicati tramite id.

### `channel`

Campo presente nel JSON e nella UI, ma nel runtime attuale il canale effettivo dell'evento viene impostato da:

```text
signalChannel = antenna.getId()
```

Il campo `channel` non è quello che decide il routing finale dell'evento. Il routing è legato all'antenna.

## Send Signal: esecuzione runtime

Quando `WiredEffectSendSignal.execute(ctx)` viene chiamato:

1. Legge la room dal context.
2. Controlla la profondità del segnale.
3. Risolve le antenne.
4. Risolve gli utenti da inoltrare.
5. Risolve i furni da inoltrare.
6. Costruisce la lista utenti effettiva.
7. Costruisce la lista furni effettiva.
8. Genera uno o più eventi `SIGNAL_RECEIVED`.

## Limite profondità segnale

Il limite è:

```text
WiredEffectSendSignal.MAX_SIGNAL_DEPTH
```

La configurazione collegata è:

```text
wired.signal.max.depth
```

Valore importante:

```text
0 = nessun limite
```

Se il limite è maggiore di zero, il send si ferma quando:

```text
currentDepth >= MAX_SIGNAL_DEPTH
```

Ogni signal generato incrementa la profondità:

```text
nextDepth = currentDepth + 1
```

## Costruzione utenti del signal

Il `Send Signal` parte da:

```java
RoomUnit triggeringUser =
    ctx.event().getOriginActor().orElseGet(() -> ctx.actor().orElse(null));
```

Quindi cerca di preservare l'utente originale della catena.

Poi risolve:

```java
forwardedUsers = WiredSourceUtil.resolveUsersRaw(ctx, userForward)
```

### Se `signalPerUser=false`

Viene generato un solo utente per evento:

```text
usersToSend = [triggeringUser]
```

`signalSourceUsers` contiene:

- `forwardedUsers`, se presenti;
- altrimenti `triggeringUser`, se presente;
- altrimenti lista vuota.

### Se `signalPerUser=true`

Viene creata una mappa ordinata per id:

1. prima `triggeringUser`, se presente;
2. poi tutti i `forwardedUsers`.

Duplicati rimossi per id.

Se non ci sono utenti, viene comunque creato un elemento `null` per permettere al segnale di partire senza actor.

Esempio:

```text
triggeringUser = 10
forwardedUsers = [10, 11, 12]

usersToSend = [10, 11, 12]
```

## Costruzione furni del signal

Il `Send Signal` risolve:

```java
forwardedFurni = WiredSourceUtil.resolveItemsRaw(ctx, furniForward, forwardItems)
```

Poi crea:

```text
signalSourceItems = forwardedFurni senza duplicati, ordine preservato
```

### Se `signalPerFurni=false`

Viene generato un solo evento.

Il `sourceItem` dell'evento è:

```text
primo item di signalSourceItems
```

Se la lista è vuota:

```text
sourceItem = null
```

In questo caso `SOURCE_SIGNAL` nei consumer può rappresentare tutta la lista `signalSourceItems`, perché il signal non è stato splittato per furni.

### Se `signalPerFurni=true`

Viene generato un evento per ogni furni in `signalSourceItems`.

Esempio:

```text
signalSourceItems = [1438, 1439, 1440, 1441]
```

Eventi generati:

```text
SIGNAL_RECEIVED sourceItem=1438
SIGNAL_RECEIVED sourceItem=1439
SIGNAL_RECEIVED sourceItem=1440
SIGNAL_RECEIVED sourceItem=1441
```

Ogni evento contiene anche la lista completa `signalSourceItems`, ma quella lista è metadato. Il target principale dell'esecuzione corrente è `sourceItem`.

Quindi, con `signalPerFurni=true`, ogni wired che usa `SOURCE_SIGNAL` deve vedere solo il furni corrente.

## Combinazione `signalPerUser` + `signalPerFurni`

Il codice usa tre cicli annidati:

```text
for user in usersToSend
  for sourceItem in furniToSend
    for antenna in resolvedAntennas
      fireSignalAtAntenna(...)
```

Quindi il numero massimo di eventi generati è:

```text
usersToSend.size * furniToSend.size * resolvedAntennas.size
```

Esempio:

```text
2 utenti
4 furni
1 antenna
= 8 eventi SIGNAL_RECEIVED
```

Se ci sono più antenne selezionate, ogni coppia user/furni viene inviata a ogni antenna.

## Evento `SIGNAL_RECEIVED`

Ogni evento generato dal `Send Signal` contiene:

```text
type                 = SIGNAL_RECEIVED
tile                 = tile dell'antenna
callStackDepth       = profondità + 1
signalChannel        = id antenna
signalUserCount      = numero utenti inoltrati
signalFurniCount     = numero furni inoltrati
signalSourceUsers    = lista completa utenti inoltrati
signalSourceItems    = lista completa furni inoltrati
signalPerUser        = flag split utenti
signalPerFurni       = flag split furni
contextVariableScope = copia delle variabili context
triggeredByEffect    = true
createdAtMs          = timestamp comune della raffica
actor                = utente corrente della iterazione, se presente
originActor          = utente originale della catena, se presente
sourceItem           = furni corrente della iterazione, se presente
```

### `createdAtMs`

Tutti gli eventi generati dalla stessa esecuzione del `Send Signal` condividono lo stesso `createdAtMs`.

Questo è voluto: identifica la stessa emissione/raffica.

Conseguenza: eventuali dedupe non devono usare solo `createdAtMs`, altrimenti collassano tutta la raffica. Devono includere almeno il `sourceItem` o l'actor corrente.

## Dispatch del segnale

`Send Signal` invia l'evento tramite:

```java
WiredManager.dispatchEffectTriggeredEvent(event)
```

Se non siamo già dentro una chain wired, viene chiamato subito:

```java
handleEvent(event)
```

Se siamo già dentro una chain wired, l'evento viene messo nella coda deferred:

```text
DEFERRED_EFFECT_EVENTS
```

Gli eventi deferred vengono processati quando la profondità di handling torna al primo livello.

Questo evita esecuzioni ricorsive immediate dentro lo stesso stack.

Ordine:

```text
Send Signal crea eventi in ordine
  -> deferredEvents.addLast(...)
quando la chain corrente finisce
  -> deferredEvents.pollFirst()
  -> engine.handleEvent(...)
```

Quindi la raffica deve mantenere l'ordine di creazione.

## Receive Signal: configurazione salvata

`WiredTriggerReceiveSignal` salva:

```json
{
  "channel": 1503,
  "furniSource": 100,
  "itemIds": [1503]
}
```

### `channel`

Canale/corrispondenza antenna.

Nel caso normale, se il receiver usa antenne selezionate, `channel` viene impostato all'id della prima antenna selezionata.

### `furniSource`

Il Receive Signal supporta solo:

```text
100 = SOURCE_SELECTED
200 = SOURCE_SELECTOR
```

Qualsiasi altro valore viene normalizzato a:

```text
SOURCE_SELECTED
```

Nota: in `loadWiredData`, se `furniSource` non è `SOURCE_SELECTOR`, viene forzato a `SOURCE_SELECTED`.

### `itemIds`

Antenne selezionate manualmente nel receiver.

Sono valide solo se sono furni di tipo `antenna`.

## Receive Signal: matching

`WiredTriggerReceiveSignal.matches(triggerItem, event)` fa:

1. Se l'evento non è `SIGNAL_RECEIVED`, ritorna `false`.
2. Risolve le antenne del receiver:

```java
WiredTriggerSourceUtil.resolveItems(this, event, this.furniSource, this.items)
```

3. Filtra solo furni `antenna`.
4. Se ci sono antenne risolte, matcha se almeno una ha id uguale a:

```text
event.signalChannel
```

5. Se non ci sono antenne risolte, usa fallback:

```text
channel > 0 && event.signalChannel == channel
```

Quindi il receiver non guarda direttamente `sourceItem` per decidere se attivarsi. Guarda il canale antenna.

## Semantica di `SOURCE_SIGNAL`

Questa è la parte più importante.

Tutti i wired che vogliono leggere il contenuto del signal devono passare da:

```java
WiredSourceUtil.resolveItems(ctx, SOURCE_SIGNAL, ...)
WiredSourceUtil.resolveUsers(ctx, SOURCE_SIGNAL, ...)
```

oppure, per i trigger:

```java
WiredTriggerSourceUtil.resolveItems(trigger, event, SOURCE_SIGNAL, ...)
WiredTriggerSourceUtil.resolveUsers(trigger, event, SOURCE_SIGNAL, ...)
```

### Furni con `SOURCE_SIGNAL`

Se l'evento è `SIGNAL_RECEIVED`:

```text
signalPerFurni=false e signalSourceItems non vuota
  -> ritorna tutta signalSourceItems

altrimenti
  -> ritorna sourceItem come lista singola
```

Quindi:

```text
signalPerFurni=true
  -> SOURCE_SIGNAL = [sourceItem corrente]

signalPerFurni=false
  -> SOURCE_SIGNAL = [tutti i furni inoltrati]
```

### Utenti con `SOURCE_SIGNAL`

Se l'evento è `SIGNAL_RECEIVED`:

```text
signalPerUser=false e signalSourceUsers non vuota
  -> ritorna tutta signalSourceUsers

altrimenti
  -> ritorna actor come lista singola
```

Quindi:

```text
signalPerUser=true
  -> SOURCE_SIGNAL = [actor corrente]

signalPerUser=false
  -> SOURCE_SIGNAL = [tutti gli utenti inoltrati]
```

## Selector, condition ed effects nello stack Receive Signal

Ogni evento `SIGNAL_RECEIVED` viene processato dal `WiredEngine` come evento separato.

Per ogni evento:

1. Il dispatcher trova gli stack compatibili.
2. Per ogni stack crea un nuovo `WiredContext`.
3. Il context parte con:

```text
targets.users = actor, se presente
targets.items = triggerItem del Receive Signal
```

4. Vengono eseguiti i selector nello stack.
5. Vengono applicati gli extra filtro.
6. Vengono valutate le condition.
7. Se le condition passano, vengono eseguiti gli effect.

Importante:

```text
ctx.sourceItem() != ctx.triggerItem()
```

Nel Receive Signal:

- `ctx.triggerItem()` è il box `Receive Signal`;
- `ctx.sourceItem()` è il furni trasportato dal signal, oppure l'antenna nel caso di click antenna/manuale.

## Esempio completo: 4 furni, 1 occupato

Configurazione:

```text
Send Signal:
  furniForward = SOURCE_SELECTOR o SOURCE_SELECTED
  signalPerFurni = true
  antenna = 1503

Receive Signal:
  antenna = 1503

Selector Users Neighborhood:
  sourceType = SOURCE_FURNI_SIGNAL
  tileOffsets = [[0,0]]

Condition Selection Quantity:
  sourceGroup = users
  sourceType = SOURCE_SELECTOR
  comparison = equal
  quantity = 0

Effect Message:
  message = $(id)

Effect Teleport:
  furniSource = SOURCE_SIGNAL
  userSource = SOURCE_TRIGGER
```

Furni inoltrati:

```text
[1438, 1439, 1440, 1441]
```

Se `1441` ha un utente sopra:

```text
event sourceItem=1438
  selector trova 0 utenti
  condition PASS
  effects eseguono

event sourceItem=1439
  selector trova 0 utenti
  condition PASS
  effects eseguono

event sourceItem=1440
  selector trova 0 utenti
  condition PASS
  effects eseguono

event sourceItem=1441
  selector trova 1 utente
  condition FAIL
  effects non eseguono
```

Risultato:

```text
3 esecuzioni valide
1 esecuzione scartata dalla condition
```

Il Send Signal può comunque avere generato 4 eventi. La condition è nello stack receiver, quindi filtra l'esecuzione dello stack receiver, non impedisce al Send Signal di creare l'evento iniziale.

## Message e placeholder

Il messaggio wired usa `WiredTextPlaceholderUtil`.

Regola corretta:

```text
signalPerFurni=true
  -> placeholder furni SOURCE_SIGNAL usa il sourceItem corrente

signalPerFurni=false
  -> placeholder multiplo furni SOURCE_SIGNAL può usare tutta signalSourceItems

signalPerUser=true
  -> placeholder user SOURCE_SIGNAL usa actor corrente

signalPerUser=false
  -> placeholder multiplo user SOURCE_SIGNAL può usare tutta signalSourceUsers
```

Questo evita che un placeholder multiplo rompa la semantica del ciclo.

### Dedupe messaggi

Il `Show Message` ha una cache dedupe per non mandare duplicati identici.

La chiave deve includere dati che distinguono la raffica:

```text
room id
effect id
recipient id
createdAtMs
signalChannel
callStackDepth
sourceItem id
actor id
```

Se la chiave includesse solo `createdAtMs`, tutti i messaggi della stessa raffica verrebbero collassati in uno solo.

## Teleport

Il teleport è un caso speciale.

Gli altri effect possono eseguire una volta per ogni signal valido.

Il teleport, invece, non può teleportare lo stesso utente contemporaneamente su tre caselle diverse. Se tre eventi validi arrivano nello stesso tick, senza gestione speciale si schedulerebbero tre teleport e vincerebbe quello eseguito per ultimo.

Per questo `WiredEffectTeleport` raggruppa i teleport quando:

```text
furniSource == SOURCE_SIGNAL
eventType == SIGNAL_RECEIVED
signalPerFurni == true
signalFurniCount > 1
```

In quel caso:

1. Ogni esecuzione valida aggiunge la tile candidata.
2. Dopo un micro-delay interno di batching, viene scelta una tile random tra le candidate.
3. Viene eseguito un solo teleport reale.

Questo non cambia la semantica del Receive Signal. Cambia solo il modo in cui il teleport risolve il conflitto fisico di più destinazioni per lo stesso utente.

## Delay e cooldown

Regola progetto:

```text
nessun wired deve imporre cooldown implicito
```

Il tempo di attesa deve venire solo da:

- slider delay del singolo wired effect;
- trigger time/timer;
- eventuale comportamento fisico necessario, come animazione teleport.

I cooldown wired base sono impostati a `0`.

Il `Send Signal` ha `requiredCooldown() = 0`.

## Cache wired

Ci sono due categorie di cache.

### Cache stack/index

`RoomWiredStackIndex` cache-a gli stack per:

```text
room id
lifecycleGeneration
wiredCacheGeneration
event type
```

Quando una room viene invalidata:

```java
WiredManager.invalidateRoom(room)
```

succede:

1. `room.advanceWiredCacheGeneration()`
2. `stackIndex.invalidateAll(room)`
3. `engine.clearRoomExecutionCaches(roomId)`

Quindi la cache stack non dovrebbe mantenere vecchi stack dopo salvataggi/spostamenti wired.

### Source stack cache

`WiredStackRepository` cache-a lookup specifici per source item con:

```text
room id
lifecycleGeneration
wiredGeneration
event type
sourceItemId
```

Viene pulita tramite:

```java
clearRoomSourceStackCache(roomId)
```

### Cache runtime

`clearRoomExecutionCaches(roomId)` pulisce:

- recursion depth;
- rate limiters;
- source stack cache;
- diagnostics;
- room ban;
- unseen cache;
- effect cooldown service.

Questo serve a non lasciare stato runtime sporco dopo modifiche wired.

## Cosa deve essere vero per tutti i wired

Per restare allineati alla semantica dei signal:

1. Un wired non deve leggere direttamente `event.getSignalSourceItems()` come target principale se `signalPerFurni=true`.
2. Un wired non deve leggere direttamente `event.getSignalSourceUsers()` come target principale se `signalPerUser=true`.
3. Deve usare `WiredSourceUtil` o `WiredTriggerSourceUtil`.
4. Se ha bisogno della lista completa, deve farlo solo quando il signal non è per-item/per-user, oppure deve trattarla esplicitamente come metadato.
5. Dedupe, cooldown o cache interne devono distinguere almeno `sourceItem` e/o `actor` durante una raffica.

## Differenza tra lista completa e item corrente

Dentro `WiredEvent` ci sono entrambi:

```text
sourceItem
signalSourceItems
```

Significato:

```text
sourceItem
  = item corrente di questa esecuzione

signalSourceItems
  = lista completa di item inoltrati dal Send Signal originale
```

Con `signalPerFurni=true`, i wired devono usare `sourceItem`.

Con `signalPerFurni=false`, i wired possono usare `signalSourceItems` come lista target.

Stessa regola per:

```text
actor
signalSourceUsers
```

## Esempi rapidi

### Send non splittato

```text
forwardItemIds = [1,2,3]
signalPerFurni = false

event unico:
  sourceItem = 1
  signalSourceItems = [1,2,3]

SOURCE_SIGNAL furni = [1,2,3]
```

### Send splittato per furni

```text
forwardItemIds = [1,2,3]
signalPerFurni = true

event 1:
  sourceItem = 1
  signalSourceItems = [1,2,3]
  SOURCE_SIGNAL furni = [1]

event 2:
  sourceItem = 2
  signalSourceItems = [1,2,3]
  SOURCE_SIGNAL furni = [2]

event 3:
  sourceItem = 3
  signalSourceItems = [1,2,3]
  SOURCE_SIGNAL furni = [3]
```

### Send splittato per utenti

```text
forwardUsers = [10,11]
signalPerUser = true

event 1:
  actor = 10
  signalSourceUsers = [10,11]
  SOURCE_SIGNAL users = [10]

event 2:
  actor = 11
  signalSourceUsers = [10,11]
  SOURCE_SIGNAL users = [11]
```

## Debug consigliato

Per verificare una raffica, loggare sempre:

```text
event.type
event.signalChannel
event.signalPerFurni
event.signalPerUser
event.signalFurniCount
event.signalUserCount
event.sourceItem.id
event.actor.id
ctx.targets().items()
ctx.targets().users()
```

Nel `Send Signal`, i log utili sono:

```text
forwardedFurni
selectorTargets
antenna
sourceItem
user
depth
```

Nel receiver, il test più importante è:

```text
per ogni SIGNAL_RECEIVED sourceItem=X,
SOURCE_SIGNAL deve risolvere [X]
```

## Regola finale

Il comportamento corretto è:

```text
Send Signal con signalPerFurni=true
  prende ogni item inoltrato
  crea una singola esecuzione/evento con quell'item come sourceItem
  il Receive Signal processa ogni evento separatamente
  selector/condition/effects vedono SOURCE_SIGNAL come item corrente
```

Se un wired vede tutta la lista mentre `signalPerFurni=true`, quel wired sta bypassando la semantica corretta.

Se un wired vede sempre lo stesso item, controllare:

1. che il Send Signal stia generando più eventi;
2. che `sourceItem` cambi per ogni evento;
3. che il wired consumer usi `WiredSourceUtil`;
4. che non ci siano dedupe/cooldown/cache interne che collassano la raffica;
5. che il risultato visuale non sia un caso speciale come teleport multiplo sullo stesso utente.
