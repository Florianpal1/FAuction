# Plan - Interface d'importation de donnees depuis d'autres plugins d'HDV

**Objectif :** permettre d'importer dans FAuction les donnees d'autres plugins
d'hotel des ventes (ventes en cours, objets expires, historique, argent en
attente) au travers d'une API publique. Ecrire un "module d'import" pour un
plugin X doit se resumer a : lire la source de X et convertir chaque ligne en
un objet simple. Toute la partie delicate (validation, ecriture en base,
doublons, threads, rapport, commande) reste dans FAuction et n'est ecrite
qu'une seule fois.

**Perimetre :** bases SQL partagees uniquement (MySQL, MariaDB, PostgreSQL).
Le mode SQLite est **hors perimetre** : la commande d'import refuse de se
lancer si `database.yml` est en SQLite (message explicite).

Base de depart : `master` (323efd8), version 2.3.0.

---

## 1. Constats sur le code existant

### 1.1 Persistance
- JDBC brut via HikariCP (`DatabaseManager`). Hibernate est dans le pom mais
  n'est pas utilise : on ne s'appuie **pas** dessus.
- Les classes `queries/*Queries` ouvrent une connexion par appel, sans
  transaction ni batch, et loguent les `SQLException` au lieu de les lever.
  -> Inadapte a l'import de milliers de lignes : il faut des methodes batch
  transactionnelles dediees.
- La syntaxe d'auto-increment varie selon `SQLType` (cf. `AuctionQueries`) :
  toute nouvelle table doit suivre le meme mecanisme.

### 1.2 Cache
- En MySQL/MariaDB/PostgreSQL, la base est la source de verite ; les caches
  par joueur des `*CommandManager` sont rafraichis periodiquement par
  `CacheSchedule`. Il suffira de forcer un `updateCache()` en fin d'import.
- Pourquoi exclure SQLite : en SQLite le cache memoire fait foi et la table
  `auctions` est reecrite depuis ce cache a l'arret (`FAuction.onDisable`).
  Un import ecrit en base serait perdu. D'ou le refus explicite plutot qu'un
  support partiel.

### 1.3 Modele de donnees (`objects/`)
- `Auction` (id, playerUUID, playerName, price, itemStack, date), reutilise
  pour les expires.
- `Historic extends Auction` (+ playerBuyerUUID, playerBuyerName, buyDate).
  `HistoricCommandManager.addHistoric()` date l'achat a "maintenant" : il faut
  une variante qui accepte la date d'origine.
- `CurrencyPending` (playerUUID, `CurrencyType` VAULT/EXPERIENCE/LEVEL, amount)
  pour l'argent du aux vendeurs hors ligne.
- `playerName` est un `VARCHAR(36)` ; `item` est un BLOB (`SerializationUtil`,
  format Paper ou Bukkit).
- Les encheres (`/ah bid`) sont sur la branche `feature/bid`, pas sur master :
  hors perimetre, mais l'API doit pouvoir les accueillir plus tard (voir 2.6).

### 1.4 Commandes / permissions / langues
- Incendo Cloud 2 par annotations dans `AuctionCommand`, chemins `${root}` /
  `${admin}`. Les commandes admin actuelles prennent un `Player` : la commande
  d'import doit accepter un `CommandSender` (console indispensable pour un
  import sur un serveur en maintenance).
- Permissions `fauction.admin.<verbe>`, a declarer dans `plugin.yml` (enfant de
  `fauction.admin` + entree propre).
- Messages : enum `MessageKeys` + `lang_en/fr/ru/zhcn.yml`, verifies par
  `MessageKeysTest`.

### 1.5 Points d'extension existants
- Uniquement `FAuction.getApi()` (statique) et des events Bukkit. Pas de package
  api, rien dans le `ServicesManager`. Ce plan cree le premier vrai contrat
  public : il doit etre petit, stable et documente.

---

## 2. Architecture proposee

### 2.1 Vue d'ensemble

```
Plugin source (fichiers / BDD / API)
         |
         v
[ Module d'import ]   <- ecrit par le dev tiers : implemente DataImporter
         |               et pousse des Imported* dans un ImportSink
         v
[ ImportManager ]     validation -> deduplication -> buffer
         |            -> ecriture batch transactionnelle -> rapport
         v
auctions / expires / fa_auctions_historic / fa_currency_pending (+ fa_import_log)
```

Modele "push" : le module lit sa source a son rythme et appelle
`sink.accept(...)` ligne par ligne. Il n'a ni a tout charger en memoire, ni a
connaitre la base de FAuction, ni a gerer les threads.

### 2.2 Package public : `fr.florianpal.fauction.api.importer`
Seul ce package est garanti stable (documente "API" dans la Javadoc). Tout le
reste de FAuction reste interne.

**a) `DataImporter`** (interface a implementer)
```java
String id();                          // "zauctionhouse", unique, [a-z0-9_-]
String displayName();
Set<ImportDataType> supportedTypes();
ImportAvailability checkAvailability(ImportContext ctx); // source trouvee ?
void read(ImportContext ctx, ImportSink sink) throws Exception;
default int requiredApiVersion() { return 1; }
```

**b) `ImportDataType`** (enum) : `AUCTION`, `EXPIRED`, `HISTORIC`,
`PENDING_CURRENCY` (`BID` ajoute plus tard, voir 2.6).

**c) Objets importes** : records Java 21 immuables, avec builder pour les
champs optionnels.
```java
ImportedAuction(String sourceId, UUID sellerUuid, String sellerName,
                ImportedItem item, double price, Instant listedAt)
ImportedExpired(String sourceId, UUID ownerUuid, String ownerName,
                ImportedItem item, double price, Instant listedAt)
ImportedHistoric(String sourceId, UUID sellerUuid, String sellerName,
                 UUID buyerUuid, String buyerName, ImportedItem item,
                 double price, @Nullable Instant listedAt,
                 @Nullable Instant soldAt)
ImportedPendingCurrency(String sourceId, UUID playerUuid,
                        String sourceCurrency, double amount, String reason)
```
- `sourceId` = identifiant stable de la ligne dans la source (id SQL, cle
  YAML, UUID...). Obligatoire, sert a l'idempotence (2.4).
- Noms de joueurs facultatifs partout : si absents (NexusAuctionHouse ne les
  stocke pas) ou douteux (display names colores d'Auction-House), FAuction
  les resout via l'UUID (`OfflinePlayer`), retire les codes couleur et
  tronque a 36 caracteres.
- `ImportedHistoric` : `listedAt` / `soldAt` facultatifs car plusieurs sources
  ne stockent pas la date de vente. Repli : `soldAt` -> `listedAt` -> date de
  l'import ; `buyDate` FAuction est deja nullable.
- `ImportedPendingCurrency` : la devise est le nom *cote source* ("Vault",
  "CoinsEngine-coins"...). La conversion vers le `CurrencyType` de FAuction est
  faite par FAuction via une table de correspondance (2.5), jamais par le
  module. `reason` (ex. "vente non encaissee", "remboursement d'enchere") va
  dans le rapport.
- `ImportedItem` : l'objet peut etre fourni sous plusieurs formes, la
  conversion etant faite par FAuction :
  - `ImportedItem.of(ItemStack)`
  - `ofPaperBytes(byte[])` / `ofPaperBase64(String)`
  - `ofBukkitBase64(String)` : `BukkitObjectOutputStream` en base64, decode
    avec le decodeur MIME (tolere les retours a la ligne des anciennes donnees)
    -> NexusAuctionHouse et Auction-House
  - `ofVanillaNbtGzip(byte[])` : NBT vanilla compresse GZIP avec `DataVersion`
    (format AxAPI) -> AxAuctions. Probablement lisible directement par
    `ItemStack.deserializeBytes` de Paper (meme principe : NBT compresse +
    DataVersion + DataFixer) ; **a verifier sur un vrai dump**, sinon
    decompression + `Bukkit.getUnsafe().deserializeItem(...)`.

**d) `ImportSink`** (fourni par FAuction)
```java
void accept(ImportedAuction a);   // + une surcharge par type
void skip(String sourceId, String reason); // ligne illisible cote source
boolean isCancelled();            // a tester dans les boucles du module
```

**e) `ImportContext`** (fourni par FAuction)
```java
Logger logger();                  // prefixe [Import:<id>]
Map<String, String> options();    // options passees en commande
boolean dryRun();
File pluginsFolder();
Optional<Plugin> sourcePlugin(String name);
ImportHelpers helpers();
```

**f) `ImportHelpers`** : outils pour que les modules restent courts
- `Connection openJdbc(String url, String user, String password)` (lecture de
  la base du plugin source, y compris un fichier SQLite *source* : seul le
  stockage de FAuction est restreint au SQL partage)
- `File sourceDataFolder(String pluginName)` : `plugins/<nom>/`, que le
  plugin source soit charge ou non (cas nominal : il est desactive)
- `YamlConfiguration loadYaml(File f)` (sert aussi a lire le `config.yml` du
  plugin source : durees d'expiration, taxes, parametres de base)
- `JsonElement loadJson(File f)` / `<T> T loadJson(File f, Type t)` via le Gson
  embarque par Paper, fichier vide = resultat vide (pas d'exception)
- `String resolvePlayerName(UUID uuid)` (OfflinePlayer, avec fallback)
- `Instant fromEpochMillis(long)` / `fromEpochSeconds(long)`
- `<T> T callSync(Callable<T>)` via FoliaLib si le module a besoin de l'API
  Bukkit sur le thread principal

**g) Classes de base facultatives** (le gros du gain de simplicite)
- `AbstractSqlImporter` : le module fournit la connexion, une requete SQL par
  type et un `RowMapper<Imported*>`. La boucle, la fermeture des ressources,
  `isCancelled()` et les erreurs par ligne (skip au lieu d'arret) sont gerees
  par la classe de base.
- `AbstractYamlImporter` : idem avec le fichier, la section racine et un
  mapper `ConfigurationSection -> Imported*`.

Un module simple = une classe d'environ 50 lignes.

**h) Enregistrement**
- `ImporterRegistry` (interface) : `register(Plugin owner, DataImporter)`,
  `unregister(...)`, `list()`, `find(id)`.
- Accessible via le `ServicesManager` Bukkit
  (`getServicesManager().load(ImporterRegistry.class)`) ou via
  `FAuction.getApi().getImporterRegistry()`.
- Desenregistrement automatique sur `PluginDisableEvent` du proprietaire.
- Refus si l'id est deja pris ou si `requiredApiVersion()` depasse
  `ImporterApi.API_VERSION` (= 1).

**h bis) `RecordingImportSink`** (package `api.importer.testing`) : sink
en memoire qui enregistre tout ce qu'un module emet, pour que les
developpeurs de modules testent leur conversion sans FAuction ni base
(utilise aussi par nos propres tests, section 8.5).

**i) Events** (`api.importer.event`) : `ImportStartEvent` (Cancellable),
`ImportFinishEvent` (porte l'`ImportReport`). Pas d'`AuctionAddEvent` par
ligne importee (documente).

### 2.3 Moteur interne : `managers/importer/ImportManager`
- Verifie le type de base au lancement : refus si SQLite.
- Un seul import a la fois (verrou), lance en async via `newChain()`.
- `read()` s'execute sur un thread async ; le sink empile dans un buffer par
  type, vide par paquets de N lignes (config, defaut 500).
- Pipeline par ligne :
  1. validation (2.5) -> rejet compte avec sa raison
  2. deduplication (2.4) -> deja importe = ignore
  3. conversion `ImportedItem -> ItemStack -> bytes` (`SerializationUtil`,
     format courant du serveur)
  4. ajout au buffer ; au flush : ecriture batch + journal dans une seule
     transaction
- Nouvelles methodes batch transactionnelles :
  `AuctionQueries.addAuctionsBatch(...)`, `ExpireQueries.addExpiresBatch(...)`,
  `HistoricQueries.addHistoricsBatch(...)` (avec `buyDate` d'origine),
  `CurrencyPendingQueries.addBatch(...)`. Elles recoivent une `Connection`
  deja en transaction, pour partager le commit avec `fa_import_log`.
- Un paquet en echec est rollbacke et compte en erreur ; les suivants
  continuent.
- Fin : `updateCache()` des `*CommandManager`, rapport final,
  `ImportFinishEvent`.
- Annulation : `/ah admin import cancel` -> `isCancelled() = true`, le paquet
  en cours est termine (pas d'etat a moitie ecrit).

### 2.4 Idempotence (relancer un import ne doit jamais dupliquer)
- Nouvelle table `fa_import_log` (importerId, dataType, sourceId, targetId,
  importedAt), contrainte `UNIQUE(importerId, dataType, sourceId)`, creee par
  une nouvelle `ImportLogQueries` enregistree comme les autres tables (DDL par
  `SQLType`).
- Ecrite dans la **meme transaction** que le paquet de donnees : apres un crash
  au milieu, une relance reprend exactement ou on s'etait arrete.
- Option `--force-reimport` pour ignorer le journal.

### 2.5 Validation (cote FAuction, jamais deleguee au module)
- UUID non null ; nom non vide, tronque a 36 caracteres (resolu via
  `ImportHelpers` si absent).
- Objet non null, non AIR, quantite > 0, deserialisable.
- Prix fini et > 0 (ni NaN, ni infini, ni negatif).
- Dates non nulles ; une date future est ramenee a maintenant.
- Les limites par rang / prix min-max de `LimitationManager` ne sont **pas**
  appliquees par defaut (on reprend un existant) ; option `--apply-limits`.
- Une vente deja expiree au regard de `expiration.*` reste dans `auctions`
  (`ExpireSchedule` la basculera) ou option `--expired-to-expires`.
- **Devises** : `ImportedPendingCurrency.sourceCurrency` est convertie via une
  table de correspondance (`import.currency-map` dans `config.yml`, defaut
  `Vault: VAULT`, `Experience: EXPERIENCE`, `Level: LEVEL`). Une devise non
  mappee = ligne rejetee avec sa raison (jamais convertie au hasard).
  Les ventes en cours dans une autre devise que celle de FAuction
  (`currencyUse`) sont rejetees par defaut ; option `--other-currency=expire`
  pour rendre l'objet au vendeur (table `expires`) au lieu de le perdre.

### 2.5 bis Encheres cote source (FAuction master n'en a pas)
Auction-House (ElaineQheart) gere des encheres avec argent en sequestre.
Tant que `feature/bid` n'est pas fusionnee, le module convertit chaque etat
en donnees FAuction existantes, sans jamais perdre d'objet ni d'argent :

| Etat source | Conversion |
|---|---|
| Enchere en cours, sans offre | objet rendu au vendeur (`expires`) |
| Enchere en cours, avec offres | objet rendu au vendeur (`expires`) + chaque encherisseur rembourse de sa derniere offre (`fa_currency_pending`) |
| Enchere terminee, gagnant pas encore servi | objet au gagnant (`expires` du gagnant) |
| Enchere terminee, vendeur pas encore paye | montant du au vendeur (`fa_currency_pending`) |
| Enchere terminee, perdants pas encore rembourses | remboursement de chacun (`fa_currency_pending`) |

Option `--bids=skip` pour ne rien importer de ces lignes (elles sont alors
listees dans le rapport). Quand `feature/bid` sera fusionnee, un type `BID`
permettra d'importer les encheres en cours telles quelles (2.6).

### 2.6 Evolutivite
- Ajouter un type (ex. `BID` apres fusion de `feature/bid`) = nouvelle valeur
  d'enum + nouveau record + nouvelle methode `default` dans `ImportSink` : les
  modules existants compilent toujours. `API_VERSION` passe a 2.
- Ne jamais modifier la signature des records existants.

---

## 3. Commande, permissions, config, langues

**Commandes** (`CommandSender`, utilisables en console) :

| Commande | Role |
|---|---|
| `/ah admin import list` | modules enregistres, disponibilite, types supportes |
| `/ah admin import run <id> [options]` | lance l'import |
| `/ah admin import status` | progression (lues / importees / rejets) |
| `/ah admin import cancel` | annulation propre |

Options de `run` : `--types auction,expired,...`, `--dry-run`,
`--apply-limits`, `--expired-to-expires`, `--force-reimport`,
`--other-currency=reject|expire`, `--bids=convert|skip`,
`--opt cle=valeur` (options propres au module, ex. `--opt mode=runtime`).

- Parser Cloud `fauction:importer` avec suggestions des ids enregistres (meme
  principe que `fauction:migrate_version`).
- Confirmation obligatoire pour un `run` reel : seconde saisie
  `/ah admin import run <id> confirm` sous 30 s.
- Avertissement si le plugin source est encore actif (les objets restent
  vendables des deux cotes -> duplication). Recommandation documentee :
  serveur en maintenance, plugin source desactive, sauvegarde de la base faite
  par l'admin avant l'import.

**Permission** : `fauction.admin.import` (`plugin.yml` : enfant de
`fauction.admin` + entree propre, default op).

**config.yml** (version 12 -> 13, BoostedYAML gere l'ajout) :
```yaml
import:
  batch-size: 500
  progress-interval-seconds: 10
  currency-map:          # devise source -> CurrencyType FAuction
    Vault: VAULT
    Experience: EXPERIENCE
    Level: LEVEL
```

**Langues** : nouvelles `MessageKeys` `IMPORT_*` (+ `*_help_description`) dans
les 4 fichiers lang, dont le message de refus en SQLite ; `MessageKeysTest`
les verifiera automatiquement.

**Rapport** : resume en chat + fichier
`plugins/FAuction/imports/<id>-<date>.log` listant chaque ligne rejetee
(sourceId + raison).

---

## 4. Distribution de l'API pour les developpeurs de modules

Recommandation : garder un seul module Maven dans un premier temps, le package
`api.importer` etant la frontiere. Le dev tiers depend du jar FAuction en
scope `provided` (le depot Maven GitHub Packages deja documente sur la page
wiki `API.md`) et ajoute `depend: [FAuction]` dans son `plugin.yml`.
Extraction d'un module `fauction-api` possible plus tard (hors plan).

Documentation : le wiki `../FAuction.wiki` (voir section 7) + Javadoc complete
du package `api.importer` + un paragraphe dans `README.md` renvoyant au wiki
(en precisant : MySQL/MariaDB/PostgreSQL uniquement).

Modules livres avec le projet : voir section 5. Auction-House (ElaineQheart)
sert de module de reference dans la documentation (le plus simple : un seul
fichier JSON, des identifiants UUID stables).

---

## 5. Modules d'import prevus

Les trois plugins ont ete analyses (sources GitHub au 2026-10-10 pour les deux
premiers ; pour AxAuctions, code ferme : stub d'API publique, documentation
officielle et traces d'erreurs des tickets). Aucune ligne de code de ces
plugins n'est reprise : seul leur **format de donnees** est lu.

Regle commune aux trois : **importer avec le plugin source desactive** (ou le
serveur arrete pour lire ses fichiers). Les trois gardent leurs donnees en
memoire et reecrivent leurs fichiers / leur base : un import "a chaud" lirait
des donnees perimees, et la source resterait capable de rendre les memes
objets ou le meme argent. `checkAvailability()` refuse si le plugin source est
active (sauf mode `runtime` d'AxAuctions).

### 5.1 Auction-House (ElaineQheart) - `id: auctionhouse-elaine`
- Version analysee : 1.5.6 (commit 7a2da1c), Spigot 1.21, Java 21, GPL-3.0.
- **Source** : un seul fichier `plugins/AuctionHouse/data/notes.json`
  (ancien emplacement : `plugins/AuctionHouse/notes.json`), tableau JSON
  d'`ItemNote` ecrit par Gson. Pas de SQL (le code Redis est mort).
- **Champs utiles** : `noteID` (UUID), `playerUUID`, `playerName` (display
  name), `buyerUUID` / `buyerName`, `price` (double, prix total de la pile),
  `dateCreated` (Date Gson, format dependant de la locale), `itemData`,
  `isSold`, `partiallySoldAmountLeft`, `adminMessage`, `auctionTime`
  (secondes), `isBIDAuction`, `bidHistory[]` (player, playerName, date, bid),
  `claimedPlayers[]`.
- **sourceId** : `noteID` ; `noteID:<uuidJoueur>` pour les remboursements et
  objets dus par joueur.
- **Objets** : `ImportedItem.ofBukkitBase64(itemData)` (decodeur MIME).
- **Expiration** (non stockee) : `dateCreated + (auctionTime +
  auction-setup-time) s`, avec `auction-setup-time` lu dans le `config.yml`
  de la source (defaut 30). `auctionTime == -1` = expire par un admin ;
  `auctionTime == 0` = donnee ancienne, recalculee avec `bin-auction-duration`.
- **Conversion** :

| Etat de la note | Import FAuction |
|---|---|
| Vente directe active (non expiree, non vendue ou partiellement vendue) | `AUCTION` (prix au prorata de la quantite restante si partielle) |
| Vente directe expiree non vendue, ou expiree par un admin | `EXPIRED` pour le vendeur |
| Supprimee par un admin (`itemData` remplace par l'objet "terre" configure) | ignoree, raison dans le rapport (l'objet d'origine n'existe plus) |
| Vente directe vendue, non encaissee (`isSold`) | `PENDING_CURRENCY` vendeur = prix vendu, taxe `tax` de la source deduite (option `--opt apply-source-tax=false`) + `HISTORIC` sans date de vente |
| Encheres | tableau de la section 2.5 bis |

- **Pieges** : dates Gson a parser avec Gson (jamais a la main ; espace
  insecable U+202F avant AM/PM depuis le JDK 20) ; champs absents dans les
  anciennes donnees (`buyerUUID`, `bidHistory`, `claimedPlayers`) ; pas
  d'historique structure (les `logs/*.log` texte, sans UUID, ne sont pas
  importes) ; un seul acheteur memorise en cas de ventes partielles.

### 5.2 NexusAuctionHouse - `id: nexusauctionhouse`
- Version analysee : 2.4.7 (commit c430359), Spigot 1.18 / Java 25, licence
  "MIT No-Resale" (on ne lit que le format).
- **Source** : JSON uniquement, dans `plugins/NexusAuctionHouse/data/` :
  - `bins.json` : `{"money": ..., "bins": [{"id", "seller", "item", "price",
    "expiry", "buyer"}]}` ; `money` est une statistique globale, **ignoree**.
  - `expired-retrieve.json` : `{"players": [{"uuid", "items": [base64...]}]}`,
    le "coffre" d'objets a recuperer par joueur.
  - Ancien format `bins.yml` / `expired-retrieve.yml` (lignes separees par
    `;`) : hors perimetre (le plugin le convertit lui-meme au demarrage).
  - Fichiers vides (0 octet) possibles : traites comme "aucune donnee".
- **Champs** : `seller` / `buyer` en UUID seuls (pas de noms -> resolution
  FAuction), `price` en `long` (entier), `expiry` en **secondes** epoch
  (date d'expiration, pas de date de mise en vente).
- **sourceId** : les `id` peuvent etre renumerotes par la source au
  chargement -> `nexus:bin:` + sha256(seller|expiry|price|item) ; pour le
  coffre `nexus:stash:<uuid>:<sha256(item)>:<occurrence>`. Le coffre est un
  import "one-shot" (documente).
- **Objets** : `ImportedItem.ofBukkitBase64(...)`.
- **Conversion** :

| Etat | Import FAuction |
|---|---|
| `buyer == ""` et `expiry > maintenant` | `AUCTION`, `listedAt = expiry - expiry.time` (lu dans le config source) |
| `buyer == ""` et `expiry.enable: false` dans la source | `AUCTION` (bug amont : `expiry` = date de creation) |
| `buyer == ""` et expiree | **ignoree** : l'objet est deja dans le coffre (sinon doublon) ; option `--opt recover-orphans=true` pour la rendre en `EXPIRED` si aucun objet identique n'est dans le coffre du vendeur (perte possible cote source si le serveur s'est arrete entre l'expiration et la verification) |
| `buyer` renseigne et `!= seller` | `HISTORIC` uniquement (vendeur deja paye, objet deja livre ou dans le coffre), sans date de vente |
| `buyer == seller` (retrait par le vendeur) | ignoree (journal seulement) |
| chaque objet de `expired-retrieve.json` | `EXPIRED` pour ce joueur, prix 0 |

- **Pas d'argent en attente** : la source paie le vendeur immediatement,
  meme hors ligne.
- **Pieges** : sauvegarde toutes les 5 minutes et a l'arret -> serveur arrete
  obligatoire ; ecriture non atomique -> JSON tronque possible, erreur
  claire ; l'historique ne couvre que `log-keep-time` (30 j par defaut).

### 5.3 AxAuctions (Artillex Studios, premium) - `id: axauctions`
- Version de reference : 2.10.0 (2026-10-09), MC 1.20.2+, Java 21. Code
  ferme : le schema n'est connu que partiellement -> le module **decouvre et
  valide le schema** au lancement et refuse proprement s'il ne le reconnait
  pas (version testee affichee dans le message).
- **Source** : base H2 (defaut, fichier probablement
  `plugins/AxAuctions/data.mv.db`, mode MySQL, H2 2.1.214), MySQL/MariaDB ou
  PostgreSQL. Parametres lus dans `plugins/AxAuctions/config.yml`
  (`database.type`, `prefix` defaut `axauctions`, adresse, identifiants).
- **Tables** (prefixe `axauctions_`) : utilisateurs (`id`, `uuid`, `name`),
  devises (`id`, `name`), objets en vente, historique, objets supprimes,
  paiements en attente, notifications, logs, messages. Noms exacts des
  tables/colonnes autres que users/currencies/logs/notifications **a
  confirmer sur une vraie base** (`INFORMATION_SCHEMA`) avant de coder.
- **Deux modes** (`--opt mode=db|runtime`, defaut `db`) :
  - `db` : lecture JDBC directe, AxAuctions arrete. Pour H2, le module a
    besoin de son propre driver H2 2.x (celui d'AxAuctions est reloge) ->
    declare via `libraries:` du `plugin.yml` du module (telechargement Paper),
    pas embarque dans FAuction.
  - `runtime` : AxAuctions charge, lecture via ses DAO (`Database.ITEM`,
    `HISTORY`, `PAYOUT`, `USER`, `CURRENCY`) avec l'artefact
    `com.artillexstudios:AxAuctionsAPI` en `provided`. Evite le verrou H2 et
    le decodage des objets, mais ces classes sont internes (pas une API
    garantie) et les ventes d'une devise non chargee sont invisibles.
    Secours uniquement ; AxAuctions doit etre desactive juste apres.
- **Objets** : `byte[]` NBT vanilla GZIP + `DataVersion` (AxAPI) ->
  `ImportedItem.ofVanillaNbtGzip(...)` ; detection par l'en-tete GZIP
  `1F 8B`.
- **Conversion** :

| Donnee source | Import FAuction |
|---|---|
| Objet en vente, `start_time + item-expire-time > maintenant` | `AUCTION` |
| Objet en vente expire (reste dans la meme table jusqu'a `item-deletion-time`) | `EXPIRED` pour le vendeur |
| Historique (vendeur, acheteur, prix, date de vente) | `HISTORIC` |
| Paiement en attente (`user_id`, montant, `currency_id`) | `PENDING_CURRENCY` |
| Objets supprimes, notifications, logs, messages multi-serveur | ignores |

- **Multi-devises** : chaque vente a une devise (`Vault`,
  `CoinsEngine-coins`, `PlayerPoints`...) -> table `import.currency-map` et
  option `--other-currency` (2.5).
- **Pas d'encheres** dans AxAuctions.
- **Pieges** : schema change selon les versions (refonte 2.0.0, devises
  deplacees vers AxIntegrations en 2.6.0, format des logs change en 2.10.0) ;
  fichier H2 verrouille tant qu'AxAuctions tourne (travailler sur une copie
  sinon) ; durees d'expiration a lire dans le config source.

### 5.4 Ou vivent ces modules
- **Auction-House et NexusAuctionHouse : integres au jar FAuction** (package
  `importers/`). Lecture de JSON uniquement, Gson est fourni par Paper : aucune
  dependance a ajouter. Enregistres au demarrage comme n'importe quel module.
- **AxAuctions : plugin separe `FAuction-Import-AxAuctions`**. Il a besoin du
  driver H2 et, en mode `runtime`, de l'API AxAuctions ; les ajouter a FAuction
  imposerait un telechargement a tous les serveurs. Bonus : c'est la preuve
  concrete que l'API fonctionne pour un module externe (meme chemin que les
  developpeurs tiers).

---

## 6. Etapes de realisation
Chaque etape compile et passe toute la suite de tests. Dans chaque etape, les
tests listes en section 8 sont ecrits **avant** le code de l'etape.

0. **E0 - Socle de tests** : dependance H2 (scope test), `TestItems`,
   chargeur de fixtures a jetons, `Clock` injectable, `RecordingImportSink`,
   `FakeImporter`, JaCoCo si retenu. Aucun code de production.
1. **E1 - API publique** : interfaces, enum, records, `ImportedItem`,
   `ImportAvailability`, `ImporterApi.API_VERSION`, events, Javadoc. Aucun
   branchement. *Tests : builders et factories `ImportedItem`.*
2. **E2 - Registre** : `ImporterRegistryImpl`, enregistrement `ServicesManager`
   dans `onEnable`, getter dans `FAuction`, desenregistrement sur
   `PluginDisableEvent`. *Tests : id en doublon refuse, version API trop haute
   refusee, desenregistrement a la desactivation du proprietaire.*
3. **E3 - Persistance** : methodes batch transactionnelles dans les 4 Queries,
   `ImportLogQueries` + table `fa_import_log`. *Tests : ajouter H2 en scope
   test (modes de compatibilite MySQL et PostgreSQL) pour tester les batch, le
   rollback d'un paquet et la contrainte UNIQUE sur une vraie base - premiere
   vraie base dans les tests.*
4. **E4 - ImportManager** : refus SQLite, pipeline validation / dedup / buffer
   / flush, dry-run, annulation, rapport, fichier de log. *Tests avec un
   `FakeImporter` : refus en SQLite, lignes invalides rejetees avec raison,
   relance = 0 doublon, dry-run = 0 ecriture, annulation propre, echec d'un
   paquet sans arret des suivants.*
5. **E5 - Commandes, permission, config, langues** (4 fichiers). *Tests :
   etendre `AuctionCommandAnnotationsTest` / `CommandRegistrationTest`,
   `MessageKeysTest`, parser `fauction:importer`.*
6. **E6 - Classes de base** `AbstractSqlImporter` / `AbstractYamlImporter` +
   helpers (dont `loadJson`, `sourceDataFolder`, table `currency-map`).
   *Tests : un importeur YAML de test sur un fichier de `src/test/resources`,
   correspondance de devises (mappee / non mappee).*
7. **E7a - Module Auction-House (ElaineQheart)**, integre. *Tests sur des
   `notes.json` de test couvrant chaque ligne du tableau 5.1 et 2.5 bis : vente
   active, partielle, expiree, expiree/supprimee par admin, vendue non
   encaissee, encheres (en cours avec/sans offres, terminee, perdants),
   champs absents, dates Gson avec U+202F, fichier vide.*
8. **E7b - Module NexusAuctionHouse**, integre. *Tests : chaque ligne du
   tableau 5.2, absence de doublon entre `bins` expirees et coffre,
   `expiry.enable: false`, option `recover-orphans`, fichiers vides ou JSON
   tronque, relance (sourceId par hash stable).*
9. **E7c - Plugin `FAuction-Import-AxAuctions`** : d'abord inspecter une vraie
   base AxAuctions 2.10 (H2 + MySQL) pour figer noms de tables/colonnes et
   valider `ofVanillaNbtGzip` sur de vrais objets ; puis mode `db`, puis mode
   `runtime`. *Tests : validation de schema (refus propre si inconnu),
   devises multiples, ventes expirees, paiements en attente, sur une base H2
   de test construite a partir du schema releve.*
10. **E8 - Documentation** : mise a jour du wiki `../FAuction.wiki` (detail
    en section 7), Javadoc, paragraphe README. Les exemples de code du wiki
    sont copies depuis les tests "documentation" (section 8.6) pour garantir
    qu'ils compilent.
11. **E9 - Audit en 3 passes** par sous-agents (fonctionnel / qualite /
    securite) : duplication d'objets ou d'argent (en particulier coffre
    Nexus et sequestre des encheres Auction-House), relance apres crash,
    transactions, entrees malveillantes d'un module.

---

## 7. Documentation : wiki `../FAuction.wiki`

Le wiki est un depot Git separe (pages Markdown en anglais, sans
`_Sidebar.md` : la navigation passe par `Home.md`). Les pages existantes
sont mises a jour, deux pages sont creees. Commit dans le depot du wiki,
separe du commit du code, et seulement une fois la version publiee.

### 7.1 Nouvelles pages
- **`Importing-Data.md`** (administrateurs)
  - Prerequis : MySQL/MariaDB/PostgreSQL uniquement (SQLite refuse, avec la
    raison et la marche a suivre pour migrer de base d'abord).
  - Procedure pas a pas : sauvegarde de la base -> arret ou desactivation du
    plugin source -> `/ah admin import list` -> `run <id> --dry-run` ->
    lecture du rapport -> `run <id>` + `confirm` -> verification ->
    suppression du plugin source.
  - Reference des options de `run` (`--types`, `--dry-run`,
    `--apply-limits`, `--expired-to-expires`, `--force-reimport`,
    `--other-currency`, `--bids`, `--opt`).
  - Une sous-section par module integre / officiel (Auction-House,
    NexusAuctionHouse, AxAuctions) : version source testee, fichiers ou base
    lus, tableau "ce qui est importe / ignore et pourquoi", options propres
    (`recover-orphans`, `apply-source-tax`, `mode=db|runtime`), pieges connus.
  - Conversion des encheres (tableau 2.5 bis) et des devises
    (`import.currency-map`).
  - Relancer un import (idempotence, `fa_import_log`), lire le fichier
    `imports/<id>-<date>.log`, FAQ (objet rejete, devise non mappee,
    multi-serveur).
- **`Importer-API.md`** (developpeurs de modules)
  - Dependance Maven (meme depot GitHub Packages que `API.md`), `plugin.yml`
    (`depend: [FAuction]`), enregistrement via `ServicesManager` ou
    `FAuction.getApi()`.
  - Contrat de `DataImporter`, `ImportSink`, `ImportContext`,
    `ImportHelpers`, records `Imported*`, formats `ImportedItem`.
  - Regles : `sourceId` stable et unique, tester `isCancelled()`, ne jamais
    ecrire dans la source, thread async (`callSync` si besoin de Bukkit),
    exceptions -> `skip()` plutot que `throw` pour une ligne.
  - Exemple complet commente : module minimal avec `AbstractYamlImporter`,
    puis extrait du module Auction-House (JSON).
  - Versionnement : `ImporterApi.API_VERSION`, `requiredApiVersion()`,
    politique de compatibilite (2.6).
  - Tester son module : utiliser `RecordingImportSink` (8.5) dans ses
    propres tests.

### 7.2 Pages existantes a modifier
| Page | Modification |
|---|---|
| `Home.md` | bloc "What's new in <version>" (import depuis d'autres plugins), liens vers les 2 nouvelles pages dans "Pages" |
| `Commands-and-Permissions.md` | lignes `/ah admin import list/run/status/cancel` dans "Admin commands" (utilisables en console), noeud `fauction.admin.import` dans "Permission tree" |
| `Configuration.md` | section `## import` (`batch-size`, `progress-interval-seconds`, `currency-map`) ; note dans "database.yml" : l'import exige une base SQL partagee |
| `API.md` | paragraphe + lien vers `Importer-API.md` ; events `ImportStartEvent` / `ImportFinishEvent` dans "Events" ; mise a jour de la version de la dependance Maven (actuellement 2.1.0) |

### 7.3 Controle
- Relecture croisee : chaque commande, option, permission et cle de config
  citee dans le wiki existe dans le code (verifie par un sous-agent lors de
  l'audit E9).
- Liens internes du wiki verifies (`[Texte](Page#ancre)`).

---

## 8. Strategie de tests

Objectif : chaque comportement decrit dans ce plan est couvert par un test
automatise **ecrit avant le code** (rouge -> vert), et la suite existante
reste verte a chaque etape. Les tests vivent dans
`src/test/java/fr/florianpal/fauction/` en miroir des packages (`api/importer`,
`managers/importer`, `importers/...`, `queries`).

### 8.1 Outillage a ajouter
- **H2** (`com.h2database:h2`, scope test) en mode `MODE=MySQL` puis
  `MODE=PostgreSQL` : premiere vraie base dans les tests. Les tests de
  persistance sont parametres sur les deux modes (`@ParameterizedTest`).
- **Fabrique d'objets de test** (`TestItems`) : genere les objets via
  MockBukkit et les serialise **au moment du test** (base64 Bukkit, octets
  Paper) plutot que de stocker des chaines figees dependantes de la version.
- **Fixtures de fichiers** dans `src/test/resources/importers/<plugin>/` :
  `notes.json`, `bins.json`, `expired-retrieve.json`, `config.yml` source,
  avec des jetons `${ITEM_x}` remplaces par `TestItems` au chargement.
- **Horloge injectable** (`Clock`) dans `ImportManager` et les modules :
  tous les calculs d'expiration sont testes a date fixe, jamais sur
  `System.currentTimeMillis()`.
- Optionnel : JaCoCo avec seuil de couverture de lignes (ex. 85 %) sur les
  packages `api.importer`, `managers.importer` et `importers`.

### 8.2 API publique et registre (E1, E2)
- `ImportedItem` : chaque factory, base64 avec retours a la ligne (MIME),
  base64 invalide -> erreur explicite, tableau vide refuse.
- Records : champs obligatoires null -> `NullPointerException` au builder,
  champs facultatifs acceptes.
- `ImporterRegistry` : id invalide (majuscules, espaces) ou deja pris refuse,
  `requiredApiVersion()` trop haut refuse, desenregistrement sur
  `PluginDisableEvent` du proprietaire seulement, service visible via
  `ServicesManager`.

### 8.3 Persistance (E3) - sur H2, modes MySQL et PostgreSQL
- Batch de N lignes -> N lignes en base avec les bonnes valeurs (dont
  `buyDate` d'origine pour l'historique et la devise pour l'argent en
  attente).
- Echec au milieu d'un paquet (ligne volontairement invalide en SQL) ->
  **rollback complet** du paquet, journal compris.
- `fa_import_log` : contrainte UNIQUE effective ; donnees et journal ecrits
  dans la meme transaction (crash simule entre les deux impossible a
  observer).
- DDL de `fa_import_log` valide pour chaque `SQLType` SQL.

### 8.4 Moteur d'import (E4, E5)
Avec un `FakeImporter` scriptable (liste de lignes, exception a la ligne k,
lenteur simulee) :
- Refus en SQLite (aucune ecriture, message dedie).
- Validation : une ligne invalide par regle de 2.5 (UUID null, AIR, prix 0 /
  negatif / NaN / infini, nom > 36 car., date future, devise non mappee) ->
  rejetee avec la bonne raison, les autres lignes importees.
- Noms : absent -> resolu via UUID ; codes couleur retires ; troncature.
- Idempotence : deux lancements successifs -> second = 0 ecriture ;
  `--force-reimport` -> reimport ; relance apres crash simule au paquet k ->
  reprise exacte, aucun doublon.
- `--dry-run` -> 0 ligne en base, rapport identique a un vrai import.
- `--types`, `--apply-limits`, `--expired-to-expires`, `--other-currency`,
  `--bids` : un test par option.
- Annulation : le paquet en cours est termine, aucun paquet suivant.
- Exception dans `read()` -> import termine proprement, rapport en erreur,
  verrou libere.
- Un seul import a la fois (second lancement refuse pendant le premier).
- Fin d'import : `updateCache()` appele, `ImportFinishEvent` emis avec le bon
  rapport ; `ImportStartEvent` annule -> rien n'est lu.
- Commandes : `AuctionCommandAnnotationsTest` / `CommandRegistrationTest`
  etendus, execution depuis la console, permission manquante refusee,
  confirmation (sans `confirm`, apres 30 s, par un autre emetteur).
- Config / langues : `ConfigUpdateTest` (passage version 12 -> 13 garde les
  valeurs et ajoute `import.*`), `MessageKeysTest` (cles `IMPORT_*` dans les
  4 langues).

### 8.5 Modules d'import (E6, E7a-c)
Principe : chaque module est teste **sans base FAuction**, avec un
`RecordingImportSink` qui enregistre ce que le module emet. On verifie donc
precisement la traduction source -> objets `Imported*`, independamment du
moteur. `RecordingImportSink` est livre dans l'API (classe de test
utilisable par les developpeurs tiers, cf. 7.1).

Un test par ligne de chaque tableau de conversion :
- **Auction-House** : vente active ; vente partiellement vendue (prix au
  prorata) ; expiree non vendue ; expiree par admin ; supprimee par admin
  (objet "terre" -> skip) ; vendue non encaissee (montant net de taxe et
  `apply-source-tax=false`) ; enchere en cours sans offre / avec offres ;
  enchere terminee (gagnant servi ou non, vendeur paye ou non, perdants
  rembourses ou non) ; `auctionTime` 0 et -1 ; champs absents ; dates Gson
  avec et sans U+202F ; fichier absent, vide, JSON invalide ; ancien
  emplacement de `notes.json`.
- **NexusAuctionHouse** : vente active (et `listedAt` deduit) ;
  `expiry.enable: false` ; vente expiree **non importee** (pas de doublon
  avec le coffre) ; `recover-orphans` avec et sans objet correspondant dans
  le coffre ; vente achetee -> historique seul ; retrait vendeur ignore ;
  coffre -> `EXPIRED` ; `money` ignore ; fichiers vides / tronques ;
  stabilite des `sourceId` hashes entre deux lectures et apres renumerotation
  des `id`.
- **AxAuctions** (base H2 de test construite d'apres le schema releve en
  E7c) : schema reconnu / inconnu ; vente active / expiree ; historique ;
  paiements en attente ; devises mappees / non mappees ; jointure
  utilisateurs ; detection GZIP `1F 8B`. Le decodage reel des objets NBT
  vanilla ne fonctionne pas sous MockBukkit -> isole derriere une interface
  `ItemDecoder` simulee en test unitaire, et couvert par le test sur serveur
  reel (8.7).
- `checkAvailability()` de chaque module : plugin source actif -> refus,
  fichiers/base absents -> message clair.

### 8.6 Tests de bout en bout et tests "documentation"
- Pour chaque module integre : fixture source -> `ImportManager` reel -> base
  H2 -> relecture via `AuctionCommandManager` / `ExpireCommandManager` /
  `HistoricCommandManager` : les donnees visibles par les joueurs sont les
  bonnes (proprietaire, prix, objet identique a l'original via
  `ItemStack.isSimilar` + quantite).
- Bilan global : pour une fixture donnee, nombre d'objets entrants = objets
  importes + objets rejetes/ignores listes dans le rapport (**aucun objet ne
  disparait sans trace**) ; somme d'argent due dans la source = somme
  importee en `fa_currency_pending` + montants rejetes.
- Les exemples de code du wiki `Importer-API.md` sont des classes de test
  compilees (`docs/examples` sous `src/test`), executees contre
  `RecordingImportSink` : un exemple du wiki qui ne compile plus casse le
  build.

### 8.7 Verification manuelle sur serveur reel (avant publication)
Ce que MockBukkit ne peut pas prouver, fait une fois par module sur un Paper
de test avec MariaDB, en suivant mot pour mot la page wiki
`Importing-Data.md` (ce qui la valide aussi) :
- vrais fichiers / vraie base de chaque plugin source avec quelques ventes
  de chaque etat (dont objets enchantes, nommes, tetes, shulkers pleins) ;
- `--dry-run`, import, relance (0 doublon), verification en jeu des objets,
  de l'argent en attente verse a la connexion, de l'historique ;
- AxAuctions : decodage NBT vanilla reel, en mode `db` (H2 et MySQL) et
  `runtime`.
Checklist et resultats notes dans la section REALISATION de ce plan.

---

## 9. Risques et points de vigilance
- **Plugin source actif apres l'import** : le risque principal pour les trois
  modules (objets/argent rendus deux fois). `checkAvailability()` refuse si le
  plugin source est active ; la doc impose de le retirer apres l'import.
- **Format source qui evolue** : chaque module affiche la version source
  testee et echoue proprement sur un format inconnu (critique pour
  AxAuctions, code ferme).
- **Duplication d'objets** : source toujours active pendant/apres l'import.
  -> avertissement + doc + idempotence ; FAuction ne modifie jamais la source
  (lecture seule par principe).
- **Lancement en SQLite** : bloque par un controle explicite au demarrage de
  l'import (teste en E4), jamais un import "a moitie supporte".
- **Items non deserialisables** (version MC differente, items custom) : rejet
  ligne par ligne, jamais d'arret global, liste dans le rapport.
- **Volume** (dizaines de milliers de lignes) : batch + transaction + flush par
  paquets, jamais tout en memoire ; taille du pool Hikari a garder en tete
  (l'import tient une connexion pendant chaque paquet).
- **Multi-serveur sur base partagee** : lancer l'import sur un seul serveur ;
  les autres voient les donnees au prochain `CacheSchedule`. Le verrou
  d'import est par JVM (meme limite acceptee que pour `ClaimManager`) ; la
  contrainte UNIQUE de `fa_import_log` empeche quand meme les doublons si deux
  serveurs lancent le meme import.
- **Modules tiers non fiables** : toute exception de `read()` est capturee et
  l'import termine proprement ; aucune donnee n'est ecrite sans passer la
  validation FAuction.

---

## 10. Decisions a valider avant de commencer
- **D1.** Un seul module Maven + package `api` (recommande) ou extraction
  immediate d'un module `fauction-api` ?
- **D2.** ~~Plugins cibles~~ : Auction-House (ElaineQheart), NexusAuctionHouse,
  AxAuctions.
- **D3.** Repartition proposee en 5.4 (deux modules integres, AxAuctions en
  plugin separe) : OK ? Si oui, le plugin AxAuctions vit-il dans un depot
  separe ou dans un sous-dossier de ce depot (ce qui implique un pom parent,
  donc lie a D1) ?
- **D6.** Encheres Auction-House : conversion en remboursements + objets
  rendus (2.5 bis, recommande) ou attendre la fusion de `feature/bid` pour
  les importer telles quelles ?
- **D7.** AxAuctions : avez-vous acces a un serveur avec AxAuctions (ou a une
  copie de sa base) ? Indispensable pour figer le schema en E7c ; sans cela,
  ce module reste en "best effort".
- **D4.** Confirmation a deux saisies pour `run` : OK ou trop contraignant en
  console ?
- **D5.** H2 en mode compatibilite pour les tests (recommande, leger) ou
  Testcontainers avec un vrai MariaDB/PostgreSQL (plus fidele, necessite
  Docker) ?

---

## REALISATION (2026-10-10)

Rien n'est commite ni pousse (demande explicite). Code dans le depot FAuction,
documentation dans `../FAuction.wiki` (non commitee non plus).

### Decisions prises par defaut (section 10, en l'absence de reponse)
- D1 : un seul module Maven, package `api.importer` comme frontiere.
- D3 : Auction-House et NexusAuctionHouse integres au jar.
- D4 : confirmation a deux saisies conservee (`run <id> confirm` sous 30 s,
  options de la premiere saisie ; le dry-run n'en demande pas).
- D5 : H2 2.3.232 en scope test, modes MySQL et PostgreSQL.
- D6 : conversion des encheres (tableau 2.5 bis), `--bids=skip` disponible.
- D7 : pas d'acces a une base AxAuctions -> **E7c non realise** (voir "Reste").

### Livre, par etape
- E0 : H2 (test), `TestItems`, `Fixtures` (jetons `${...}`, date de
  modification fixee), `FakeImporter`, horloge injectable partout.
  JaCoCo non ajoute (optionnel).
- E1 : package `api.importer` : `DataImporter`, `ImportDataType`,
  `ImportedRow` + 4 records avec builders, `ImportedItem` (5 formats),
  `ImportSink`, `ImportContext`, `ImportHelpers`, `ImportAvailability`,
  `ImportReport`, `ImporterRegistry`, `ImporterApi.API_VERSION = 1`,
  `ImportSkipException`, events `ImportStartEvent` / `ImportFinishEvent`,
  `testing.RecordingImportSink` et `testing.TestImportContext`.
- E2 : `ImporterRegistryImpl` (ServicesManager + `FAuction.getImporterRegistry()`,
  desenregistrement sur `PluginDisableEvent`).
- E3 : `add*Batch` dans les 4 Queries (connexion fournie, exception levee),
  `ImportLogQueries` + table `fa_import_log` (UNIQUE importerId/dataType/
  sourceId, `sourceId` en `utf8_bin` sur MySQL/MariaDB).
- E4 : `ImportManager`, `ImportRun` (sink + compteurs), `ImportValidator`,
  `JdbcImportStore` (donnees + journal dans une transaction), rapport chat +
  fichier `plugins/FAuction/imports/<id>-<date>.log`.
- E5 : `/ah admin import list|run|status|cancel` (console OK), parser
  `fauction:importer`, permission `fauction.admin.import`, config.yml v13
  (`import.batch-size`, `progress-interval-seconds`, `currency-map`),
  33 cles de langue x 4 fichiers.
- E6 : `AbstractSqlImporter`, `AbstractYamlImporter`, helpers.
- E7a/E7b : modules `auctionhouse-elaine` et `nexusauctionhouse`.
- E8 : wiki (`Importing-Data.md`, `Importer-API.md` crees ; `Home`, `API`,
  `Commands-and-Permissions`, `Configuration` modifies), README, Javadoc.
  Les exemples du wiki sont les classes compilees de `src/test/.../docs/examples`.
- E9 : audit 3 passes (fonctionnel / qualite / securite), corrections ci-dessous.

Tests : 334 (dont ~110 nouveaux), tous verts ; `mvn package` OK.

### Ecarts au plan
- `ImportedAuction` a un champ `currency` (necessaire pour `--other-currency`).
- `ImportedExpired.price` peut valoir 0 (coffre Nexus) ; prix > 0 ailleurs.
- `--apply-limits` : un depassement **rend l'objet au vendeur** (expires) au
  lieu de le rejeter (jamais d'objet perdu) ; limites de prix + blacklist
  seulement, pas `limitations` par rang ni le contenu des shulkers.
- `ImportContext` expose aussi `requestedTypes()`, `convertBids()`, `clock()` ;
  `ImportHelpers` aussi `lastModified()` et `decodeItem()`.
- Les etats dependant du temps sont evalues a la date de derniere ecriture du
  fichier source (pas "maintenant"), cf. audit C1.
- `supportedTypes()` n'est plus deduit de `sources(null)` dans les classes de
  base : le module le declare.
- AH : la decision "supprime par admin" utilise `layout.yml`
  (`items.deleted.name`) ; repli = DIRT nomme.

### Corrections issues de l'audit E9
- CRITIQUE : une vente importee en AUCTION puis vue EXPIRED a la relance etait
  dupliquee. Corrige : evaluation a la date du fichier source + AUCTION/EXPIRED
  partagent leur espace de `sourceId` (journal et doublons intra-run).
- Enchere AH en cours puis terminee entre deux runs creait de l'argent : meme
  correctif. Encherisseur deja dans `claimedPlayers` jamais rembourse.
- Paquet refuse : rejoue ligne a ligne (seule la ligne fautive echoue).
- Objet > 65 535 octets (BLOB), donnees > 1 Mo, pile > 99 : rejetes.
- Deserialisation Bukkit des fichiers sources filtree (`ObjectInputFilter` :
  liste blanche + profondeur/refs/taille).
- `--force-reimport` : avertissement explicite a la confirmation.
- Disponibilite verifiee avec les options tapees (modules a `--opt`).
- `callSync` : refus depuis le thread principal, timeout 30 s.
- Verrou de fin porte par le run (course avec l'arret du serveur).
- Rapport : textes bornes, caracteres de controle retires ; `lastReport` sans
  la liste des lignes.
- Arrondi AH : ordre de calcul de la source, pas de troncature sans taxe.
- Mode/statut traduits ; `supportedTypes()` tiers protege par try/catch.

### Constats hors perimetre (pre-existants)
- Le DDL livre de FAuction n'est pas du PostgreSQL valide (backticks, `BLOB`,
  `LONG`) : les tests PG traduisent le DDL comme le ferait un admin.
- `HistoricQueries.addHistoric(UUID, ..., Date)` remplit 2 fois le parametre 7
  et jamais le 8 (inutilise aujourd'hui).
- `CurrencyScheduler` paie puis supprime sans reservation : deux passes
  concurrentes pourraient payer deux fois (fenetre elargie par un gros import).

### Reste a faire
- E7c AxAuctions (plugin separe) : necessite une vraie base 2.10 pour figer le
  schema et valider `ofVanillaNbtGzip` (decode par `ItemStack.deserializeBytes`,
  non verifie sur de vraies donnees).
- 8.7 : verification manuelle sur Paper + MariaDB (non faite).
- Bump de version (pom/plugin.yml 2.3.0 -> 2.4.0, le wiki annonce 2.4.0).
- Suggestions d'audit non retenues : liste blanche des URL JDBC de
  `openJdbc`, plafond `import.max-amount`, renommage/marquage des fichiers
  source apres import, lecture JSON en flux pour les tres gros fichiers,
  `seen` en hash 64 bits, retrait des codes couleur des valeurs non fiables
  interpolees dans les messages admin, verification de "expire par admin"
  sur une enchere AH avec offres.
- Commit du depot et du wiki : a faire par l'utilisateur.
