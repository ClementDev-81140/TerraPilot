# Tests — résultats

## 1. Tests unitaires (JUnit, `CalculsTest.kt`)

Exécutés par `./gradlew testDebugUnitTest`. 12 tests :

| Test | Attendu | Statut |
|---|---|---|
| Pente 10 m / 20 cm | 2 % = 2 cm/m | À confirmer par le build |
| Pente % ≡ cm/m | Équivalence exacte | À confirmer |
| Tranchée 10 × 0,40 × 0,80 | 3,20 m³, surface 4,0 m² | À confirmer |
| Plateforme 10 × 5 × 0,20 | 10,00 m³ | À confirmer |
| Équerrage 4 × 3 → diagonale 5 | 5,00 m, OK à 5,00 / KO à 5,10 | À confirmer |
| Nivellement scénario (plan 101,50, lecture 1,20, cible 100,20) | +10 cm TROP HAUT | À confirmer |
| Nivellement trop bas | Remblai 10 cm | À confirmer |
| Relevé insuffisant (1 point) | Non fiable | À confirmer |
| Relevé grille 3×3 (6 m) | Fiable | À confirmer |
| Haversine Paris–Lyon | ~465 km ±15 km | À confirmer |
| ENU aller-retour | Écart < 1e-6° | À confirmer |
| Déblai/remblai grille 3×3 (10×10 m, 0,5 m) | 112,5 m³ déblai | À confirmer |

> Les statuts seront mis à jour avec les résultats réels du build (voir `app/build/reports/tests/`).

## 2. Vérifications manuelles (mode avion)

À effectuer sur la tablette après installation :

- [ ] Calculs (pentes, volumes, implantation) sans Internet.
- [ ] Plans déjà importés + annotations sans Internet.
- [ ] Tuiles IGN téléchargées sans Internet.
- [ ] Rapports PDF sans Internet.
- [ ] Sauvegarde locale sans Internet.
- [ ] Import/export GPX (iPhiGéNie).
- [ ] Import PDF/JPG/PNG + calibration.
- [ ] Alertes réseaux (son + visuel).

## 3. Limites connues (V1)

- Tranchées multi-tronçons : V1 = tronçon unique (additionnez les tronçons).
- Courbes de niveau : carte colorée + grille (courbes simplifiées non calculées).
- Parcelles cadastrales : couche non incluse (voir guide).
- PDF : visualisation via lecteur système ; annotations sur images.
- Camera : import via galerie (prise de photo native à brancher).
- Bluetooth : non implémenté (matériel sans interface documentée).
