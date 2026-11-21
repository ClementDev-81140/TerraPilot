# Guide utilisateur — Assistant Terrassement

## Démarrage rapide (5 minutes)

1. **Mes chantiers → Créer** : nom, adresse, client. Le chantier devient le chantier ouvert.
2. **Niveaux** : installez le laser, saisissez l'altitude de référence + lecture mire → « Définir le plan laser ». Pour chaque point : nom, lecture mire, cote projet → « Calculer ». Choisissez l'affichage SIMPLE / NUMÉRIQUE / GRAPHIQUE.
3. **Pentes / Volumes / Implantation** : saisissez les dimensions (unités affichées près des champs), lisez le résultat, il est enregistré dans le chantier.
4. **Réseaux** : avant de creuser, enregistrez chaque réseau (type, source, classe A/B/C, profondeur, marge). Testez la proximité depuis votre position de travail.
5. **Carte IGN** : à la maison (Internet), téléchargez la zone. Sur le chantier, consultez-la hors ligne.
6. **Sauvegardes** : exportez en ZIP sur clé USB / carte SD chaque semaine.

## Nivellement au laser (détail)

- Le laser crée un plan horizontal à une altitude inconnue.
- **Plan laser = altitude référence + lecture mire référence.**
- **Altitude point = plan laser − lecture mire point.**
- **Écart = altitude point − cote projet** : positif = trop haut (creuser), négatif = trop bas (remblayer).
- Tolérance « niveau atteint » : ±5 mm. Affichage arrondi au demi-centimètre.
- **Précision réelle** : laser ±2–3 mm/30 m + mire + opérateur. Ne confondez pas l'affichage (5 mm) avec la précision.

## Pentes

- 1 % = 1 cm/m (par définition). L'app convertit automatiquement.
- Profil longitudinal : hauteurs à chaque mètre depuis le départ.
- Signe : pente montante ▲ (arrivée plus haute) / descendante ▼.

## Volumes

- Tranchée : L × l × p. Plusieurs tronçons possibles (V1 : tronçon unique, additionnez les tronçons).
- Plateforme : L × l × épaisseur.
- Fondations : filantes + fouilles isolées.
- Déblai/remblai : grille régulière existant vs projeté. **Volumes refusés si < 9 points ou emprise < 4 m** (message explicite).
- Foisonnement : coefficient 1,25 par défaut (estimation, modifiable dans Paramètres).

## Implantation

1. Implantez le rectangle au mètre ruban (longueur ×2, largeur ×2).
2. Mesurez la diagonale. Comparez à la diagonale théorique (ex. 4×3 → 5 m).
3. Écart ≤ tolérance (2 cm par défaut) = équerrage OK.

## Relevé topographique

- **Ruban** : X/Y locaux + Z. Précis (cm).
- **GPS** : lat/lon + précision affichée (m). Jamais centimétrique.
- **Plan** : report depuis plan calibré.
- La carte colorée (bleu = bas, rouge = haut) aide à repérer les points hauts/bas.
- Origine du repère conservée dans le chantier (lat/lon + nom).

## Plans

- Import PDF/JPG/PNG via le sélecteur de fichiers (SAF).
- Zoom (pincez), déplacement (glissez), rotation (bouton), plein écran.
- **Calibration** : saisissez une distance connue (ex. 10 m entre 2 points du plan). Les annotations servent de repères.
- **Annotations** : couche indépendante (le plan original n'est jamais modifié). Types : point, ligne, texte, flèche, réseau, fondation.
- **Attention** : photo en perspective = mesures faussées. Préférez un scan/PDF ou une photo bien à plat.

## Carte IGN + iPhiGéNie

- Couches : SCAN 25 (carte topographique) / Ortho (photo aérienne).
- **Hors ligne** : téléchargez la zone à la maison (bouton « Télécharger la zone »). Gérez l'espace (taille du cache affichée, suppression).
- **GPX** : importez vos points/traces iPhiGéNie, exportez vos repères pour les retrouver dans iPhiGéNie.
- Parcelles cadastrales : nécessitent une couche adaptée (non incluse en V1, voir limites).

## Réseaux enterrés — sécurité (à lire)

- **DT-DICT obligatoire** via le guichet unique INERIS (`reseaux-et-canalisations.gouv.fr`) avant tout terrassement à proximité de réseaux.
- **Classes** : A ≤ 40 cm (rigide) / 50 cm (flexible) • B : 40 cm–1,5 m • C : > 1,5 m ou inconnue.
- L'app archive vos réponses DT-DICT (source, classe, date, profondeur, certitude).
- **Marges de vigilance** : réglage d'assistance, **pas** une distance réglementaire.
- **Un plan ne suffit jamais à autoriser le terrassement.** Une zone sans tracé n'est **pas** garantie sans danger.
- Alertes : visuelles permanentes + sonores réglables (Paramètres). En environnement bruyant, fiez-vous aux bannières rouges.

## Guidage laser sur la KX012

1. Installez le laser sur trépied stable, chauffez 5 min.
2. Vérifiez le plan sur 2 points à la mire.
3. Fixez le FMR 600 magnétiquement sur le bras (démontable).
4. Testez : montez/descendez le bras, vérifiez TROP HAUT / TROP BAS / NIVEAU ATTEINT sur le récepteur.
5. Contrôlez à la mire toutes les heures (trépied qui bouge, laser qui dérive).
- La tablette calcule les cotes ; elle ne mesure pas le godet. Pas de Bluetooth inventé.

## Photos, rapports, sauvegardes

- **Photos** : importez, classez AVANT / PENDANT / APRÈS, commentez. Inclues dans les sauvegardes.
- **Rapports PDF** : complet (tout le chantier) ou simplifié (maçon/client). Partageables via Drive/e-mail.
- **Sauvegardes** : ZIP avec manifeste SHA-256. USB OTG, carte SD, dossier cloud personnel. Restauration avec vérification.

## Paramètres

Thème (clair/sombre/système), orientation (paysage/portrait/auto), alertes sonores (on/off, volume, choix niveau/réseaux), affichage demi-cm, coefficient de foisonnement.
