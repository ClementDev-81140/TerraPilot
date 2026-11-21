# ASSISTANT TERRASSEMENT — Analyse du cahier des charges

Date : 21 novembre 2026. Dépôt GitHub `ClementDev-81140/TerraPilot` : **vide** (vérifié le 21/11/2026) — développement parti d'une base propre.

## 1. Contraintes identifiées

| Domaine | Contrainte | Conséquence |
|---|---|---|
| Machine | Kubota KX012 ≈ 1 280 kg, profondeur 1 910 mm, portée 3 475 mm, largeur 1 055 mm, lame 930 mm | Fixation magnétique démontable possible sur bras acier, sans perçage. Version exacte (canopy/cabine, année) à confirmer avant achat de support. |
| Tablette | Non encore achetée, 10–11", GPS obligatoire, lisible en extérieur | Ne pas supposer le GPS sur les Wi-Fi. Référence vérifiée : Samsung Galaxy Tab A9+ Wi-Fi (SM-X210) — GNSS GPS+Galileo+GLONASS+Beidou+QZSS confirmé sur fiche technique. |
| Laser | Non encore acheté, budget < 1 000 € laser + récepteur | Compatibilité laser ↔ récepteur obligatoire. FMR 600 universel rouge/vert. Achat en 2 étapes recommandé (voir 03-materiel.md). |
| Hors ligne | Chantier sans Internet | Architecture OFFLINE FIRST : Room local, calculs 100 % locaux, tuiles IGN en cache, PDF natif. |
| Sécurité réseaux | DT-DICT obligatoire, classes A/B/C | L'app stocke les réponses DT-DICT, affiche les classes, ne présente jamais une zone vide comme « sans danger ». |
| Précision | Affichage 0,5 cm souhaité | Arrondi d'affichage seulement ; calculs en double. Résolution ≠ précision réelle (documenté). |
| GPS tablette | 3–10 m typique | Jamais présenté comme centimétrique. Précision affichée pour chaque point GPS. |

## 2. Fonctionnalités réalisables directement (logiciel seul)

Chantiers, niveaux, pentes, volumes, implantation, relevé (ruban/GPS/plan), plans (import/calibration/annotations), carte IGN WMTS + cache hors ligne, GPX iPhiGéNie, réseaux (fiches + alertes), guidage type A (guide + calculs tablette), photos, rapports PDF, sauvegardes ZIP, paramètres (thème/orientation/alertes).

## 3. Fonctionnalités nécessitant du matériel

- Guidage laser réel : laser rotatif + trépied + mire + cellule FMR 600 sur bras. Sans ce matériel, le module Niveaux fonctionne à la mire (lecture manuelle), le module Guidage affiche la procédure.
- Position GPS réelle : dépend du GNSS de la tablette (vérifier la référence exacte).
- Cartes IGN hors ligne : nécessite un téléchargement préalable avec Internet (respect CGU Géoplateforme).

## 4. API et services externes

- **IGN Géoplateforme WMTS** : `https://data.geopf.fr/wmts`, CGU `https://cartes.gouv.fr/cgu/`. WMTS sans limite de requêtes/s, mais usage raisonnable + cache local. Aucun scraping de cartes protégées.
- **Guichet unique DT-DICT** : `reseaux-et-canalisations.gouv.fr` (INERIS). L'app ne dépose pas les DT-DICT ; elle archive les réponses (plans, classes, dates).
- **iPhiGéNie** : aucune API privée supposée. Échange par fichiers GPX 1.1 uniquement.
- **Cloud** : aucun service payant imposé. Sauvegarde via SAF vers le dossier cloud personnel de l'utilisateur (Drive/OneDrive).

## 5. Risques techniques

- Build Android lourd (JDK 21 + SDK + Gradle) : documenté dans 05-installation.md.
- Photos en perspective : mesures faussées sans rectification (avertissement dans l'app).
- Relevé insuffisant : volumes refusés avec message explicite (garde-fou `SurveyCalculator.reliability`).
- Bluetooth : non simulé. Le FMR 600 ne transmet pas à la tablette ; l'app ne prétend pas le contraire.
- Données : séparation stricte par chantier (`chantierId` sur chaque entité), historique, confirmation avant suppression.

## 6. Hypothèses documentées

- minSdk 26 (Android 8) pour couvrir les tablettes économiques ; targetSdk 34.
- Repère local ENU par approximation équirectangulaire (< 10 km). RGF93 ≈ WGS84 à quelques décimètres (dérive des plaques) — suffisant pour l'assistance, pas pour le géoréférencement réglementaire.
- Foisonnement par défaut 1,25, modifiable, toujours étiqueté « estimation ».
- Tracé réseau par défaut = segment de démonstration à affiner au relevé (voir guide).
