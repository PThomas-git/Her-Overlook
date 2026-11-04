# Hér'Overlook

Application Android de relevé terrain pour l'inventaire de réversibilité **Hérault Télécom** (POP / NRO et ADR).
Collecte hors ligne : accès, check-list de conformité, aménagement intérieur, baies et équipements (n° de série),
documents présents, usagers, photos, synthèse par site. Export ZIP (JSON + CSV + photos nommées) pour l'analyse sur PC.

> Ce dépôt est **public** : il ne contient aucune donnée ni document client.
> L'inventaire des sites est importé depuis le téléphone (`sites_inventaire.json`), jamais commité.

## Installer avec Obtainium

1. Obtainium → *Ajouter une application* → `https://github.com/PThomas-git/Her-Overlook`
2. Les mises à jour sont publiées à chaque tag `vX.Y.Z` (voir ci-dessous).

## Journal (log)

L'appli garde un journal local (démarrages, navigation, photos, export, GPS, erreurs) et enregistre la pile d'appels en cas de crash.
Menu ⋮ de l'accueil → **Partager le log** (mail, Drive, messagerie) ou **Enregistrer le log (fichier)**.
Le journal est aussi inclus dans l'export ZIP (`journal.log`).

## Publier une version

```bash
git tag v0.1.0
git push origin v0.1.0
```

Le workflow `.github/workflows/build.yml` compile l'APK et l'attache à la release GitHub.
Un push sur `main` compile aussi l'APK (artefact téléchargeable dans l'onglet Actions) sans créer de release.

### Signature (indispensable pour que les mises à jour s'installent par-dessus)

Sans secrets, l'APK est signé avec la clé *debug* du runner, qui change à chaque build : Obtainium ne pourra pas
mettre à jour par-dessus. Créer **une fois** un keystore et ajouter 4 secrets au dépôt
(*Settings → Secrets and variables → Actions*) :

| Secret | Contenu |
|---|---|
| `KEYSTORE_B64` | le fichier `.jks` encodé en base64 |
| `KEYSTORE_PASSWORD` | mot de passe du keystore |
| `KEY_ALIAS` | alias de la clé |
| `KEY_PASSWORD` | mot de passe de la clé |

```bash
keytool -genkeypair -v -keystore heroverlook.jks -alias heroverlook -keyalg RSA -keysize 2048 -validity 10000
```

PowerShell : `[Convert]::ToBase64String([IO.File]::ReadAllBytes("heroverlook.jks")) | Set-Clipboard`

**Ne jamais commiter le `.jks`** (déjà dans `.gitignore`). Le sauvegarder ailleurs : sa perte oblige à désinstaller l'app.

## Importer l'inventaire dans l'app

Copier `sites_inventaire.json` dans le téléphone (Téléchargements), puis dans l'app : menu ⋮ → *Importer l'inventaire (JSON)*.
Format : tableau d'objets `{ id, loc, kind (POP|ADR|ADR_PLUS), name, address, city, postalCode, tier, active, cooling, power, lat, lon, accessHO, accessHNO, note }`.

## Données

- Stockage : un fichier JSON par site dans le stockage privé de l'app (`files/sites/`), photos dans `files/photos/<site>/`.
- Photos : réduites à 2560 px, JPEG 85 %, nommées `<site>_<contexte>_<horodatage>.jpg`.
- Export : bouton ↑ de l'accueil → ZIP contenant `export.json`, `sites.csv`, `checklist.csv`, `baies_equipements.csv`,
  `amenagement.csv` et `sites/<id>/{site.json,photos/}`. CSV en UTF-8 avec BOM, séparateur `;` (ouverture directe dans Excel FR).

## Structure

```
app/src/main/java/fr/heroverlook/
  data/   Models, Catalog (points de contrôle), Store, Exporter, Photos
  ui/     Theme (charte), Components, écrans
  AppVM.kt, MainActivity.kt
```

Pas de wrapper Gradle commité : le CI installe Gradle 8.10.2. En local : Android Studio (Ladybug ou +) ouvre le projet tel quel.
