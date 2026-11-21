# Installation — Assistant Terrassement

## 1. Prérequis (PC)

- JDK 21 (OpenJDK 21 ou Temurin 21).
- Android SDK (platform-tools, platforms;android-34, build-tools;34.0.0).
- 8 Go RAM minimum (4 Go possibles avec `--no-daemon` et `-Xmx1536m`).

## 2. Compiler l'APK

```bash
cd TerraPilot
export JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
export ANDROID_HOME=~/android-sdk
echo "sdk.dir=$ANDROID_HOME" > local.properties
./gradlew testDebugUnitTest assembleDebug --no-daemon
```

APK généré : `app/build/outputs/apk/debug/app-debug.apk`

## 3. Installer sur la tablette

- Copiez l'APK sur la tablette (USB, e-mail, Drive).
- Autorisez « Installer des applications inconnues » pour le navigateur/fichiers.
- Touchez l'APK → Installer. L'app s'appelle **Assistant Terrassement**.
- Au premier lancement : autorisez la localisation (GPS) et la caméra (photos).

## 4. Permissions

| Permission | Usage |
|---|---|
| Localisation précise | Position GPS, relevés GPS, proximité réseaux |
| Caméra | Photos de chantier (via galerie/import en V1) |
| Internet | Tuiles IGN en ligne (optionnel, hors ligne sinon) |

## 5. Première configuration

1. Paramètres → Thème (clair en plein soleil, sombre le soir), Orientation (paysage recommandé en cabine).
2. Paramètres → Alertes sonores (volume, niveau/réseaux).
3. Mes chantiers → Créer votre premier chantier.
4. Carte IGN → Téléchargez la zone du chantier (avec Internet).

## 6. Sauvegarde et restauration

- **Exporter** : Sauvegardes → « Exporter tous les chantiers » → choisissez USB / SD / Drive.
- **Restaurer** : Sauvegardes → « Importer / restaurer » → choisissez le ZIP.
- Vérifiez le message « intégrité OK ».

## 7. Mise à jour

- Recompilez l'APK (nouveau `versionCode`) et réinstallez par-dessus : les données Room sont conservées (migration automatique).
- Ne désinstallez pas avant d'avoir exporté une sauvegarde.

## 8. Dépannage

- **GPS imprécis** : travaillez en extérieur, attendez 2–3 min, vérifiez la précision affichée.
- **Tuiles absentes** : reconnectez-vous et retéléchargez la zone.
- **PDF illisible** : ouvrez-le avec le lecteur PDF du système.
- **Espace insuffisant** : Carte IGN → « Supprimer les zones téléchargées », ou Sauvegardes → exportez puis libérez.
