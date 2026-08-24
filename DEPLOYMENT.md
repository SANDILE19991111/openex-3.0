# Deploying OpenEx 3.0 to Render

This deploys all 5 pieces: Postgres, the Kotlin backend, the Python
microservice, Ollama, and the React frontend — using `render.yaml` at the
project root (a Render "Blueprint").

## Before you start — read this honestly

1. **This will cost real money.** Postgres has a free tier (Render
   historically expires free Postgres databases after 90 days — check
   Render's current pricing page, since this may have changed). The Kotlin
   backend and Python service need paid "Starter" plans to stay always-on.
   Ollama needs a plan with real RAM — check Render's pricing page for
   current tiers and pick one with **at least 2-4GB RAM**; anything less
   will likely hit the same out-of-memory error we saw running Ollama
   locally on your 4GB machine.
2. **The first deploy will be broken on purpose.** Render can't know a
   service's public URL until that service already exists. So the blueprint
   deploys everything pointing at placeholder `localhost` URLs first — you
   fix the real URLs in step 4 below, then redeploy the affected services.
   This is normal, not a mistake.
3. **Ollama's first startup will be slow.** It has to download the model
   (~1.3GB) before it can serve any requests — expect several minutes on
   first boot, possibly longer depending on Render's network speed.

## 1. Push everything to GitHub
```powershell
cd C:\dev\openex-3.0
git add .
git commit -m "chore(deploy): Render blueprint and production config"
git push
```

## 2. Create the Blueprint on Render
1. Go to **render.com** → sign in
2. Click **New** → **Blueprint**
3. Connect your GitHub account if you haven't already, select the
   `SANDILE19991111/openex-3.0` repo
4. Render detects `render.yaml` and shows you all 5 services it's about to
   create — review, then click **Apply**
5. Wait for the initial deploys to finish (Ollama will be the slowest —
   watch its logs for "Pulling model" then "Ollama server is up")

## 3. Note down the real URLs
Once services are up, each one has a URL like:
```
openex-core-reactor  → https://openex-core-reactor-XXXX.onrender.com
openex-astromech      → https://openex-astromech-XXXX.onrender.com
openex-frontend        → https://openex-frontend-XXXX.onrender.com
```
(Ollama is a private service — no public URL, only reachable internally by
`openex-astromech` via `http://openex-ollama:11434`, which is already
configured.)

## 4. Fix the circular env vars (manual step)
Go to each service's **Environment** tab on Render and update:

**`openex-core-reactor`**
- `FRONTEND_ORIGINS` → your real frontend URL from step 3

**`openex-astromech`**
- `KOTLIN_API_BASE` → your real backend URL from step 3
- `FRONTEND_ORIGINS` → your real frontend URL from step 3

**`openex-frontend`**
- `VITE_API_BASE` → `<backend-url>/api`
- `VITE_WS_BASE` → `<backend-url>/ws`
- `VITE_DROID_BASE` → `<astromech-url>/api`

After changing env vars, click **Manual Deploy → Deploy latest commit** on
each of the three services above — env var changes alone don't
automatically trigger a redeploy, and the frontend especially needs a
rebuild since Vite bakes these values in at build time, not runtime.

## 5. Verify it actually works
Visit your frontend URL and walk through the same checks we did locally:
1. Register a user
2. Create a wallet, deposit funds
3. Check the Markets page shows all 10 coins
4. Place an order on the Trading page, confirm the order book updates live
5. Try the AI chat widget, ask about your balance

## If something breaks
Same approach as everything else today: check the specific service's
**Logs** tab on Render for the actual error, paste it here, and we'll debug
it the same way we've been doing locally all along.

## Ongoing costs
Once you're done demoing/submitting this, remember to either delete the
services or downgrade them if you don't want to keep paying — Render bills
continuously for as long as paid services stay running.
