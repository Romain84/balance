# Directives et Contexte Médical du Projet openScale Santé

## 1. Contexte Clinique Patient Spécifique
- **Colostomie** :
  - La patiente utilise le système SenSura® Mio Click 2 pièces (poches fermées grises 60mm réf. 114229, noires réf. 197329, supports semi-convexes 15-40mm réf. 165690), anneaux et demi-lunes Brava® Support Plus (standard et XL), pâte, poudre, ceinture, anneaux Hollister et compresses Mölnlycke.
  - Le module de commande de stomie (`StomaOrderScreen.kt` / `StomaProductRepository.kt`) gère l'ajout, modification, suppression et restauration du catalogue persistant dans `stoma_catalog.json`.
  - La génération du bon de commande PDF pour la pharmacie se fait en A4 vectoriel avec en-tête Coloplast, cartouche ALD, codes GTIN/CIP et cadre de signature.
- **Endométriose & Cycle Hormonal (Post-Hystérectomie)** :
  - **Important** : La patiente n'a plus d'utérus (aucune menstruation/saignement). Ne JAMAIS poser de questions sur les « règles ».
  - Les ovaires sont conservés : la patiente ressent son cycle hormonal (variations d'humeur, douleurs, transit).
  - La question du check-in doit obligatoirement être formulée sous forme : *"Cycle menstruel ressenti (sensation hormonale) : Oui / Non"*.
- **Régime Sans Gluten Strict** :
  - Suivi des symptômes d'exposition accidentelle au gluten et du ballonnement (« Endo-Belly »).
- **Montre connectée Samsung Galaxy Watch 6 (SM-R930) & Health Connect** :
  - Lecture des données de sommeil nocturne (`SleepSessionRecord` : durée, réveils nocturnes).
  - Lecture de la température cutanée/basale nocturne (`BasalBodyTemperatureRecord`) pour objectiver la phase lutéale ovulatoire en l'absence de repères menstruels.
  - Intégration de ces biomarqueurs dans le Bilan Médical PDF A4.

---

## 2. Environnement Technique & Déploiement
- **Système d'exploitation & Shell** :
  - L'OS hôte est Windows avec PowerShell.
  - Ne JAMAIS utiliser `&&` pour enchaîner des commandes shell (syntaxe bash/cmd invalide en PowerShell) ; utiliser `;`.
- **Pas de JDK local sur la machine** :
  - Aucun JDK/Gradle n'est installé localement sur Windows.
  - La compilation, les tests unitaires (`testDebugUnitTest`) et la génération de l'APK (`assembleDebug`) s'effectuent exclusivement via l'intégration continue GitHub Actions (`ci_master.yml`) en poussant sur `origin/main`.
  - Pour tester ou installer l'APK : télécharger l'artefact depuis la release `dev-build` (`openScale-debug.apk`) ou surveiller l'API GitHub Actions.
- **ADB & Conflit de Signature APK** :
  - Les builds GitHub Actions utilisent un keystore de debug éphémère. L'installation par-dessus une version existante peut provoquer `INSTALL_FAILED_UPDATE_INCOMPATIBLE`.
  - Toujours préserver les données patient : sauvegarder la base de données Room (`openScale.db`, `openScale.db-wal`) et les préférences Datastore (`settings.preferences_pb`) avant toute désinstallation/réinstallation.
  - Restauration fiable via `adb push` vers `/data/local/tmp` puis `run-as com.health.openscale.debug cp`.

---

## 3. Standards d'Architecture & Génération PDF
- **Génération de Rapports PDF** :
  - Utiliser l'API native Android `android.graphics.pdf.PdfDocument` (format standard A4 : 595 x 842 points à 72 dpi).
  - Charte graphique : Bleu Coloplast (`#00365B`), Vert Émeraude (`#00695C`) et Turquoise (`#009DA8`).
  - Partage système : Utiliser `FileProvider` (`${context.packageName}.file_provider`) avec `Intent.ACTION_SEND` et drapeau `FLAG_GRANT_READ_URI_PERMISSION`.
