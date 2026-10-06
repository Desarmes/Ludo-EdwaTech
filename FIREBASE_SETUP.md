# Konfigirasyon Firebase Ludo Express

## Sa app la bezwen

- Pwojè Firebase `edwa-panel-bot-admin` la sou plan Blaze pou Cloud Functions.
- Authentication: aktive **Anonymous** pou li klasman an ak **Email/Password** pou tout jwè ranked ak admin lan.
- Realtime Database ki deja egziste a; match yo ak klasman an rete nan branch apa `ludo/`.
- Cloud Functions deploy nan `us-central1`.

## Sekrè admin

Kòd admin ki te parèt nan ansyen kòd/konvèsasyon an pa dwe itilize ankò. Kreye yon nouvo kòd prive; pa mete li nan Kotlin, GitHub, oswa `google-services.json`.

Nan tèminal ki konekte ak pwojè w la, konekte Firebase CLI epi antre sekrè a nan prompt CLI a (pa nan chat):

```bash
npx firebase-tools login
npx firebase-tools functions:secrets:set LUDO_ADMIN_BOOTSTRAP_CODE --project edwa-panel-bot-admin
```

Apre deploy Functions yo, kreye yon kont admin ak e-mail/modpas Firebase pa w nan panèl la, epi aktive dwa admin ak nouvo kòd la. Fonksyon sèvè a bay custom claim la; modpas/kòd la pa estoke nan APK a.

Match ranked yo mande kont e-mail/modpas ki rete estab; kont anonim yo ka sèlman li klasman piblik la. Sa fè bannisman yo mare ak yon UID ki ka retounen sou yon lòt aparèy.

## Règ Realtime Database

`database.rules.ludo-fragment.json` se yon fragman pou nouvo branch lan. Li pa ranplase ansyen règ yo, epi li poko konekte ak `firebase.json` espre. Anvan nenpòt deploy Rules, konpare fragman sa a ak règ aktyèl yo pou konsève panèl admin/itilizatè ki la deja. Firebase Rules ki bay aksè nan rasin lan pa ka bloke lè yon règ pi ba di `false`.

## Deploy Cloud Functions

Apre ou fin aktive Blaze, antre kòd prive a nan Secret Manager ak kòmand anwo a epi deploye fonksyon yo:

```bash
npm install --prefix functions
npx firebase-tools deploy --only functions --project edwa-panel-bot-admin
```

Pa deploy Rules jiskaske ansyen règ yo te konpare ak fragman an.

## Bati APK

Nan Codespaces, kouri:

```bash
./gradlew assembleDebug
```

APK a parèt nan `app/build/outputs/apk/debug/app-debug.apk`.

Pou GitHub Actions: voye chanjman yo sou branch `main` oswa `master`, ouvri **Actions > Build Android APK**, epi telechaje artifact `app-debug` apre workflow la fini. Ou ka chwazi **Run workflow** tou.
