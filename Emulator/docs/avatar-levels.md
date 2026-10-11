# Livelli avatar e Pantheon

Il livello avatar va da 1 a 30. Le soglie sono quelle documentate per Habbo:
0, 30, 35, 45, 60, 75, 95, 115, 140, 165, 190, 215, 240, 270, 300,
340, 390, 450, 520, 600, 700, 800, 900, 1100, 1300, 1600, 2000, 2500, 3200, 4000.

Il server calcola nell'ordine:

1. Livelli dei traguardi completati, incluse le definizioni archiviate ma escluse
   quelle rimosse o disabilitate. Il vecchio achievement_score non viene usato.
2. Distintivi unici, esclusi i badge ACH_ dei traguardi.
3. Tempo dei visitatori nelle proprie stanze, ponderato per il loro livello.
   Un punto corrisponde a un milione di minuti ponderati. Il proprietario non
   conta; il suo account può essere offline. Le frazioni vengono conservate.
4. Tipologie uniche di furni posseduti, anche nelle stanze. Duplicati, post-it,
   foto della camera e prestiti Builders Club non contano.

Ogni categoria dopo i traguardi contribuisce al massimo quanto la somma dei
contributi delle categorie precedenti. Il client mostra in verde le categorie
al limite, in giallo quelle ancora incrementabili e tra parentesi i punti
effettivi che superano il limite, come richiesto per questa implementazione.

Esempio dello screenshot: 1046 + 484 + 1 + min(1681,1531) = 3062.
Il livello è 28; il prossimo richiede 3200 punti e dà 270 Duckets.

Livello massimo raggiunto e massimo punteggio sono persistenti: vendere e
ricomprare gli stessi furni non permette di ricevere di nuovo premi o progressi
Pantheon già ottenuti. Pantheon usa max(0, massimo punteggio avatar - 4000).
4000 -> 4002 punti porta quindi Pantheon da 0 a 2. I livelli guadagnati da
Pantheon stesso contano poi normalmente fra i traguardi.

## Ricompense

I premi sono accodati nella voce Livelli della finestra Guadagni e si riscuotono
con il percorso già presente nel client. Il registro economico usa un codice
univoco per utente e livello, anche se l'emulatore si interrompe fra il premio
e il salvataggio del livello premiato. La vecchia ricompensa configurabile per
cittadinanza/aiutanti non viene accreditata una seconda volta.

Duckets per i livelli 1–30:
0, 40, 40, 45, 45, 50, 100, 110, 110, 200, 120, 120, 120, 130, 130,
130, 140, 140, 140, 300, 225, 225, 225, 240, 240, 240, 270, 270, 270, 300.

La tabella aggiornata della community lascia vuoti i livelli 18, 19, 20, 24,
26 e 30. Con l'autorizzazione dell'utente, sono stati completati con 140, 140,
300, 240, 240 e 300 Duckets seguendo la progressione delle ricompense elencate.
Questi sei importi sono una scelta di implementazione, non una conferma ufficiale.

Fonti: [regole ufficiali](https://help.habbo.com/hc/en-us/articles/360014395240-Level-Progression),
[soglie e tabella aggiornata](https://habboxwiki.com/Levels),
[tabella storica](https://habbox.com/guides/habbo2020/levels).

## Installazione e compatibilità

Ricompilare emulatore e client con il renderer aggiornato. La migrazione Flyway
V20261011120000 crea lo stato dei livelli. Conserva i vecchi contatori Pantheon
e gli incrementi in coda in users_pantheon_legacy_progress prima di azzerarli:
erano alimentati dal punteggio Flash e non possono essere convertiti direttamente.
I badge già assegnati restano di proprietà degli utenti.

I profili usano il campo accountLevel già esistente; gli altri campi del profilo
rimangono presenti per i vecchi client. I nuovi pacchetti opzionali hanno codice
9510 in entrambe le direzioni. Il pannello chiede i dati all'apertura e il server
li aggiorna al primo ciclo di consegna (ogni secondo). La migrazione
V20261011150000 assicura tabelle InnoDB per i dati coinvolti e mantiene punteggio corrente, massimo e Pantheon nella stessa
transazione SQL di badge, furni e traguardi, anche per utenti offline e per
assegnazioni provenienti da RCON, housekeeping o integrazioni già esistenti.
Un aumento seguito da una rimozione fra due cicli rimane nel massimo salvato.
I contatori delle tipologie uniche e dei badge sono aggiornati nella stessa transazione delle righe originali, con un ordine uniforme dei blocchi per evitare conflitti fra assegnazioni simultanee. La coda persistente viene ripresa dopo un riavvio. Il client online riceve i
conteggi dal database, senza affidarsi a inventari in memoria potenzialmente
non aggiornati. Il ciclo elabora fino a 200 utenti con aggiornamenti in attesa per passaggio;
all'apertura del pannello il proprio stato viene letto direttamente.

I progressi dei traguardi online vengono salvati prima di pubblicarli. Il
salvataggio periodico non può sovrascriverli con un valore vecchio. Le code
ricevute offline vengono contate subito, poi trasferite nei progressi e rimosse
in una singola transazione al login, evitando perdite o applicazioni doppie.
Pantheon è materializzato nei traguardi anche offline; il contatore canonico
è già salvato con il massimo avatar. I livelli dello stesso Pantheon vengono
ricontati fino a raggiungere un valore stabile.

Gli scambi registrano solo la collezione finale, evitando punti dal passaggio
intermedio ricevi/cedi. I nuovi prestiti Builders Club sono esclusi già al loro
inserimento, prima del posizionamento e della registrazione nella tabella BC.

Il tempo di ospitalità viene salvato a ogni ciclo della stanza e all'uscita,
tramite checkpoint cumulativi identificati per sessione. Il checkpoint e
l'incremento del proprietario vengono confermati insieme: ripetere il tentativo
anche dopo una risposta COMMIT persa non duplica il tempo. L'ultimo intervallo
non ancora campionato al momento del crash non è tempo già confermato. In caso
di indisponibilità del database, il tempo non confermato resta in memoria per
il tentativo seguente; un crash durante tale indisponibilità può perderlo.
Il conteggio storico dell'ospitalità parte da zero perché il vecchio sistema
non registrava il livello dei visitatori. La durata dei dati confermati dipende
anche dalla configurazione di durabilità e dai backup del database.

## Verifiche

I test di dominio coprono soglie, limiti delle categorie, massimo raggiunto,
Pantheon e premi ripetuti. I test MariaDB di durabilità coprono assegnazioni
offline, duplicati, esclusioni, rollback, trasferimenti, code offline, assegnazioni simultanee, conversione delle tabelle MyISAM,
ricaricamento delle definizioni e recupero dei contatori da una nuova connessione. Usano soltanto oggetti con prefisso
`duck_avcheck_`, poi li rimuovono; non modificano gli utenti dell'hotel.

## Correzione del livello 30 e riconciliazione

La migrazione V20261011170000 corregge la risoluzione della colonna `score`
nel confronto SQL delle soglie: la versione precedente confrontava la soglia
con sé stessa e selezionava sempre 30. Il livello viene ora derivato dal
massimo punteggio persistente: massimo 40 -> livello 3, anche con punteggio
attuale 28. Un livello 30 ottenuto realmente a 4000 punti viene conservato.
La migrazione conserva gli stati corretti in `users_avatar_level_repairs`.

I premi prematuri non ancora riscossi vengono conservati in
`users_avatar_deferred_rewards`, e tornano nei Guadagni al raggiungimento del
livello corrispondente. Ripristino e conferma del premio sono atomici e usano
lo stesso identificatore del registro economico, senza duplicare premi già
riscossi o modificare i saldi. Al livello 30 il pannello omette completamente
la riga della prossima ricompensa.

Ogni modifica persistente di badge, furni o traguardi richiede un ricalcolo
completo delle categorie dai dati originali al successivo passaggio del worker.
Le modifiche ravvicinate dello stesso utente vengono accorpate. I contatori
transazionali continuano a salvare subito i massimi e i progressi Pantheon,
anche offline; la riconciliazione ricostruisce le proiezioni e ripara le derive.
Il pannello e il login richiedono anch'essi un ricalcolo completo. Non c'è
alcun controllo periodico degli account senza eventi. Qualsiasi evento di
ospitalità, furni, distintivi o traguardi richiede il ricalcolo completo di tutte
le categorie: anche l'ospitalità fa rileggere inventario, badge e traguardi.
I checkpoint ricostruiscono anche l'ospitalità,
conservando separatamente il tempo guadagnato prima della loro introduzione.

I dati originali vengono letti senza bloccare le righe dopo aver acquisito
il blocco dell'utente in una nuova transazione: gli eventi concorrenti non
possono confermare modifiche parziali durante il ricalcolo. Nessun inventario
in memoria viene usato come fonte autorevole. I test riproducono il salto
errato al 30 e verificano correzione al 3, mantenimento dei livelli legittimi,
premi differiti, riparazione delle proiezioni dopo eventi offline e assenza di
ricontrolli periodici. Anche gli eventi di ospitalità rileggono tutte le
categorie e riparano eventuali discrepanze nei contatori delle collezioni.

## Progressi dei singoli traguardi e barra toolbar

La migrazione V20261011180000 conserva il massimo progresso originale e il
numero di righe in `users_achievement_progress_repairs`, unifica i duplicati
in una transazione e garantisce una sola riga per utente e traguardo. Gli
incrementi successivi aggiornano quella riga: ogni nuovo livello completato
aggiunge un punto alla categoria Traguardi. Il limite delle altre categorie
può aumentare di conseguenza. Login e riconciliazione leggono sempre il massimo
progresso, anche se devono incontrare dati storici duplicati.

La toolbar termina con l'avatar e poi la barra Livelli. Toolbar e pannello
mostrano la stessa percentuale: punteggio attuale diviso per la soglia del
prossimo livello, arrotondato per difetto. Questo permette alla barra di
avanzare anche quando il livello massimo mantenuto è superiore al punteggio
attuale: 26/45 -> 57%, 28/45 -> 62%. Al livello 30 la barra mostra 100%.
