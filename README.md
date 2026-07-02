# Portail — app d'ouverture automatique

Petite app Android qui ouvre le portail via Sinric quand :
**tu entres dans la zone GPS "maison"** ET **un Bluetooth autorisé (voiture) est connecté**.

Aucune config à faire avant le build : tout se règle dans l'app après installation.

## 1. Mettre le code sur GitHub

### Option A — avec git (le plus simple)
1. Sur github.com : **New repository** → nom `portail-app` → **Create** (repo vide, sans README).
2. Dans ce dossier `gate-opener`, ouvre un terminal et :
```bash
git init
git add .
git commit -m "app portail v1"
git branch -M main
git remote add origin https://github.com/<TON_USER>/portail-app.git
git push -u origin main
```
(le `push` demandera ta connexion GitHub)

### Option B — sans git
Installe **GitHub Desktop** → *File → Add local repository* → choisis ce dossier → *Publish repository*.

## 2. Récupérer l'APK
- Sur ton repo GitHub → onglet **Actions** → clique le dernier run **Build APK**.
- En bas, section **Artifacts** → télécharge **portail-apk** (un .zip).
- Dézippe → tu obtiens **app-debug.apk**.

## 3. Installer sur le téléphone
- Envoie-toi l'APK (mail, câble, cloud…).
- Ouvre-le → autorise "installer des applis inconnues" → installe.

## 4. Configurer dans l'app
- **Clé API Sinric**, **Device ID** (déjà pré-rempli), **Latitude/Longitude** de la maison, **Rayon** (300), **Bluetooth autorisés** (ex. `Toyota Multimedia, Moto`).
- Bouton **1. Autoriser les permissions** → accepte tout, et mets la **Localisation sur "Toujours"**.
- Bouton **2. Enregistrer et activer**.
- Bouton **Tester** → doit afficher `HTTP 200` et actionner le portail.

⚠️ Comme MacroDroid : mettre la **batterie de l'app en "Sans restriction"** pour qu'elle tourne en fond.
