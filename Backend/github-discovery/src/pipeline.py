"""Main pipeline orchestrator"""

import json
import re
from pathlib import Path
from typing import List, Dict, Any, Optional
from dataclasses import dataclass, field
from datetime import datetime
import concurrent.futures

import yaml

from .config import Config
from .utils.logger import get_logger, setup_logger
from .utils.errors import PipelineError, FileProcessingError, ExtractionError, LLMError
from .utils.cache import Cache
from .repo_manager import RepositoryManager
from .tech_detector import TechStackDetector, TechStack
from .file_scanner import FileScanner
from .ast_parser import TreeSitterParser, ParseResult
from .chunker import ChunkingEngine, Chunk
from .llm_extractor import LLMExtractor, LLMExtractionResult
from .regex_extractor import RegexExtractor
from .endpoint_processor import EndpointProcessor, ProcessingResult
from .route_resolver import RouteResolver
from .models.openapi import OpenAPISpec
from .models.endpoint import Endpoint
from .layered_extractor import SpecFirstExtractor, ConfigRoutesExtractor, UniversalFallbackExtractor
from .java_dto_extractor import JavaDTOExtractor, java_type_to_schema


@dataclass
class PipelineStats:
    """Statistics from pipeline execution"""
    start_time: datetime
    end_time: Optional[datetime] = None
    total_files: int = 0
    files_processed: int = 0
    files_failed: int = 0
    endpoints_found: int = 0
    endpoints_from_spec: int = 0
    endpoints_from_config: int = 0
    endpoints_from_ast: int = 0
    endpoints_from_regex: int = 0
    endpoints_from_llm: int = 0
    endpoints_from_universal: int = 0
    duplicates_removed: int = 0
    llm_calls: int = 0
    llm_failed: int = 0
    cache_hits: int = 0
    spec_files_found: int = 0
    config_files_parsed: int = 0
    universal_files_scanned: int = 0
    errors: List[Dict[str, Any]] = field(default_factory=list)

    def duration(self) -> Optional[float]:
        """Get pipeline duration in seconds"""
        if self.end_time:
            return (self.end_time - self.start_time).total_seconds()
        return None

    def to_dict(self) -> Dict[str, Any]:
        """Convert to dictionary"""
        return {
            "start_time": self.start_time.isoformat(),
            "end_time": self.end_time.isoformat() if self.end_time else None,
            "duration_seconds": self.duration(),
            "total_files": self.total_files,
            "files_processed": self.files_processed,
            "files_failed": self.files_failed,
            "endpoints_found": self.endpoints_found,
            "endpoints_from_spec": self.endpoints_from_spec,
            "endpoints_from_config": self.endpoints_from_config,
            "endpoints_from_ast": self.endpoints_from_ast,
            "endpoints_from_regex": self.endpoints_from_regex,
            "endpoints_from_llm": self.endpoints_from_llm,
            "endpoints_from_universal": self.endpoints_from_universal,
            "duplicates_removed": self.duplicates_removed,
            "llm_calls": self.llm_calls,
            "llm_failed": self.llm_failed,
            "cache_hits": self.cache_hits,
            "spec_files_found": self.spec_files_found,
            "config_files_parsed": self.config_files_parsed,
            "universal_files_scanned": self.universal_files_scanned,
            "errors": self.errors,
        }


class Pipeline:
    """Main pipeline orchestrator"""

    def __init__(self, config: Config):
        self.config = config
        self.logger = get_logger(__name__)
        setup_logger(config.log_level)

        # Initialize components
        self.cache = Cache(ttl=config.cache_ttl)
        self.repo_manager = RepositoryManager(config, self.cache)
        self.file_scanner = FileScanner(Path())  # Will be set per repo
        self.ast_parser = TreeSitterParser()
        self.regex_extractor = RegexExtractor()
        self.chunker = ChunkingEngine(max_chunk_size=config.max_file_size // 100)  # Convert bytes to lines approx

        # Layered extractors
        self.spec_first_extractor = SpecFirstExtractor()
        self.config_routes_extractor = ConfigRoutesExtractor()
        self.universal_fallback_extractor = UniversalFallbackExtractor()
        
        # Initialize LLM extractor (supports both OpenRouter and OpenAI)
        try:
            self.llm_extractor = LLMExtractor(config)
            # Share pipeline cache with the LLM extractor (it implements optional caching).
            self.llm_extractor.cache = self.cache
            self.logger.info("LLM extractor initialized")
        except Exception as e:
            self.logger.warning("LLM extractor not available", error=str(e))
            self.llm_extractor = None
            
        self.endpoint_processor = EndpointProcessor()
        self.route_resolver = RouteResolver(Path(), self.file_scanner)  # Will be set per repo

        self.stats = PipelineStats(start_time=datetime.utcnow())

    def _filter_gateway_wildcard_endpoints(self, endpoints: List[Endpoint]) -> List[Endpoint]:
        """Remove Spring Cloud Gateway routing patterns from the endpoint list.

        Gateway route/predicate paths (often ending in '/**') are not directly testable
        REST endpoints, and they confuse downstream test generation.
        """
        if not endpoints:
            return endpoints

        gateway_patterns = {
            "spring_cloud_gateway_route",
            "spring_cloud_gateway_path_predicate",
        }

        filtered: List[Endpoint] = []
        removed = 0

        for e in endpoints:
            path = (getattr(e, "path", "") or "")
            md = getattr(e, "metadata", None)
            pattern = md.get("pattern") if isinstance(md, dict) else None

            is_gateway_pattern = pattern in gateway_patterns
            is_wildcard_path = "/**" in path

            if is_gateway_pattern or is_wildcard_path:
                removed += 1
                continue

            filtered.append(e)

        if removed:
            self.logger.info("Filtered gateway wildcard routes", removed=removed)

        return filtered

    def _enrich_openapi_with_discovery(
        self,
        openapi_dict: Dict[str, Any],
        *,
        repo_url: str,
        branch: Optional[str],
        repo_root: Path,
    ) -> Dict[str, Any]:
        """Attach a minimal runnable project config into OpenAPI as x-discovery.

        The goal is to make the OpenAPI artifact self-contained for downstream
        automation (run strategy, compose/service, base_url/health, auth/env hints).
        """

        def guess_provider(url: str) -> str:
            u = (url or "").lower()
            if "gitlab" in u:
                return "gitlab"
            return "github"

        def find_compose_candidates(root: Path) -> List[Path]:
            preferred = [
                root / "docker-compose.yml",
                root / "docker-compose.yaml",
                root / "compose.yml",
                root / "compose.yaml",
            ]
            out: List[Path] = [p for p in preferred if p.exists() and p.is_file()]

            # Add docker-compose.* variants at root
            for p in list(root.glob("docker-compose.*.yml")) + list(root.glob("docker-compose.*.yaml")):
                if p.exists() and p.is_file() and p not in out:
                    out.append(p)

            # If none at root, do a bounded search (depth <= 3)
            if not out:
                try:
                    for p in root.rglob("docker-compose.y*ml"):
                        try:
                            rel = p.relative_to(root)
                        except Exception:
                            continue
                        if len(rel.parts) <= 4 and p.is_file():
                            out.append(p)
                            if len(out) >= 10:
                                break
                except Exception:
                    pass
            return out

        def parse_ports(ports: Any) -> List[Dict[str, Optional[int]]]:
            parsed: List[Dict[str, Optional[int]]] = []
            if not ports:
                return parsed
            if isinstance(ports, list):
                for item in ports:
                    if isinstance(item, str):
                        parts = item.split(":")
                        published = None
                        target = None
                        try:
                            if len(parts) == 1:
                                target = int(parts[0])
                            elif len(parts) == 2:
                                published = int(parts[0])
                                target = int(parts[1])
                            elif len(parts) == 3:
                                published = int(parts[1])
                                target = int(parts[2])
                        except Exception:
                            continue
                        parsed.append({"published": published, "target": target})
                    elif isinstance(item, dict):
                        try:
                            target = int(item.get("target")) if item.get("target") is not None else None
                            published = int(item.get("published")) if item.get("published") is not None else None
                            parsed.append({"published": published, "target": target})
                        except Exception:
                            continue
            return parsed

        def _is_infra_service(name: str, svc: Any) -> bool:
            nl = (name or "").lower()
            if any(k in nl for k in [
                "postgres",
                "postgis",
                "mysql",
                "mariadb",
                "mongodb",
                "mongo",
                "redis",
                "memcached",
                "rabbitmq",
                "kafka",
                "zookeeper",
                "elasticsearch",
                "opensearch",
                "pgadmin",
                "adminer",
                "phpmyadmin",
            ]):
                return True

            if isinstance(svc, dict):
                img = (svc.get("image") or "").lower() if svc.get("image") else ""
                if img:
                    if any(img.startswith(p) for p in [
                        "postgres",
                        "postgis",
                        "mysql",
                        "mariadb",
                        "mongo",
                        "mongodb",
                        "redis",
                        "memcached",
                        "rabbitmq",
                        "confluentinc/cp-kafka",
                        "bitnami/kafka",
                        "bitnami/zookeeper",
                        "elasticsearch",
                        "opensearchproject/opensearch",
                        "dpage/pgadmin",
                        "dpage/pgadmin4",
                        "adminer",
                        "phpmyadmin",
                    ]):
                        return True
            return False

        def _is_httpish_port(p: Optional[int]) -> bool:
            if not p:
                return False
            if p in {80, 443, 8000, 8001, 8080, 8081, 8082, 8085, 8088, 8089, 8090, 3000, 3001, 5000, 5001, 9000}:
                return True
            if 8080 <= p <= 8099:
                return True
            return False

        def _select_published_port(port_entries: List[Dict[str, Optional[int]]]) -> Optional[int]:
            if not port_entries:
                return None

            # Prefer published HTTP-ish ports
            for pe in port_entries:
                pub = pe.get("published")
                if _is_httpish_port(pub):
                    return pub

            # Otherwise any published port
            for pe in port_entries:
                pub = pe.get("published")
                if pub:
                    return pub

            # Fall back to target HTTP-ish ports
            for pe in port_entries:
                tgt = pe.get("target")
                if _is_httpish_port(tgt):
                    return tgt

            # Otherwise any target
            for pe in port_entries:
                tgt = pe.get("target")
                if tgt:
                    return tgt

            return None

        def health_guess_from_paths(paths_obj: Any) -> Optional[str]:
            """Return a health path only when we can see it in the spec."""
            if not isinstance(paths_obj, dict):
                return None
            if "/actuator/health" in paths_obj:
                return "/actuator/health"
            if "/health" in paths_obj:
                return "/health"
            return None

        def auth_guess_from_paths(paths_obj: Any) -> Dict[str, Any]:
            """Infer auth only when strongly supported by the spec.

            If multiple plausible login endpoints exist, do not pick one.
            Instead, return type=unknown and provide candidates so the UI can ask.
            """

            def _has_post(ops: Any) -> bool:
                return isinstance(ops, dict) and any(str(m).lower() == "post" for m in ops.keys())

            if not isinstance(paths_obj, dict):
                return {
                    "type": "unknown",
                    "login": None,
                    "login_candidates": [],
                    "static_token": None,
                    "header_name": "Authorization",
                    "header_template": "Bearer {token}",
                }

            candidates: List[str] = []
            for p, ops in paths_obj.items():
                if not _has_post(ops):
                    continue
                ps = str(p).lower()
                if any(k in ps for k in ["signup", "register"]):
                    continue
                if any(k in ps for k in ["signin", "login"]):
                    candidates.append(str(p))
                elif "authenticate" in ps:
                    candidates.append(str(p))

            if candidates:
                def _rank(x: str) -> int:
                    xl = x.lower()
                    if "signin" in xl:
                        return 0
                    if "/login" in xl or xl.endswith("login"):
                        return 1
                    return 2

                candidates.sort(key=_rank)
                if len(candidates) == 1:
                    login_endpoint = candidates[0]
                    return {
                        "type": "login_flow",
                        "login": {
                            "endpoint": login_endpoint,
                            "username": None,
                            "password": None,
                            "token_field": None,
                        },
                        "login_candidates": [login_endpoint],
                        "static_token": None,
                        "header_name": "Authorization",
                        "header_template": "Bearer {token}",
                    }

                return {
                    "type": "unknown",
                    "login": None,
                    "login_candidates": candidates,
                    "static_token": None,
                    "header_name": "Authorization",
                    "header_template": "Bearer {token}",
                }

            return {
                "type": "unknown",
                "login": None,
                "login_candidates": [],
                "static_token": None,
                "header_name": "Authorization",
                "header_template": "Bearer {token}",
            }

        def _service_http_endpoint(name: str, svc: Any) -> Dict[str, Any]:
            """Compute an HTTP-ish endpoint for a compose service (best-effort)."""
            svc_dict = svc if isinstance(svc, dict) else {}
            port_entries = parse_ports(svc_dict.get("ports"))
            is_infra = _is_infra_service(name, svc_dict)
            published_port = _select_published_port(port_entries)
            base_url = f"http://localhost:{published_port}" if published_port else None
            return {
                "is_infra": is_infra,
                "published_port": published_port,
                "base_url": base_url,
            }

        def choose_api_service(services: Dict[str, Any]) -> Optional[str]:
            if not services:
                return None

            scored: List[Dict[str, Any]] = []
            for name, svc in services.items():
                if _is_infra_service(name, svc):
                    continue
                svc_dict = svc if isinstance(svc, dict) else {}
                port_entries = parse_ports(svc_dict.get("ports"))
                if not port_entries:
                    continue

                nl = (name or "").lower()
                score = 0
                if "gateway" in nl:
                    score += 100
                if any(k in nl for k in ["api", "backend", "server"]):
                    score += 60
                if any(k in nl for k in ["auth", "users", "service", "app"]):
                    score += 10
                if svc_dict.get("build") is not None:
                    score += 15
                if any(_is_httpish_port(pe.get("published") or pe.get("target")) for pe in port_entries):
                    score += 40

                scored.append({"name": name, "score": score})

            if not scored:
                return None

            scored.sort(key=lambda x: x.get("score", 0), reverse=True)

            # Conservative: require a strong and non-ambiguous winner.
            top = scored[0]
            if top.get("score", 0) < 70:
                return None
            if len(scored) > 1:
                second = scored[1]
                if (top.get("score", 0) - second.get("score", 0)) <= 10:
                    return None
            return top.get("name")

        def _score_compose_services(services: Dict[str, Any]) -> int:
            if not services:
                return -10

            score = 0
            http_candidates = 0
            for name, svc in services.items():
                if _is_infra_service(name, svc):
                    continue
                svc_dict = svc if isinstance(svc, dict) else {}
                port_entries = parse_ports(svc_dict.get("ports"))
                if not port_entries:
                    continue
                http_candidates += 1
                nl = (name or "").lower()
                score += 20
                if "gateway" in nl:
                    score += 80
                if any(k in nl for k in ["api", "backend", "server"]):
                    score += 40
                if svc_dict.get("build") is not None:
                    score += 10
                if any(_is_httpish_port(pe.get("published") or pe.get("target")) for pe in port_entries):
                    score += 30

            if http_candidates == 0:
                return -10
            return score

        def _choose_best_compose_file(candidates: List[Path]) -> tuple[Optional[Path], int]:
            best: Optional[Path] = None
            best_score = -10

            for p in candidates:
                try:
                    text = p.read_text(encoding="utf-8", errors="replace")
                    data = yaml.safe_load(text) if text else None
                    d = data if isinstance(data, dict) else {}
                    svcs = d.get("services") if isinstance(d, dict) else None
                    svcs_dict = svcs if isinstance(svcs, dict) else {}
                    score = _score_compose_services(svcs_dict)
                except Exception:
                    score = -10

                if score > best_score:
                    best = p
                    best_score = score

            # If everything looks infra-only, return the best guess + score;
            # downstream logic decides whether to trust it.
            if best is None and candidates:
                return candidates[0], best_score
            return best, best_score

        def extract_required_env_vars(text: str) -> List[str]:
            # ${VAR} or ${VAR:-default}
            pattern = re.compile(r"\$\{([A-Za-z_][A-Za-z0-9_]*)[^}]*\}")
            return sorted(set(pattern.findall(text or "")))

        def _detect_db_from_compose(services: Dict[str, Any]) -> Dict[str, Any]:
            """Best-effort DB detection from trusted docker-compose services.

            Conservative rules:
            - Only report a DB when we can see a known DB image/name.
            - If multiple DB types exist, mark required but leave type unset.
            """

            if not services:
                return {"required": None, "type": None, "service": None, "url_env_var": None}

            candidates: List[Dict[str, str]] = []
            for name, svc in services.items():
                if not isinstance(svc, dict):
                    continue
                nl = (name or "").lower()
                img = (svc.get("image") or "").lower() if svc.get("image") else ""

                def _add(db_type: str) -> None:
                    candidates.append({"type": db_type, "service": str(name)})

                if "postgres" in nl or img.startswith("postgres") or img.startswith("postgis"):
                    _add("postgres")
                elif "mysql" in nl or img.startswith("mysql"):
                    _add("mysql")
                elif "mariadb" in nl or img.startswith("mariadb"):
                    _add("mariadb")
                elif "sqlserver" in nl or "mssql" in nl or img.startswith("mcr.microsoft.com/mssql"):
                    _add("mssql")
                elif "mongo" in nl or img.startswith("mongo") or img.startswith("mongodb"):
                    _add("mongodb")

            if not candidates:
                return {"required": None, "type": None, "service": None, "url_env_var": None}

            types = sorted({c["type"] for c in candidates if c.get("type")})
            if len(types) != 1:
                return {"required": True, "type": None, "service": None, "url_env_var": None}

            db_type = types[0]
            services_for_type = sorted({c["service"] for c in candidates if c.get("type") == db_type and c.get("service")})
            service_name = services_for_type[0] if len(services_for_type) == 1 else None
            return {"required": True, "type": db_type, "service": service_name, "url_env_var": None}

        def _detect_db_from_repo_files(root: Path) -> Dict[str, Any]:
            """Detect DB requirement/type from common framework config.

            This is intentionally conservative: it only sets type when strongly supported
            by a DATABASE_URL scheme or Doctrine driver.
            """

            candidates: List[Path] = []
            for rel in [
                ".env",
                ".env.local",
                ".env.dev",
                ".env.test",
                ".env.dist",
                ".env.example",
                "config/packages/doctrine.yaml",
                "config/packages/doctrine.yml",
                "config/packages/doctrine.yaml.dist",
                "config/packages/doctrine.yml.dist",
            ]:
                p = root / rel
                if p.exists() and p.is_file():
                    candidates.append(p)

            # Bounded search for doctrine.* at shallow depth
            if not candidates:
                try:
                    for p in root.rglob("doctrine.y*ml"):
                        try:
                            rel = p.relative_to(root)
                        except Exception:
                            continue
                        if len(rel.parts) <= 4 and p.is_file():
                            candidates.append(p)
                            if len(candidates) >= 5:
                                break
                except Exception:
                    pass

            scheme_re = re.compile(r"DATABASE_URL\s*=\s*['\"]?([A-Za-z][A-Za-z0-9+.-]*)://", re.IGNORECASE)
            driver_re = re.compile(r"\bpdo_(mysql|pgsql|sqlite|sqlsrv)\b", re.IGNORECASE)

            saw_database_url_key = False
            inferred_type: Optional[str] = None

            for p in candidates:
                try:
                    text = p.read_text(encoding="utf-8", errors="replace")
                except Exception:
                    continue

                if "DATABASE_URL" in text:
                    saw_database_url_key = True
                    m = scheme_re.search(text)
                    if m:
                        scheme = (m.group(1) or "").lower()
                        mapping = {
                            "mysql": "mysql",
                            "mariadb": "mariadb",
                            "pgsql": "postgres",
                            "postgres": "postgres",
                            "postgresql": "postgres",
                            "sqlite": "sqlite",
                            "sqlsrv": "mssql",
                            "mssql": "mssql",
                        }
                        mapped = mapping.get(scheme)
                        if mapped:
                            inferred_type = mapped
                            break

                dm = driver_re.search(text)
                if dm and not inferred_type:
                    drv = (dm.group(1) or "").lower()
                    mapping2 = {
                        "mysql": "mysql",
                        "pgsql": "postgres",
                        "sqlite": "sqlite",
                        "sqlsrv": "mssql",
                    }
                    inferred_type = mapping2.get(drv)

            if inferred_type:
                return {"required": True, "type": inferred_type, "service": None, "url_env_var": "DATABASE_URL"}
            if saw_database_url_key:
                return {"required": True, "type": None, "service": None, "url_env_var": "DATABASE_URL"}
            return {"required": None, "type": None, "service": None, "url_env_var": None}

        compose_path: Optional[str] = None
        compose_text = ""
        compose_data: Dict[str, Any] = {}
        compose_candidates = find_compose_candidates(repo_root)
        compose_candidate_paths = [p.relative_to(repo_root).as_posix() for p in compose_candidates]
        compose_score = -10

        if compose_candidates:
            chosen, chosen_score = _choose_best_compose_file(compose_candidates)
            compose_score = chosen_score

            # Be conservative: if we cannot see any plausible HTTP API entrypoint in compose,
            # do not treat it as authoritative. We'll ask the user to confirm.
            if chosen is not None and chosen_score > 0:
                compose_path = chosen.relative_to(repo_root).as_posix()
                try:
                    compose_text = chosen.read_text(encoding="utf-8", errors="replace")
                    loaded = yaml.safe_load(compose_text) if compose_text else None
                    compose_data = loaded if isinstance(loaded, dict) else {}
                except Exception:
                    compose_text = ""
                    compose_data = {}

        services = compose_data.get("services") if isinstance(compose_data, dict) else None
        services_dict: Dict[str, Any] = services if isinstance(services, dict) else {}

        # DB detection (compose is authoritative only when trusted, i.e., score > 0)
        db_info = _detect_db_from_compose(services_dict) if compose_path else {"required": None, "type": None, "service": None, "url_env_var": None}
        if not db_info.get("required"):
            # Fall back to repo configuration hints (e.g., Symfony doctrine DATABASE_URL)
            db_info = _detect_db_from_repo_files(repo_root)

        api_service = choose_api_service(services_dict)
        base_url = None
        published_port = None
        if api_service and isinstance(services_dict.get(api_service), dict):
            port_entries = parse_ports(services_dict[api_service].get("ports"))
            published_port = _select_published_port(port_entries)
            if published_port:
                base_url = f"http://localhost:{published_port}"

        # If base_url is known (e.g., from prior completion) but published_port is missing,
        # derive it deterministically from the URL.
        if base_url and not published_port:
            try:
                m = re.search(r":(\d+)(?:/|$)", str(base_url))
                if m:
                    published_port = int(m.group(1))
            except Exception:
                pass

        healthcheck_path = health_guess_from_paths(openapi_dict.get("paths"))
        auth = auth_guess_from_paths(openapi_dict.get("paths"))

        # Collect env placeholders from compose
        required_env_vars = extract_required_env_vars(compose_text)

        # Minimal service inventory
        services_inventory: Dict[str, Any] = {}
        for name, svc in services_dict.items():
            if not isinstance(svc, dict):
                continue
            http_ep = _service_http_endpoint(name, svc)
            services_inventory[name] = {
                "ports": parse_ports(svc.get("ports")),
                "depends_on": svc.get("depends_on"),
                "environment": svc.get("environment"),
                "image": svc.get("image"),
                "build": svc.get("build"),
                "http": {
                    "published_port": http_ep.get("published_port"),
                    "base_url": http_ep.get("base_url"),
                },
                "is_infra": bool(http_ep.get("is_infra")),
            }

        # Ranked list of HTTP-capable services (non-infra), so runners can choose.
        http_services: List[Dict[str, Any]] = []
        for name, svc in services_dict.items():
            if not isinstance(svc, dict):
                continue
            ep = _service_http_endpoint(name, svc)
            if ep.get("is_infra"):
                continue
            if not ep.get("base_url"):
                continue

            nl = (name or "").lower()
            score = 0
            if "gateway" in nl:
                score += 100
            if any(k in nl for k in ["api", "backend", "server"]):
                score += 60
            if svc.get("build") is not None:
                score += 15
            if _is_httpish_port(ep.get("published_port")):
                score += 25

            http_services.append(
                {
                    "service": name,
                    "base_url": ep.get("base_url"),
                    "published_port": ep.get("published_port"),
                    "score": score,
                }
            )

        http_services.sort(key=lambda x: x.get("score", 0), reverse=True)

        missing: List[str] = []
        if not compose_path:
            missing.append("run.compose_path")
        if not api_service:
            missing.append("run.api_service")
        if not base_url:
            missing.append("run.base_url")
        if not healthcheck_path:
            missing.append("run.healthcheck_path")

        # DB is required to be explicitly known (yes/no). If required, type/url are required.
        if db_info.get("required") is None:
            missing.append("db.required")
        elif db_info.get("required") is True:
            if not db_info.get("type"):
                missing.append("db.type")
            if not db_info.get("url_env_var"):
                missing.append("db.url_env_var")
        auth_type = auth.get("type")
        login_candidates = auth.get("login_candidates") if isinstance(auth, dict) else None
        has_login_candidates = isinstance(login_candidates, list) and len(login_candidates) > 0

        if auth_type in {None, "unknown"}:
            missing.append("auth")
            # If we detected plausible login endpoints but cannot decide, ask for the full login flow
            # fields in the same questionnaire so the UX doesn't need multiple rounds.
            if has_login_candidates:
                missing.extend([
                    "auth.login.endpoint",
                    "auth.login.username",
                    "auth.login.password",
                ])

        if auth_type == "login_flow":
            if not isinstance(auth.get("login"), dict) or not auth.get("login", {}).get("endpoint"):
                missing.append("auth.login.endpoint")
            missing.extend(["auth.login.username", "auth.login.password"])

        discovery_payload: Dict[str, Any] = {
            "repo": {
                "provider": guess_provider(repo_url),
                "url": repo_url,
                "branch": branch or "main",
            },
            "run": {
                "strategy": "docker_compose",
                "compose_path": compose_path,
                "compose_candidates": compose_candidate_paths,
                "compose_score": compose_score,
                "api_service": api_service,
                "published_port": published_port,
                "base_url": base_url,
                "healthcheck_path": healthcheck_path,
                "http_services": http_services,
            },
            "db": {
                "required": db_info.get("required"),
                "type": db_info.get("type"),
                "service": db_info.get("service"),
                "url_env_var": db_info.get("url_env_var"),
            },
            "auth": auth,
            "secrets_env": {
                "required_env_vars": required_env_vars,
            },
            "services": services_inventory,
            "discovery": {
                "missing": sorted(set(missing)),
                "notes": [],
            },
        }

        openapi_dict["x-discovery"] = discovery_payload

        # Align OpenAPI server URL with discovered base_url when available.
        if base_url:
            servers = openapi_dict.get("servers")
            if isinstance(servers, list) and servers and isinstance(servers[0], dict):
                servers[0]["url"] = base_url
            else:
                openapi_dict["servers"] = [{"url": base_url}]

            info = openapi_dict.get("info")
            if isinstance(info, dict):
                info["x-discovery-base-url"] = base_url

        return openapi_dict

    def _postprocess_openapi_for_autotest(
        self,
        openapi_dict: Dict[str, Any],
        *,
        repo_root: Path,
    ) -> Dict[str, Any]:
        """Make minimal, safe adjustments so the spec is executable for auto-tests.

        - Keep global BearerAuth as-is.
        - Mark known public auth/forgotPassword endpoints with operation-level `security: []`.
        - For `/api/auth/signin`, replace the `{}` response schema with a concrete schema
          only when the token field can be inferred (from code or an existing example).
        """

        if not isinstance(openapi_dict, dict):
            return openapi_dict

        paths = openapi_dict.get("paths")
        if not isinstance(paths, dict):
            return openapi_dict

        def _is_http_method(k: str) -> bool:
            return k.lower() in {"get", "post", "put", "patch", "delete", "head", "options"}

        def _set_public_security(path_key: str, method: str) -> None:
            ops = paths.get(path_key)
            if not isinstance(ops, dict):
                return
            op = ops.get(method)
            if not isinstance(op, dict):
                return
            op["security"] = []

        def _op_uses_refresh_token(op: Dict[str, Any]) -> bool:
            """Heuristic: refresh endpoints often accept a refresh token in body."""
            rb = op.get("requestBody")
            if not isinstance(rb, dict):
                return False
            content = rb.get("content")
            if not isinstance(content, dict):
                return False
            app_json = content.get("application/json")
            if not isinstance(app_json, dict):
                return False
            schema = app_json.get("schema")
            if isinstance(schema, dict):
                props = schema.get("properties")
                if isinstance(props, dict):
                    for k in props.keys():
                        kl = str(k).lower()
                        if kl in {"refreshtoken", "refresh_token"}:
                            return True
            ex = app_json.get("example")
            if isinstance(ex, dict):
                for k in ex.keys():
                    kl = str(k).lower()
                    if kl in {"refreshtoken", "refresh_token"}:
                        return True
            return False

        def _should_mark_public(path_str: str, method: str, op: Dict[str, Any]) -> bool:
            """Framework-agnostic best-effort public endpoint detection."""
            if method.lower() != "post":
                # forgot/reset may be GET/POST; handle separately below
                return False

            pl = (path_str or "").lower()
            # Explicit auth patterns
            if any(seg in pl for seg in ["/auth/signup", "/signup", "/register", "/auth/register"]):
                return True
            if any(seg in pl for seg in ["/auth/signin", "/signin", "/auth/login", "/login"]):
                return True

            # Refresh token endpoints vary a lot; only mark public when we see refresh-token semantics.
            if "refresh" in pl and "token" in pl:
                return _op_uses_refresh_token(op)
            if "refreshtoken" in pl:
                return _op_uses_refresh_token(op)

            return False

        # 1) Public endpoints overrides (general, but still bounded)
        for p, ops in list(paths.items()):
            if not isinstance(p, str) or not isinstance(ops, dict):
                continue

            pl = p.lower()

            # forgot/reset password endpoints are typically public
            if ("forgot" in pl and "password" in pl) or ("reset" in pl and "password" in pl) or ("forgotpassword" in pl):
                for m, op in ops.items():
                    if isinstance(m, str) and _is_http_method(m) and isinstance(op, dict):
                        op["security"] = []

            for m, op in ops.items():
                if not (isinstance(m, str) and _is_http_method(m) and isinstance(op, dict)):
                    continue
                if _should_mark_public(p, m, op):
                    op["security"] = []

        # Explicit override requested: POST /api/auth/refreshToken is public
        _set_public_security("/api/auth/refreshToken", "post")

        # 2) Signin response schema upgrade (only if token field is inferable)
        def _get_signin_200_schema_obj() -> Optional[Dict[str, Any]]:
            ops = paths.get("/api/auth/signin")
            if not isinstance(ops, dict):
                return None
            post = ops.get("post")
            if not isinstance(post, dict):
                return None
            responses = post.get("responses")
            if not isinstance(responses, dict):
                return None
            r200 = responses.get("200")
            if not isinstance(r200, dict):
                return None
            content = r200.get("content")
            if not isinstance(content, dict):
                return None
            app_json = content.get("application/json")
            if not isinstance(app_json, dict):
                return None
            schema_obj = app_json.get("schema")
            if isinstance(schema_obj, dict):
                return app_json
            return None

        def _infer_token_field_from_example(example_obj: Any) -> Optional[str]:
            if not isinstance(example_obj, dict):
                return None
            candidates = list(example_obj.keys())
            if not candidates:
                return None
            priority = [
                "access_token",
                "accessToken",
                "token",
                "jwt",
                "idToken",
                "id_token",
            ]
            for k in priority:
                if k in example_obj:
                    return k
            # fallback: any key containing token
            for k in candidates:
                if "token" in str(k).lower():
                    return str(k)
            return None

        def _extract_java_fields(java_text: str) -> List[str]:
            fields: List[str] = []
            if not java_text:
                return fields

            # records: record Name(type field, ...)
            m = re.search(r"\brecord\s+\w+\s*\(([^)]*)\)", java_text)
            if m:
                inside = m.group(1)
                for part in inside.split(","):
                    part = part.strip()
                    if not part:
                        continue
                    # "Type name" -> capture name
                    bits = part.split()
                    if len(bits) >= 2:
                        fields.append(bits[-1].strip())

            # common private field declarations
            for m2 in re.finditer(r"\bprivate\s+[\w<>\[\]]+\s+(\w+)\s*;", java_text):
                fields.append(m2.group(1))

            return sorted(set([f for f in fields if f]))

        def _infer_token_field_from_repo(root: Path) -> Optional[str]:
            """Infer token field name without framework assumptions.

            Strategy:
            - Search common config/docs/tests/postman files for token-like JSON keys.
            - If Java DTOs exist, also check their fields (kept as a fallback).
            """

            token_priority = [
                "access_token",
                "accessToken",
                "token",
                "jwt",
                "idToken",
                "id_token",
            ]

            key_regex = re.compile(
                r"(?i)(?:\"|')(?P<k>access_token|accesstoken|token|jwt|id_token|idtoken)(?:\"|')\s*:")

            exts = {
                ".json",
                ".md",
                ".txt",
                ".yaml",
                ".yml",
                ".http",
                ".rest",
                ".js",
                ".ts",
                ".py",
                ".java",
                ".php",
                ".go",
                ".cs",
                ".sh",
            }

            filename_hints = [
                "auth",
                "signin",
                "login",
                "jwt",
                "token",
                "postman",
                "insomnia",
                "collection",
                "test",
                "swagger",
                "openapi",
            ]

            # 1) scan likely files first
            files: List[Path] = []
            try:
                for p in root.rglob("*"):
                    if not p.is_file():
                        continue
                    if p.suffix.lower() not in exts:
                        continue
                    rel = str(p.relative_to(root)).lower()
                    score = sum(1 for h in filename_hints if h in rel)
                    if score > 0:
                        files.append(p)
                    if len(files) >= 600:
                        break
            except Exception:
                files = []

            # 2) add a small fallback set if nothing matched
            if not files:
                try:
                    for p in root.rglob("*.json"):
                        if p.is_file():
                            files.append(p)
                        if len(files) >= 200:
                            break
                except Exception:
                    pass

            found_keys: List[str] = []
            for p in files[:600]:
                try:
                    if p.stat().st_size > 600_000:
                        continue
                    txt = p.read_text(encoding="utf-8", errors="ignore")
                except Exception:
                    continue

                # Try direct matches in priority order to keep stable
                for k in token_priority:
                    if re.search(rf"(?i)(?:\"|'){re.escape(k)}(?:\"|')\s*:", txt):
                        found_keys.append(k)
                if found_keys:
                    # return best priority key seen
                    for k in token_priority:
                        if k in found_keys:
                            return k

                # fallback: generic regex match
                m = key_regex.search(txt)
                if m:
                    raw = (m.group("k") or "").strip()
                    # normalize known camel/snake
                    if raw.lower() == "accesstoken":
                        return "accessToken"
                    if raw.lower() == "idtoken":
                        return "idToken"
                    if raw.lower() == "id_token":
                        return "id_token"
                    if raw.lower() == "access_token":
                        return "access_token"
                    if raw.lower() == "token":
                        return "token"
                    if raw.lower() == "jwt":
                        return "jwt"

            # 3) Java DTO field fallback (if repo is Java)
            try:
                java_files = list(root.rglob("*.java"))
            except Exception:
                java_files = []

            name_hints = [
                "jwtauthenticationresponse",
                "authenticationresponse",
                "authresponse",
                "tokenresponse",
                "jwtresponse",
                "signinresponse",
                "loginresponse",
            ]

            candidates: List[Path] = []
            for p in java_files[:2000]:
                n = p.name.lower()
                if any(h in n for h in name_hints):
                    candidates.append(p)
                    if len(candidates) >= 30:
                        break

            for p in candidates:
                try:
                    txt = p.read_text(encoding="utf-8", errors="ignore")
                except Exception:
                    continue
                fields = _extract_java_fields(txt)
                if not fields:
                    continue
                for k in token_priority:
                    if k in fields:
                        return k
                for f in fields:
                    if "token" in f.lower():
                        return f

            return None

        # Flexible token schema usable by runners even when the exact field name is unknown.
        def _ensure_auth_tokens_schema() -> None:
            components = openapi_dict.setdefault("components", {})
            if not isinstance(components, dict):
                return
            schemas = components.setdefault("schemas", {})
            if not isinstance(schemas, dict):
                return
            if "AuthTokensResponse" in schemas:
                return

            schemas["AuthTokensResponse"] = {
                "type": "object",
                "description": "Flexible auth token response (field names vary by implementation).",
                "properties": {
                    "access_token": {"type": "string"},
                    "accessToken": {"type": "string"},
                    "token": {"type": "string"},
                    "jwt": {"type": "string"},
                    "id_token": {"type": "string"},
                    "idToken": {"type": "string"},
                    "refresh_token": {"type": "string"},
                    "refreshToken": {"type": "string"},
                    "expiresIn": {"type": "integer"},
                    "expires_in": {"type": "integer"},
                    "tokenType": {"type": "string"},
                    "token_type": {"type": "string"},
                },
                "additionalProperties": True,
            }

        def _set_200_response_schema_to_tokens(path_key: str) -> None:
            ops = paths.get(path_key)
            if not isinstance(ops, dict):
                return
            post = ops.get("post")
            if not isinstance(post, dict):
                return
            responses = post.get("responses")
            if not isinstance(responses, dict):
                return
            r200 = responses.get("200")
            if not isinstance(r200, dict):
                return
            content = r200.get("content")
            if not isinstance(content, dict):
                return
            app_json = content.get("application/json")
            if not isinstance(app_json, dict):
                return
            _ensure_auth_tokens_schema()
            app_json["schema"] = {"$ref": "#/components/schemas/AuthTokensResponse"}

        # Apply to any login-ish endpoint (signin/login) rather than a single hard-coded path.
        login_paths: List[str] = []
        for p, ops in paths.items():
            if not (isinstance(p, str) and isinstance(ops, dict)):
                continue
            pl = p.lower()
            if any(k in pl for k in ["/signin", "/login"]):
                if isinstance(ops.get("post"), dict):
                    login_paths.append(p)

        # If the repo contains any login endpoint, make the 200 response schema runnable.
        if login_paths:
            for login_path in login_paths:
                _set_200_response_schema_to_tokens(login_path)

        # Refresh token responses commonly return a new access token too.
        _set_200_response_schema_to_tokens("/api/auth/refreshToken")

        return openapi_dict

    def run(self, repo_url: str, branch: Optional[str] = None, keep_repo: bool = False) -> Dict[str, Any]:
        """
        Run the complete pipeline.

        Args:
            repo_url: GitHub repository URL
            branch: Optional branch to clone
            keep_repo: Keep cloned repository for debugging

        Returns:
            Dictionary with results and stats

        Raises:
            PipelineError: If pipeline fails
        """
        repo_path = None
        was_cached = False
        
        try:
            self.logger.info("Starting pipeline", repo_url=repo_url, branch=branch)

            # Step 1: Clone repository
            repo_path, was_cached, repo_id = self.repo_manager.clone_repository(repo_url, branch, keep_repo=keep_repo)
            self.file_scanner.repo_path = repo_path
            self.route_resolver.repo_path = repo_path
            self.route_resolver.file_scanner = self.file_scanner

            # Step 2: Detect tech stack
            self.logger.info("Detecting tech stack", repo_path=str(repo_path))
            tech_detector = TechStackDetector(repo_path)
            tech_stack = tech_detector.detect()

            # Layer 1: Spec-first short-circuit
            self.logger.info("Layer 1: Spec-first import")
            spec_result = self.spec_first_extractor.try_extract(repo_path)
            if spec_result and spec_result.short_circuit:
                self.stats.spec_files_found = 1
                self.stats.endpoints_from_spec = len(spec_result.endpoints)

                # Process endpoints (normalize/dedupe) even in spec-first mode
                processing_result = self.endpoint_processor.process(spec_result.endpoints, {})
                final_endpoints = processing_result.endpoints

                # Ensure endpoints include request templates for AI-driven test generation
                self._ensure_request_templates(spec_result.endpoints)
                self._ensure_request_templates(final_endpoints)

                self.stats.total_files = 0
                self.stats.files_processed = 0
                self.stats.files_failed = 0
                self.stats.endpoints_found = len(final_endpoints)
                self.stats.duplicates_removed = processing_result.duplicates_removed
                self.stats.end_time = datetime.utcnow()

                # If we imported an OpenAPI/Swagger file, output it directly.
                # For Postman/Insomnia, generate OpenAPI from extracted endpoints.
                if spec_result.openapi and isinstance(spec_result.openapi, dict):
                    openapi_out: Dict[str, Any] = spec_result.openapi
                else:
                    openapi_spec = OpenAPISpec.from_endpoints_with_repo(
                        endpoints=final_endpoints,
                        repo_root=repo_path,
                        title=f"API Specification - {repo_id}",
                        description=f"Auto-generated from repository {repo_url}",
                        version="1.0.0",
                    )
                    openapi_out = openapi_spec.to_dict()

                # Save outputs
                openapi_out = self._enrich_openapi_with_discovery(
                    openapi_out,
                    repo_url=repo_url,
                    branch=branch,
                    repo_root=repo_path,
                )
                openapi_out = self._postprocess_openapi_for_autotest(openapi_out, repo_root=repo_path)
                self._save_outputs(openapi_out, spec_result.endpoints, repo_id)

                result = {
                    "success": True,
                    "repo_url": repo_url,
                    "repo_id": repo_id,
                    "repo_path": str(repo_path) if repo_path else None,
                    "repo_cached": was_cached,
                    "openapi": openapi_out,
                    "raw_endpoints": [e.to_dict() for e in spec_result.endpoints],
                    "final_endpoints": [e.to_dict() for e in final_endpoints],
                    "stats": self.stats.to_dict(),
                    "tech_stack": {
                        "languages": list(tech_stack.languages),
                        "frameworks": tech_stack.frameworks,
                        "has_graphql": tech_stack.has_graphql,
                        "has_rest": tech_stack.has_rest,
                    },
                }

                self.logger.info("Pipeline completed via spec-first", endpoints=len(final_endpoints))
                return result

            # Step 3: Scan files
            self.logger.info("Scanning files")
            candidate_files = self.file_scanner.scan_files()
            self.stats.total_files = len(candidate_files)

            # Step 4: Resolve route prefixes
            self.logger.info("Resolving route prefixes")
            prefix_mapping = self.route_resolver.resolve_prefixes(candidate_files)

            # Step 5: Parse files (AST → Regex → LLM)
            self.logger.info("Parsing files for endpoints")
            # Layer 2: Config routes
            self.logger.info("Layer 2: Config routes")
            config_endpoints, config_files_parsed = self.config_routes_extractor.extract(repo_path)
            self.stats.config_files_parsed = config_files_parsed
            self.stats.endpoints_from_config = len(config_endpoints)

            all_endpoints = []
            all_endpoints.extend(config_endpoints)

            # Layer 3: Code routes (AST → Regex → LLM)
            all_endpoints.extend(self._parse_files(candidate_files, tech_stack, prefix_mapping))

            # Enrich Spring endpoints with structured request input schemas (DTO attributes, query/header/path)
            self._enrich_spring_requests(all_endpoints, repo_path, candidate_files)

            # Layer 4: Universal fallback
            self.logger.info("Layer 4: Universal fallback")
            universal_endpoints, universal_files_scanned = self.universal_fallback_extractor.extract(repo_path)
            self.stats.universal_files_scanned = universal_files_scanned

            # Add universal endpoints that aren't obvious duplicates (final dedupe happens later)
            all_endpoints.extend(universal_endpoints)
            self.stats.endpoints_from_universal = len(universal_endpoints)

            # Drop gateway routing wildcards (e.g., '/svc/**') from outputs.
            # These are not real endpoints and create noisy, non-testable artifacts.
            all_endpoints = self._filter_gateway_wildcard_endpoints(all_endpoints)

            # Step 6: Process endpoints (normalize, deduplicate)
            self.logger.info("Processing endpoints")
            processing_result = self.endpoint_processor.process(all_endpoints, prefix_mapping)
            final_endpoints = processing_result.endpoints

            # Ensure final endpoints also have Spring request schemas (processor creates new Endpoint objects)
            self._enrich_spring_requests(final_endpoints, repo_path, candidate_files)

            # Ensure endpoints include request templates for AI-driven test generation
            self._ensure_request_templates(all_endpoints)
            self._ensure_request_templates(final_endpoints)

            # Optional final-stage LLM verification of request templates.
            # This is best-effort and only applies safe, schema-validated fixes.
            self._llm_verify_request_templates(final_endpoints)
            # Ensure any remaining gaps are filled deterministically.
            self._ensure_request_templates(final_endpoints)

            # Update stats
            self.stats.files_processed = self.stats.total_files - self.stats.files_failed
            self.stats.endpoints_found = len(final_endpoints)
            self.stats.duplicates_removed = processing_result.duplicates_removed
            self.stats.end_time = datetime.utcnow()

            # Step 7: Generate OpenAPI spec
            self.logger.info("Generating OpenAPI specification")
            openapi_spec = OpenAPISpec.from_endpoints_with_repo(
                endpoints=final_endpoints,
                repo_root=repo_path,
                title=f"API Specification - {repo_id}",
                description=f"Auto-generated from repository {repo_url}",
                version="1.0.0"
            )

            # Validate generated OpenAPI (best-effort). In strict verify mode, fail if invalid.
            openapi_out = openapi_spec.to_dict()
            self._validate_openapi_or_fail(openapi_out)

            openapi_out = self._enrich_openapi_with_discovery(
                openapi_out,
                repo_url=repo_url,
                branch=branch,
                repo_root=repo_path,
            )
            openapi_out = self._postprocess_openapi_for_autotest(openapi_out, repo_root=repo_path)

            # Step 8: Save outputs
            self._save_outputs(openapi_out, all_endpoints, repo_id)

            # Return results (cleanup happens in finally block)
            result = {
                "success": True,
                "repo_url": repo_url,
                "repo_id": repo_id,
                "repo_path": str(repo_path) if repo_path else None,
                "repo_cached": was_cached,
                "openapi": openapi_out,
                "raw_endpoints": [e.to_dict() for e in all_endpoints],
                "final_endpoints": [e.to_dict() for e in final_endpoints],
                "stats": self.stats.to_dict(),
                "tech_stack": {
                    "languages": list(tech_stack.languages),
                    "frameworks": tech_stack.frameworks,
                    "has_graphql": tech_stack.has_graphql,
                    "has_rest": tech_stack.has_rest,
                }
            }

            self.logger.info("Pipeline completed successfully", endpoints=len(final_endpoints))
            return result

        except Exception as e:
            self.logger.error("Pipeline failed", error=str(e))
            self.stats.end_time = datetime.utcnow()
            raise PipelineError(f"Pipeline failed: {str(e)}") from e

        finally:
            # Ensure cleanup happens in all cases (success or failure)
            # Keep repo only if explicitly requested OR if it was cached (to reuse later)
            should_cleanup = repo_path and not keep_repo
            
            if should_cleanup:
                self.logger.info("Cleaning up repository", repo_path=str(repo_path))
                try:
                    self.repo_manager.cleanup_repository(repo_path, keep=False)
                    self.logger.info("Repository cleanup completed")
                except Exception as e:
                    self.logger.error(
                        "Repository cleanup failed - attempting force cleanup",
                        repo_path=str(repo_path),
                        error=str(e)
                    )
                    try:
                        # Force cleanup with more aggressive approach
                        import shutil
                        import gc
                        
                        # Release any file handles
                        gc.collect()
                        
                        # Windows: retry cleanup with force
                        if repo_path.exists():
                            shutil.rmtree(repo_path, ignore_errors=True)
                            self.logger.info("Force cleanup completed", repo_path=str(repo_path))
                    except Exception as force_error:
                        self.logger.warning(
                            "Force cleanup also failed - repository may need manual cleanup",
                            repo_path=str(repo_path),
                            error=str(force_error)
                        )
            elif keep_repo:
                self.logger.info("Repository kept for debugging", repo_path=str(repo_path))
            elif was_cached:
                self.logger.info("Repository kept for cache reuse", repo_path=str(repo_path))

    def _parse_files(
        self,
        file_paths: List[Path],
        tech_stack: TechStack,
        prefix_mapping: Dict[str, str]
    ) -> List[Endpoint]:
        """
        Parse all candidate files using three-stage pipeline: AST → Regex → LLM.

        Args:
            file_paths: List of file paths to parse
            tech_stack: Detected tech stack
            prefix_mapping: Router prefix mapping

        Returns:
            List of all extracted endpoints
        """
        all_endpoints = []
        file_infos = []

        # Prepare file info list
        for file_path in file_paths:
            language = self.file_scanner.get_language_from_extension(file_path)
            router_prefix = prefix_mapping.get(str(file_path))

            file_infos.append({
                "path": file_path,
                "language": language,
                "router_prefix": router_prefix
            })

        # ========== STAGE 1: AST PARSING ==========
        self.logger.info("Stage 1: AST parsing", count=len(file_infos))
        ast_results = self.ast_parser.parse_files_parallel(file_infos)

        # Collect AST endpoints and track files with low endpoint counts
        files_needing_regex = []
        
        for result in ast_results:
            all_endpoints.extend(result.endpoints)
            self.stats.endpoints_from_ast += len(result.endpoints)
            
            if result.errors:
                self.stats.errors.extend(result.errors)
                self.stats.files_failed += 1
            
            # Track files with few/no endpoints for regex fallback
            if len(result.endpoints) < 2:  # Files with 0-1 endpoints need regex
                files_needing_regex.append(result)

        self.logger.info("Stage 1 complete", ast_endpoints=len(all_endpoints), files_needing_regex=len(files_needing_regex))

        # ========== STAGE 2: REGEX EXTRACTION (Fallback) ==========
        self.logger.info("Stage 2: Regex extraction for files with few endpoints", count=len(files_needing_regex))
        
        for parse_result in files_needing_regex:
            try:
                file_path = Path(parse_result.file_path) if isinstance(parse_result.file_path, str) else parse_result.file_path
                language = self.file_scanner.get_language_from_extension(file_path)
                
                if language not in ['javascript', 'typescript', 'js', 'ts', 'jsx', 'tsx', 'python', 'py', 'java', 'php', 'go', 'csharp', 'cs']:
                    continue
                
                # Read file content
                with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                    code = f.read()
                
                # Extract using regex
                regex_endpoints = self.regex_extractor.extract_endpoints(code, file_path, language)
                
                # Filter out duplicates with AST results
                new_endpoints = []
                for ep in regex_endpoints:
                    # Check if this endpoint is already found by AST
                    is_duplicate = any(
                        ast_ep.method == ep.method and ast_ep.path == ep.path
                        for ast_ep in all_endpoints
                        if ast_ep.file_path == str(file_path)
                    )
                    if not is_duplicate:
                        new_endpoints.append(ep)
                
                all_endpoints.extend(new_endpoints)
                self.stats.endpoints_from_regex += len(new_endpoints)
                
                if new_endpoints:
                    self.logger.info(
                        "Regex extraction found new endpoints",
                        file=file_path.name,
                        count=len(new_endpoints)
                    )
                
            except Exception as e:
                self.logger.warning("Regex extraction failed for file", file=str(file_path), error=str(e))

        self.logger.info("Stage 2 complete", regex_endpoints=self.stats.endpoints_from_regex)

        # ========== STAGE 3: LLM EXTRACTION (High confidence) ==========
        if self.llm_extractor and self.llm_extractor.is_available():
            self.logger.info("Stage 3: LLM extraction for low-confidence endpoints")
            
            # Find endpoints below confidence threshold
            low_confidence_by_file: Dict[str, List[Endpoint]] = {}
            for ep in all_endpoints:
                if ep.confidence < self.config.llm_confidence_threshold:
                    file_key = ep.file_path
                    if file_key not in low_confidence_by_file:
                        low_confidence_by_file[file_key] = []
                    low_confidence_by_file[file_key].append(ep)
            
            self.logger.info("Files with low-confidence endpoints", count=len(low_confidence_by_file))

            # Optional: force a few LLM calls for validation/testing.
            # This makes it possible to test the local LLM integration even when
            # earlier stages found no low-confidence endpoints.
            if self.config.llm_force and not low_confidence_by_file:
                allowed_langs = {
                    'javascript', 'typescript', 'js', 'ts', 'jsx', 'tsx',
                    'python', 'py',
                    'java',
                    'php',
                    'go',
                    'csharp', 'cs',
                }
                max_files = max(1, int(self.config.llm_force_max_files or 1))
                forced: List[str] = []
                for info in file_infos:
                    try:
                        fp = info.get('path')
                        lang = (info.get('language') or '').lower()
                        if not fp or lang not in allowed_langs:
                            continue
                        forced.append(str(fp))
                        if len(forced) >= max_files:
                            break
                    except Exception:
                        continue

                if forced:
                    low_confidence_by_file = {p: [] for p in forced}
                    self.logger.info("LLM_FORCE enabled: forcing LLM calls", count=len(forced))
                else:
                    self.logger.info("LLM_FORCE enabled but no candidate files found")
            
            # Process files with low-confidence endpoints using LLM
            for file_path_str, low_conf_endpoints in low_confidence_by_file.items():
                try:
                    file_path = Path(file_path_str)
                    language = self.file_scanner.get_language_from_extension(file_path)
                    router_prefix = prefix_mapping.get(str(file_path))
                    
                    # Read file content
                    with open(file_path, 'r', encoding='utf-8', errors='ignore') as f:
                        code = f.read()
                    
                    # Don't send files that are too large to LLM
                    if len(code) > 10000:
                        self.logger.debug("File too large for LLM", file=file_path.name, size=len(code))
                        continue
                    
                    # Call LLM
                    self.logger.debug("Calling LLM for file", file=file_path.name)
                    self.stats.llm_calls += 1
                    llm_result = self.llm_extractor.extract_endpoints(code, file_path, language, router_prefix)
                    
                    # Add high-confidence LLM endpoints
                    for ep in llm_result.endpoints:
                        if ep.confidence >= self.config.llm_confidence_threshold:
                            # Check if duplicate
                            is_duplicate = any(
                                e.method == ep.method and e.path == ep.path
                                for e in all_endpoints
                                if e.file_path == str(file_path)
                            )
                            if not is_duplicate:
                                all_endpoints.append(ep)
                                self.stats.endpoints_from_llm += 1
                    
                    self.logger.info(
                        "LLM extraction complete",
                        file=file_path.name,
                        llm_endpoints=len(llm_result.endpoints),
                        high_confidence=len([e for e in llm_result.endpoints if e.confidence >= self.config.llm_confidence_threshold])
                    )
                    
                except LLMError as e:
                    self.stats.llm_failed += 1
                    self.logger.warning("LLM extraction failed", file=file_path_str, error=str(e))
                except Exception as e:
                    self.stats.llm_failed += 1
                    self.logger.error("Unexpected error in LLM extraction", file=file_path_str, error=str(e))
            
            self.logger.info("Stage 3 complete", llm_endpoints=self.stats.endpoints_from_llm, llm_calls=self.stats.llm_calls)
        else:
            self.logger.info("Stage 3 skipped: LLM extractor not available")

        self.stats.files_processed = len(file_infos) - self.stats.files_failed
        return all_endpoints

    def _enrich_spring_requests(self, endpoints: List[Endpoint], repo_path: Path, candidate_files: List[Path]) -> None:
        """Best-effort enrichment for Spring Boot endpoints.

        Adds/updates endpoint.metadata['request'] to include:
        - path/query/header parameters with schema + required
        - request body schema for @RequestBody DTOs
        """
        try:
            import re

            def example_from_schema(schema: Any, name_hint: Optional[str] = None, depth: int = 0) -> Any:
                if not isinstance(schema, dict):
                    return None
                if depth > 6:
                    return {}

                if "example" in schema:
                    return schema.get("example")
                enum = schema.get("enum")
                if isinstance(enum, list) and enum:
                    return enum[0]

                schema_type = schema.get("type")
                fmt = schema.get("format")
                hint = (name_hint or "").lower()

                if schema_type == "string" or schema_type is None:
                    if fmt == "uuid":
                        return "00000000-0000-0000-0000-000000000000"
                    if fmt == "date":
                        return "2020-01-01"
                    if fmt == "date-time":
                        return "2020-01-01T00:00:00Z"
                    if fmt == "binary":
                        return "<binary>"
                    if "email" in hint:
                        return "user@example.com"
                    if "token" in hint or "auth" in hint:
                        return "token"
                    if hint.endswith("id") or hint == "id" or "_id" in hint:
                        return "1"
                    return "string"

                if schema_type == "integer":
                    if hint.endswith("id") or hint == "id" or "_id" in hint:
                        return 1
                    if "page" in hint or "size" in hint or "limit" in hint:
                        return 1
                    return 0

                if schema_type == "number":
                    return 0.0

                if schema_type == "boolean":
                    return True

                if schema_type == "array":
                    items = schema.get("items")
                    return [example_from_schema(items, name_hint=name_hint, depth=depth + 1)]

                if schema_type == "object":
                    props = schema.get("properties")
                    if isinstance(props, dict) and props:
                        out: Dict[str, Any] = {}
                        for k, v in props.items():
                            out[k] = example_from_schema(v, name_hint=k, depth=depth + 1)
                        return out
                    add_props = schema.get("additionalProperties")
                    if isinstance(add_props, dict):
                        return {"key": example_from_schema(add_props, name_hint="key", depth=depth + 1)}
                    return {}

                return None

            def is_empty_object_schema(schema: Any) -> bool:
                if not isinstance(schema, dict):
                    return True
                if schema.get("type") != "object":
                    return False
                props = schema.get("properties")
                if isinstance(props, dict) and props:
                    return False
                return True

            def should_refresh_example(existing: Any, schema: Any) -> bool:
                if existing is None:
                    return True
                if existing == {} and isinstance(schema, dict) and isinstance(schema.get("properties"), dict) and schema.get("properties"):
                    return True
                if existing == [] and isinstance(schema, dict) and schema.get("type") == "array":
                    return True
                return False

            # Build DTO index from all Java files in repo (less aggressive ignores than endpoint scan).
            # This avoids missing DTOs in multi-module repos or under non-standard folders.
            ignored = set(getattr(FileScanner, "IGNORED_DIRS", set()))
            # Keep test folders for DTO lookup (sometimes requests live in shared test modules)
            ignored.discard("test")
            ignored.discard("tests")
            ignored.discard("spec")
            ignored.discard("specs")

            java_files: List[Path] = []
            try:
                for p in repo_path.rglob("*.java"):
                    if any(part in ignored for part in p.parts):
                        continue
                    if not p.is_file():
                        continue
                    try:
                        if p.stat().st_size > 1_000_000:
                            continue
                    except OSError:
                        continue
                    java_files.append(p)
            except Exception:
                # Fallback to scanned candidates
                java_files = [p for p in candidate_files if p.suffix.lower() == ".java"]

            dto_extractor = JavaDTOExtractor.from_java_files(repo_path, java_files)

            for ep in endpoints:
                meta = ep.metadata or {}
                req = meta.get("request")
                if not isinstance(req, dict):
                    continue

                # If Spring param parsing missed @PathVariable, infer from the route template.
                existing_path = req.get("path") if isinstance(req.get("path"), list) else []
                existing_names = {p.get("name") for p in existing_path if isinstance(p, dict)}
                full_path = getattr(ep, "full_path", "") or ep.path
                for name in re.findall(r"\{([^}]+)\}", full_path or ""):
                    if name in existing_names:
                        continue
                    existing_path.append({
                        "name": name,
                        "java_type": "String",
                        "required": True,
                        "schema": {"type": "string"},
                        "example": "1" if name.lower().endswith("id") else "string",
                    })
                req["path"] = existing_path

                # Normalize param lists with schema mapping
                for section in ("path", "query", "header"):
                    items = req.get(section)
                    if not isinstance(items, list):
                        continue
                    for item in items:
                        if not isinstance(item, dict):
                            continue
                        jt = item.get("java_type") or "string"
                        item.setdefault("schema", java_type_to_schema(str(jt)))
                        # Path params must be required for OpenAPI
                        if section == "path":
                            item["required"] = True
                        else:
                            item.setdefault("required", True)

                        # Provide a concrete example value for test generation
                        if "example" not in item or should_refresh_example(item.get("example"), item.get("schema")):
                            item["example"] = example_from_schema(item.get("schema"), name_hint=item.get("name"))

                body = req.get("body")
                if isinstance(body, dict):
                    dto_type = (body.get("dto_type") or "").strip()
                    dto_type_simple = dto_type.split(".")[-1].strip()
                    base = dto_type_simple.split("<", 1)[0].strip() if dto_type_simple else ""

                    # 1) If body type is a collection/scalar/generic, map it directly (keep generics so items types work).
                    # 2) Else, try to resolve a DTO class and extract its fields.
                    if dto_type:
                        is_generic = "<" in dto_type_simple and ">" in dto_type_simple
                        is_array = dto_type_simple.endswith("[]")
                        is_collectionish = base in {"List", "Set", "Collection", "Iterable", "Map", "HashMap", "LinkedHashMap"}
                        is_scalarish = base in {"String", "Integer", "Long", "Double", "Float", "Boolean", "UUID", "BigDecimal"}

                        if is_generic or is_array or is_collectionish or is_scalarish:
                            body["schema"] = java_type_to_schema(dto_type_simple)
                        else:
                            schema = dto_extractor.extract_schema(base, expand_refs_depth=3) if base else None
                            if schema is not None:
                                body["schema"] = schema
                            else:
                                # Keep placeholder if it contains type info, but ensure non-empty schema exists.
                                existing = body.get("schema")
                                if not isinstance(existing, dict) or not existing or is_empty_object_schema(existing):
                                    body["schema"] = java_type_to_schema(base or dto_type_simple)

                    # Multipart fallback
                    if body.get("content_type") == "multipart/form-data":
                        # Ensure at least a file part if nothing extracted
                        schema = body.get("schema")
                        if isinstance(schema, dict):
                            schema.setdefault("type", "object")
                            props = schema.setdefault("properties", {})
                            field_name = body.get("multipart_field") or "file"
                            props.setdefault(str(field_name), {"type": "string", "format": "binary"})
                            req_list = schema.setdefault("required", [])
                            if field_name not in req_list:
                                req_list.append(field_name)

                    # Provide a concrete example body payload
                    if "example" not in body or should_refresh_example(body.get("example"), body.get("schema")):
                        body["example"] = example_from_schema(body.get("schema"), name_hint=body.get("dto_type") or "body")

                ep.metadata = meta
        except Exception as e:
            # Enrichment is best-effort; do not fail the pipeline.
            self.logger.debug("Spring request enrichment failed", error=str(e))

    def _ensure_request_templates(self, endpoints: List[Endpoint]) -> None:
        """Ensure every endpoint carries a consistent request template.

        This makes both `*_endpoints.json` and the generated OpenAPI usable for an AI agent
        that needs to synthesize concrete HTTP tests.
        """
        import re

        def example_from_schema(schema: Any, name_hint: Optional[str] = None, depth: int = 0) -> Any:
            if not isinstance(schema, dict):
                return None
            if depth > 6:
                return {}

            if "example" in schema:
                return schema.get("example")
            enum = schema.get("enum")
            if isinstance(enum, list) and enum:
                return enum[0]

            schema_type = schema.get("type")
            fmt = schema.get("format")
            hint = (name_hint or "").lower()

            if schema_type == "string" or schema_type is None:
                if fmt == "uuid":
                    return "00000000-0000-0000-0000-000000000000"
                if fmt == "date":
                    return "2020-01-01"
                if fmt == "date-time":
                    return "2020-01-01T00:00:00Z"
                if fmt == "binary":
                    return "<binary>"
                if "email" in hint:
                    return "user@example.com"
                if "token" in hint or "auth" in hint:
                    return "token"
                if hint.endswith("id") or hint == "id" or "_id" in hint:
                    return "1"
                return "string"

            if schema_type == "integer":
                if hint.endswith("id") or hint == "id" or "_id" in hint:
                    return 1
                if "page" in hint or "size" in hint or "limit" in hint:
                    return 1
                return 0

            if schema_type == "number":
                return 0.0

            if schema_type == "boolean":
                return True

            if schema_type == "array":
                items = schema.get("items")
                return [example_from_schema(items, name_hint=name_hint, depth=depth + 1)]

            if schema_type == "object":
                props = schema.get("properties")
                if isinstance(props, dict) and props:
                    out: Dict[str, Any] = {}
                    for k, v in props.items():
                        out[k] = example_from_schema(v, name_hint=k, depth=depth + 1)
                    return out
                add_props = schema.get("additionalProperties")
                if isinstance(add_props, dict):
                    return {"key": example_from_schema(add_props, name_hint="key", depth=depth + 1)}
                return {}

            return None

        def extract_path_params(path: str) -> List[str]:
            if not path:
                return []
            names: List[str] = []
            names.extend(re.findall(r"\{([^}]+)\}", path))
            names.extend(re.findall(r"(?<!\w):([A-Za-z_][A-Za-z0-9_]*)", path))
            seen = set()
            out: List[str] = []
            for n in names:
                if n in seen:
                    continue
                seen.add(n)
                out.append(n)
            return out

        for ep in endpoints:
            meta = ep.metadata if isinstance(ep.metadata, dict) else {}
            req = meta.get("request")
            if not isinstance(req, dict):
                req = {}
                meta["request"] = req

            req.setdefault("path", [])
            req.setdefault("query", [])
            req.setdefault("header", [])
            req.setdefault("body", None)

            existing_path = req.get("path") if isinstance(req.get("path"), list) else []
            existing_names = {p.get("name") for p in existing_path if isinstance(p, dict)}
            for name in extract_path_params(getattr(ep, "full_path", "") or ep.path):
                if name in existing_names:
                    continue
                schema = {"type": "string"}
                existing_path.append({
                    "name": name,
                    "java_type": "String",
                    "required": True,
                    "schema": schema,
                    "example": example_from_schema(schema, name_hint=name),
                })
            req["path"] = existing_path

            if ep.method.value in {"post", "put", "patch"} and req.get("body") is None:
                # Avoid inventing a placeholder JSON body when the endpoint only has
                # path params (common for routes like /reset/{token}). If we can't
                # confidently infer a body from code, keep it as null.
                path_param_names = {p.get("name") for p in req.get("path", []) if isinstance(p, dict)}
                candidates = [p for p in (ep.parameters or []) if p and p not in path_param_names]
                if candidates:
                    body_param = candidates[-1]
                    schema = {"type": "object"}
                    req["body"] = {
                        "param": body_param,
                        "dto_type": None,
                        "content_type": "application/json",
                        # This is an inferred body (not extracted from framework annotations),
                        # so do not mark it as required.
                        "required": False,
                        "schema": schema,
                        "example": example_from_schema(schema, name_hint=body_param),
                    }

            ep.metadata = meta

    def _llm_verify_request_templates(self, endpoints: List[Endpoint]) -> None:
        """Ask the configured LLM to verify request templates and apply safe corrections.

        This stage is optional (Config.llm_verify). It is designed for improving test readiness,
        not for inventing new DTO fields.
        """
        try:
            if not getattr(self.config, "llm_verify", False):
                return
            if not self.llm_extractor or not getattr(self.llm_extractor, "is_available", lambda: False)():
                self.logger.warning("LLM verify requested but LLM extractor unavailable; skipping")
                return

            max_endpoints = int(getattr(self.config, "llm_verify_max_endpoints", 40))
            chunk_size = int(getattr(self.config, "llm_verify_chunk_size", 15))
            strict = bool(getattr(self.config, "llm_verify_strict", False))

            # Create a compact payload for the LLM
            payload: List[dict] = []
            for ep in endpoints[:max_endpoints]:
                req = (ep.metadata or {}).get("request") if isinstance(ep.metadata, dict) else None
                if not isinstance(req, dict):
                    continue
                payload.append({
                    "method": ep.method.value,
                    "full_path": getattr(ep, "full_path", "") or ep.path,
                    "request": req,
                })

            if not payload:
                return

            issues: List[str] = []
            updates_applied = 0

            for i in range(0, len(payload), max(1, chunk_size)):
                chunk = payload[i:i + chunk_size]
                out = self.llm_extractor.verify_request_templates(chunk)
                chunk_issues = out.get("issues")
                if isinstance(chunk_issues, list):
                    issues.extend([str(x) for x in chunk_issues if x])

                updates = out.get("updates")
                if not isinstance(updates, list):
                    continue

                # Apply safe updates: replace only the `request` object for matching endpoints.
                for upd in updates:
                    if not isinstance(upd, dict):
                        continue
                    method = str(upd.get("method") or "").lower()
                    full_path = str(upd.get("full_path") or "")
                    req = upd.get("request")
                    if method not in {"get", "post", "put", "delete", "patch"}:
                        continue
                    if not full_path:
                        continue
                    if not isinstance(req, dict):
                        continue

                    # Basic structure validation
                    for key in ("path", "query", "header"):
                        if key not in req:
                            req[key] = []
                        if not isinstance(req.get(key), list):
                            req[key] = []
                    if "body" not in req:
                        req["body"] = None

                    # Find endpoint and apply
                    for ep in endpoints:
                        if ep.method.value != method:
                            continue
                        ep_full = getattr(ep, "full_path", "") or ep.path
                        if ep_full != full_path:
                            continue
                        meta = ep.metadata if isinstance(ep.metadata, dict) else {}
                        meta["request"] = req
                        ep.metadata = meta
                        updates_applied += 1
                        break

            if issues:
                # Do not fail just because the LLM reports issues; strict mode is enforced by
                # deterministic validation below.
                self.logger.warning("LLM verify reported issues", count=len(issues))

            # Deterministic validation: ensure required keys exist and examples are present.
            missing = self._find_request_template_gaps(endpoints)
            if missing:
                msg = f"Request templates still incomplete after LLM verify: {missing[:10]}"
                if strict:
                    raise PipelineError(msg)
                self.logger.warning(msg)

            self.logger.info(
                "LLM verify completed",
                endpoints_checked=min(len(endpoints), max_endpoints),
                updates_applied=updates_applied,
                issues_reported=len(issues),
                strict=strict,
            )
        except Exception as e:
            # In non-strict mode, do not fail the pipeline.
            if getattr(self.config, "llm_verify_strict", False):
                raise
            self.logger.warning("LLM verify failed; continuing", error=str(e))

    def _find_request_template_gaps(self, endpoints: List[Endpoint]) -> List[str]:
        """Return a list of human-readable gap descriptions for request templates."""
        gaps: List[str] = []

        def has_schema_and_example(p: dict) -> bool:
            if not isinstance(p.get("schema"), dict):
                return False
            return "example" in p

        for ep in endpoints:
            meta = ep.metadata if isinstance(ep.metadata, dict) else {}
            req = meta.get("request")
            if not isinstance(req, dict):
                gaps.append(f"{ep.method.value} {getattr(ep, 'full_path', '') or ep.path}: missing request")
                continue

            for section in ("path", "query", "header"):
                items = req.get(section)
                if not isinstance(items, list):
                    gaps.append(f"{ep.method.value} {getattr(ep, 'full_path', '') or ep.path}: {section} not list")
                    continue
                for it in items:
                    if not isinstance(it, dict) or not it.get("name"):
                        gaps.append(f"{ep.method.value} {getattr(ep, 'full_path', '') or ep.path}: {section} item invalid")
                        break
                    if not has_schema_and_example(it):
                        gaps.append(f"{ep.method.value} {getattr(ep, 'full_path', '') or ep.path}: {section}.{it.get('name')} missing schema/example")
                        break

            # Body is optional at the template level: many POST/PUT/PATCH endpoints do
            # legitimately have no request body. If a body exists, it must be test-ready.
            if ep.method.value in {"post", "put", "patch"}:
                body = req.get("body")
                if body is None:
                    continue
                if not isinstance(body, dict):
                    gaps.append(f"{ep.method.value} {getattr(ep, 'full_path', '') or ep.path}: body invalid")
                    continue
                if not isinstance(body.get("schema"), dict):
                    gaps.append(f"{ep.method.value} {getattr(ep, 'full_path', '') or ep.path}: body missing schema")
                if "example" not in body:
                    gaps.append(f"{ep.method.value} {getattr(ep, 'full_path', '') or ep.path}: body missing example")

        return gaps

    def _validate_openapi_or_fail(self, openapi_dict: Dict[str, Any]) -> None:
        """Validate OpenAPI output and optionally fail in strict verify mode."""
        strict = bool(getattr(self.config, "llm_verify", False) and getattr(self.config, "llm_verify_strict", False))
        try:
            from openapi_spec_validator import validate

            validate(openapi_dict)
        except Exception as e:
            if strict:
                raise PipelineError(f"OpenAPI validation failed in strict mode: {str(e)}") from e
            self.logger.warning("OpenAPI validation failed (non-strict)", error=str(e))

    def _save_outputs(self, openapi_spec: Any, endpoints: List[Endpoint], repo_name: str) -> None:
        """Save outputs to files"""
        output_dir = Path(self.config.output_dir)
        output_dir.mkdir(parents=True, exist_ok=True)

        # Save OpenAPI JSON
        openapi_path = output_dir / f"{repo_name}_openapi.json"
        if isinstance(openapi_spec, OpenAPISpec):
            openapi_spec.save(str(openapi_path))
        else:
            with open(openapi_path, "w", encoding="utf-8") as f:
                json.dump(openapi_spec, f, indent=2, ensure_ascii=False, default=str)
        self.logger.info("OpenAPI spec saved", path=str(openapi_path))

        # Save raw endpoints
        endpoints_path = output_dir / f"{repo_name}_endpoints.json"
        with open(endpoints_path, "w", encoding="utf-8") as f:
            json.dump([e.to_dict() for e in endpoints], f, indent=2, ensure_ascii=False)
        self.logger.info("Raw endpoints saved", path=str(endpoints_path))

        # Save stats
        stats_path = output_dir / f"{repo_name}_stats.json"
        with open(stats_path, "w", encoding="utf-8") as f:
            json.dump(self.stats.to_dict(), f, indent=2, ensure_ascii=False)
        self.logger.info("Stats saved", path=str(stats_path))


def run_pipeline(repo_url: str, branch: Optional[str] = None, keep_repo: bool = False) -> Dict[str, Any]:
    """
    Convenience function to run the pipeline.

    Args:
        repo_url: GitHub repository URL
        branch: Optional branch to clone
        keep_repo: Keep cloned repository for debugging

    Returns:
        Dictionary with results and stats
    """
    config = Config()
    pipeline = Pipeline(config)
    return pipeline.run(repo_url, branch, keep_repo)