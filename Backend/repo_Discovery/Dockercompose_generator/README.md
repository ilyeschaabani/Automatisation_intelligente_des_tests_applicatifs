# Dockercompose Generator (dcgen)

CLI/Lib qui **analyse** un projet déjà présent localement (Node/Python/Java/.NET/Go/…) et génère un `docker-compose.yml` **bien structuré** avec services (app + DB si détectée) + **healthchecks**.

Important : le clonage Git n’est plus fait par `dcgen`. Cette responsabilité est déléguée à un service de clonage (ex: `clone_repo`) qui gère les checkouts temporaires + cleanup.

> Ollama est optionnel : si un serveur Ollama local est dispo (`http://localhost:11434`), `dcgen` peut s’en servir pour améliorer le compose. Sinon il reste 100% heuristique.

## Installation

```bash
python -m pip install -e .
```

## Usage rapide

- Générer dans un repo local :

```bash
dcgen generate .
```

Par défaut, `dcgen` est en **mode strict**: si un service n'a pas de `Dockerfile` et que le générateur ne peut pas déduire une commande de démarrage runnable (Node/Python), il **demande** la commande à l'utilisateur.

Pour forcer un mode best-effort (draft) si besoin:

```bash
dcgen generate . --no-strict
```

- Forcer l’utilisation d’Ollama :

```bash
dcgen generate . --use-ollama --ollama-model llama3.1
```

## Tester avec Postman (API HTTP)

Lance le serveur local :

```bash
dcgen serve --host 127.0.0.1 --port 8001
```

### 1) Health

- Method: `GET`
- URL: `http://127.0.0.1:8001/health`

### 2) Génération

- Method: `POST`
- URL: `http://127.0.0.1:8001/generate`
- Body → raw → JSON:

```json
{
	"target": ".",
	"branch": null,
	"use_ollama": false,
	"ollama_model": "llama3.1",
	"db": "postgres",
	"port": null,
	"health_path": null
}
```

Remarque: `branch` est conservé pour compatibilité mais n’est plus supporté (checkout à faire en amont).

La réponse contient `compose_yml` (le contenu du `docker-compose.yml`) et éventuellement `dockerfile`.

## Résultat

Le générateur écrit :
- `docker-compose.yml`
- (si nécessaire) un `Dockerfile` minimal pour builder l’app

Le compose est **parallel-safe**:
- pas de `container_name`
- pas de ports DB publiés sur l'hôte
- **un seul service** (le *main API service*) est publié sur l'hôte via un port dynamique: `0:<containerPort>`

Pour retrouver le port hôte assigné:

```bash
docker compose port <api_service> <containerPort>
```

## Notes

- Si `dcgen` ne peut pas déduire la DB ou un endpoint de health, il **n’invente pas** : il met des `TODO` ou n’ajoute pas la DB.
- Vous pouvez forcer certaines valeurs via options CLI (`--db`, `--port`, `--health-path`).
- Vous pouvez forcer quel service est considéré comme l'API principale via `--api-service`.
