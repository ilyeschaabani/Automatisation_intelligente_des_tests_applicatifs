# Temp Git Clone + Docker Prep Service

Petit service HTTP qui clone un repo Git dans un dossier temporaire, puis le supprime quand vous appelez l’endpoint `done`.

Il expose aussi un endpoint d’orchestration `prepare` qui:
1) clone le repo dans un dossier temporaire
2) génère `docker-compose.yml` (+ `Dockerfile` si absent) **dans le repo cloné** via `dockercompose_generator` (`dcgenerator`)
3) retourne les artefacts (contenu YAML, chemins générés, notes)

## Prérequis

- Git installé et accessible via `git` dans le PATH
- Python 3.10+

## Installation

```bash
python -m venv .venv
# Windows
.\.venv\Scripts\activate
pip install -r requirements.txt
```

Note: le endpoint `/prepare` nécessite le code du générateur `dcgenerator`.
- Si vous exécutez depuis ce mono-repo, c’est pris en charge automatiquement (le service ajoute `Dockercompose_generator/src` au `PYTHONPATH` en fallback).
- Sinon, installez le package `dockercompose-generator` (ex: `pip install -e Dockercompose_generator`).

## Démarrage

```bash
uvicorn app:app --host 127.0.0.1 --port 8000
```

## Utilisation

### 1) Cloner

- Branche par défaut: `main`

```bash
curl -X POST http://127.0.0.1:8000/clone \
  -H "Content-Type: application/json" \
  -d '{"repo_url":"https://github.com/OWNER/REPO.git"}'
```

Avec une branche explicite:

```bash
curl -X POST http://127.0.0.1:8000/clone \
  -H "Content-Type: application/json" \
  -d '{"repo_url":"git@github.com:OWNER/REPO.git","branch":"develop"}'
```

Réponse (exemple):

```json
{
  "session_id": "...",
  "repo_path": ".../repo_Discovery/.workdir/clones/repo-...",
  "branch": "main"
}
```

### 2) Dire “j’ai terminé” (cleanup)

```bash
curl -X POST http://127.0.0.1:8000/done/<session_id>
```

### (Optionnel) Préparer un projet “runnable” via Docker

`/prepare` clone + génère `docker-compose.yml` et les Dockerfiles manquants.

Options utiles (toutes en JSON):
 - `generate_openapi` (défaut `true`): lance aussi github-discovery et retourne OpenAPI + endpoints + stats
 - `use_ollama` + `ollama_model`: si `use_ollama=true`, active aussi Ollama côté github-discovery (en plus de dcgen)

Options LLM (github-discovery via Ollama local):
 - définir `OLLAMA_ENABLED=true`
 - (optionnel) `OLLAMA_BASE_URL` (défaut `http://localhost:11434`)
 - (optionnel) `OLLAMA_MODEL` (défaut `llama3.1`)
 - (optionnel) `OLLAMA_TIMEOUT_SECONDS` (défaut `60`)

Outputs github-discovery:
 - Par défaut, l’orchestrateur conserve uniquement l’extraction d’endpoints (OpenAPI désactivé).
 - Pour activer la génération OpenAPI côté github-discovery: définir `OUTPUT_OPENAPI=true`.

AST (Tree-sitter) et mode offline:
 - Si ton réseau bloque les downloads GitHub des parseurs: `AST_ENABLED=false` (désactive AST) ou `AST_DISABLED_LANGUAGES=java`.

Modes “questionnaire”:

- **Standby HTTP (robuste, recommandé)**: `fail_on_missing=true`
  - si des infos sont nécessaires (DB/env), `/prepare` répond `409` avec un `session_id` et les champs `missing_*`
  - appelez ensuite `/continue/{session_id}` avec `db` et/ou `env_values` pour reprendre

- **Interactif terminal (une seule requête)**: `interactive=true`
  - si le serveur a un TTY (lancé dans un terminal), il pose les questions directement et bloque la requête jusqu’aux réponses
  - si le serveur n’a pas de TTY (service/daemon), il ne peut pas prompt et retourne simplement `missing_*`

 Champs de réponse OpenAPI (si `generate_openapi=true` et si github-discovery est disponible):
 - `openapi_path` + `openapi`
 - `endpoints_path` + `endpoints`
 - `stats_path` + `discovery_stats`

CLI interactif (simple, basé sur standby 409 + continue):

```bash
python interactive_prepare.py https://github.com/OWNER/REPO.git main
```

```bash
curl -X POST http://127.0.0.1:8000/prepare \
  -H "Content-Type: application/json" \
  -d '{"repo_url":"https://github.com/OWNER/REPO.git","branch":"main","interactive":true}'
```

Alias compatible "dcgen-style" (même effet):

```bash
curl -X POST http://127.0.0.1:8000/prepare \
  -H "Content-Type: application/json" \
  -d '{"target":"https://github.com/OWNER/REPO.git","branch":"main"}'
```

La réponse contient `compose_yml` (texte) et `repo_path` (chemin du clone). Le dossier reste présent tant que vous n’appelez pas `/done/<session_id>`.

## Notes

- Les clones et outputs sont stockés sous `repo_Discovery/.workdir/` (pas dans `%TEMP%`).
- En cas d’arrêt du service, un nettoyage best-effort est tenté au shutdown.
