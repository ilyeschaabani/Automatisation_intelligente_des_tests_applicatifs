"""Regex-based endpoint extraction for common patterns"""

import re
from typing import List, Dict, Optional
from pathlib import Path
from .models.endpoint import Endpoint, HTTPMethod
from .utils.logger import get_logger


class RegexExtractor:
    """Extract endpoints using regex patterns"""

    def __init__(self):
        self.logger = get_logger(__name__)

        # JavaScript/TypeScript patterns
        self.js_patterns = {
            # Express routes: app.get('/path', handler) or router.post('/path')
            r'(?:app|router|express)\.(get|post|put|delete|patch|all|use)\s*\(\s*[\'"]([^\'"]+)[\'"]': 'express',
            # Express route method: route('/path').get(handler).post(handler)
            r'\.route\s*\(\s*[\'"]([^\'"]+)[\'"].*?\.(?:get|post|put|delete|patch)\s*\(': 'express_route',
            # Custom registration: registerEndpoint('/path', handler)
            r'(?:register|add)?(?:[Ee]ndpoint|[Rr]oute)\s*\(\s*[\'"]([^\'"]+)[\'"]': 'custom',
        }

        # Python patterns
        self.py_patterns = {
            # Flask routes: @app.route('/path') or @router.get('/path')
            r'@(?:app|router|blueprint)\.(?:route|get|post|put|delete|patch)\s*\(\s*[\'"]([^\'"]+)[\'"]': 'flask_decorator',
            # FastAPI routes: @app.get('/path') or @router.post('/path')
            r'@(?:app|router)\.(?:get|post|put|delete|patch|api_route)\s*\(\s*[\'"]([^\'"]+)[\'"]': 'fastapi',
            # Django paths: path('path-name', view)
            r'path\s*\(\s*[\'"]([^\'"]+)[\'"]': 'django',
            # Flask-RESTful: api.add_resource(Class, '/path')
            r'api\.add_resource\s*\(\s*\w+\s*,\s*[\'"]([^\'"]+)[\'"]': 'flask_restful',
            # Custom registration: register_endpoint('/path'), add_route('/path')
            r'(?:register_endpoint|add_route|handle_route)\s*\(\s*[\'"]([^\'"]+)[\'"]': 'custom',
        }

        # Java/Spring patterns
        self.java_patterns = {
            # Spring: @GetMapping("/path") @PostMapping("/api/users")
            r'@(?:GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping)\s*\(\s*["\']([^"\']+)["\']': 'spring_method',
            # Spring: @RequestMapping(value="/path", method=GET)
            r'@RequestMapping\s*\(\s*value\s*=\s*["\']([^"\']+)["\']': 'spring_mapping',
            # Spring: @RequestMapping("/path")
            r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']': 'spring_mapping_simple',
            # Custom: registerEndpoint, addRoute in Java style
            r'(?:register|handle)(?:Endpoint|Route)\s*\(\s*["\']([^"\']+)["\']': 'java_custom',
        }

        # HTTP methods for extraction
        self.http_methods = ['get', 'post', 'put', 'delete', 'patch', 'head', 'options']

    def extract_from_javascript(self, code: str, file_path: Path) -> List[Endpoint]:
        """Extract endpoints from JavaScript/TypeScript code"""
        endpoints = []
        lines = code.split('\n')

        # NestJS (TypeScript) decorators
        # @Controller('users') class UserController { @Get(':id') ... }
        # Keep this regex-based and best-effort.
        controller_prefix: str = ""
        http_decorator_map = {
            "Get": "get",
            "Post": "post",
            "Put": "put",
            "Delete": "delete",
            "Patch": "patch",
            "All": "get",  # treat as GET for discovery
        }

        # Look for common patterns
        for line_num, line in enumerate(lines, 1):
            # NestJS controller prefix
            if "@Controller" in line:
                m = re.search(r"@Controller\s*\(\s*(?:\{[^}]*path\s*:\s*)?[\"']([^\"']*)[\"']", line)
                if m is not None:
                    controller_prefix = m.group(1) or ""

            # NestJS method decorators
            for deco, http_method in http_decorator_map.items():
                if f"@{deco}" in line:
                    m = re.search(rf"@{deco}\s*\(\s*[\"']([^\"']*)[\"']\s*\)", line)
                    subpath = m.group(1) if m else ""
                    # NestJS treats empty string as root of controller
                    path = subpath if subpath else "/"
                    endpoints.append(Endpoint(
                        method=HTTPMethod(http_method),
                        path=path,
                        file_path=str(file_path),
                        line_number=line_num,
                        router_prefix=f"/{controller_prefix}" if controller_prefix else None,
                        confidence=0.82,
                        source='regex',
                        metadata={'pattern': 'nestjs_decorator'}
                    ))

            # Express.js patterns
            for method in self.http_methods:
                # app.get('/path', handler)
                pattern = rf'(?:app|router|express)\.{method}\s*\(\s*[\'"]([^\'"]+)[\'"]'
                for match in re.finditer(pattern, line):
                    path = match.group(1)
                    endpoints.append(Endpoint(
                        method=HTTPMethod(method),
                        path=path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.85,
                        source='regex',
                        metadata={'pattern': 'express_method'}
                    ))

            # Fastify patterns: fastify.get('/path', handler)
            for method in self.http_methods:
                pattern = rf'(?:fastify|app)\.{method}\s*\(\s*[\'"]([^\'"]+)[\'"]'
                for match in re.finditer(pattern, line):
                    path = match.group(1)
                    endpoints.append(Endpoint(
                        method=HTTPMethod(method),
                        path=path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.82,
                        source='regex',
                        metadata={'pattern': 'fastify_method'}
                    ))

            # .route() style
            if '.route' in line:
                match = re.search(r'\.route\s*\(\s*[\'"]([^\'"]+)[\'"]', line)
                if match:
                    path = match.group(1)
                    # Try to extract methods from same or following lines
                    route_methods = re.findall(r'\.(\w+)\s*\(', line)
                    for m in route_methods:
                        if m.lower() in self.http_methods:
                            endpoints.append(Endpoint(
                                method=HTTPMethod(m.lower()),
                                path=path,
                                file_path=str(file_path),
                                line_number=line_num,
                                confidence=0.80,
                                source='regex',
                                metadata={'pattern': 'express_route'}
                            ))

            # Custom registration patterns
            for func_name in ['register', 'add', 'handle']:
                pattern = rf'{func_name}(?:[Ee]ndpoint|[Rr]oute|[Ss]ervice)?\s*\(\s*[\'"]([^\'"]+)[\'"]'
                for match in re.finditer(pattern, line):
                    path = match.group(1)
                    endpoints.append(Endpoint(
                        method=HTTPMethod.GET,  # Default to GET
                        path=path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.65,
                        source='regex',
                        metadata={'pattern': 'custom_registration'}
                    ))

        return endpoints

    def extract_from_python(self, code: str, file_path: Path) -> List[Endpoint]:
        """Extract endpoints from Python code"""
        endpoints = []
        lines = code.split('\n')

        for line_num, line in enumerate(lines, 1):
            # Flask decorator patterns
            for method in self.http_methods:
                # @app.route('/path') or @router.get('/path')
                pattern = rf'@(?:app|router|blueprint)\.(?:route|{method})\s*\(\s*[\'"]([^\'"]+)[\'"]'
                for match in re.finditer(pattern, line):
                    path = match.group(1)
                    endpoints.append(Endpoint(
                        method=HTTPMethod(method.lower() if method.lower() != 'route' else 'get'),
                        path=path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.90,
                        source='regex',
                        metadata={'pattern': 'flask_decorator'}
                    ))

            # Django path() patterns
            if 'path(' in line:
                match = re.search(r'path\s*\(\s*[\'"]([^\'"]+)[\'"]', line)
                if match:
                    path = match.group(1)
                    endpoints.append(Endpoint(
                        method=HTTPMethod.GET,  # Django handlers handle all methods
                        path=path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.85,
                        source='regex',
                        metadata={'pattern': 'django_path'}
                    ))

            # Flask-RESTful api.add_resource()
            if 'add_resource' in line:
                match = re.search(r'add_resource\s*\(\s*\w+\s*,\s*[\'"]([^\'"]+)[\'"]', line)
                if match:
                    path = match.group(1)
                    endpoints.append(Endpoint(
                        method=HTTPMethod.GET,  # Default to GET
                        path=path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.80,
                        source='regex',
                        metadata={'pattern': 'flask_restful'}
                    ))

            # Custom registration patterns
            for func_name in ['register_endpoint', 'add_route', 'handle_route']:
                pattern = rf'{func_name}\s*\(\s*[\'"]([^\'"]+)[\'"]'
                for match in re.finditer(pattern, line):
                    path = match.group(1)
                    endpoints.append(Endpoint(
                        method=HTTPMethod.GET,  # Default to GET
                        path=path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.70,
                        source='regex',
                        metadata={'pattern': 'custom_registration'}
                    ))

        return endpoints

    def extract_from_java(self, code: str, file_path: Path) -> List[Endpoint]:
        """Extract endpoints from Java code (Spring Boot)"""
        endpoints = []
        lines = code.split('\n')
        
        class_level_prefix = ""

        # Micronaut: @Controller("/api") + @Get("/users")
        micronaut_controller_prefix = ""

        # JAX-RS / Quarkus: @Path("/api") + @GET/@POST with optional method-level @Path
        jaxrs_class_path = ""

        # Spring Cloud Gateway (RouteLocatorBuilder) routes are often defined via a fluent DSL:
        # builder.routes().route("svc", r -> r.path("/svc/**").uri("lb://svc")).build();
        # Treat these as gateway paths (method unknown -> default GET) with lower confidence.
        for route_match in re.finditer(r"\.route\s*\(", code):
            snippet = code[route_match.start():route_match.start() + 600]
            if ".path" not in snippet:
                continue
            path_call = re.search(r"\.path\s*\(([^\)]*)\)", snippet, flags=re.DOTALL)
            if not path_call:
                continue

            raw_args = path_call.group(1)
            paths = re.findall(r"[\"']([^\"']+)[\"']", raw_args)
            if not paths:
                continue

            # Compute a best-effort line number for the first match
            line_number = code.count("\n", 0, route_match.start()) + 1
            for p in paths:
                endpoints.append(Endpoint(
                    method=HTTPMethod.GET,
                    path=p,
                    file_path=str(file_path),
                    line_number=line_number,
                    confidence=0.60,
                    source='regex',
                    metadata={'pattern': 'spring_cloud_gateway_route'}
                ))

        for line_num, line in enumerate(lines, 1):
            # Micronaut class/controller prefix
            if "@Controller" in line:
                m = re.search(r"@Controller\s*\(\s*[\"']([^\"']*)[\"']\s*\)", line)
                if m:
                    micronaut_controller_prefix = m.group(1)

            # JAX-RS class @Path
            if "@Path" in line:
                m = re.search(r"@Path\s*\(\s*[\"']([^\"']+)[\"']\s*\)", line)
                if m and ("class " in line or (line_num < len(lines) and "class " in lines[line_num])):
                    jaxrs_class_path = m.group(1)

            # Check for class-level @RequestMapping
            if '@RequestMapping' in line and 'public class' in lines[min(line_num, len(lines)-1)]:
                match = re.search(r'@RequestMapping\s*\(\s*["\']([^"\']+)["\']', line)
                if match:
                    class_level_prefix = match.group(1)

            # Spring @GetMapping, @PostMapping, etc.
            for method_annotation, http_method in [
                ('GetMapping', 'get'),
                ('PostMapping', 'post'),
                ('PutMapping', 'put'),
                ('DeleteMapping', 'delete'),
                ('PatchMapping', 'patch'),
            ]:
                pattern = rf'@{method_annotation}\s*\(\s*["\']([^"\']+)["\']'
                for match in re.finditer(pattern, line):
                    path = match.group(1)
                    # Combine with class-level prefix
                    full_path = self._combine_paths_java(class_level_prefix, path)
                    endpoints.append(Endpoint(
                        method=HTTPMethod(http_method),
                        path=full_path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.87,
                        source='regex',
                        metadata={'pattern': f'spring_{method_annotation}'}
                    ))

            # Spring @RequestMapping with method specified
            if '@RequestMapping' in line and 'method' in line:
                # @RequestMapping(value="/path", method=RequestMethod.GET)
                path_match = re.search(r'value\s*=\s*["\']([^"\']+)["\']', line)
                method_match = re.search(r'method\s*=\s*RequestMethod\.(\w+)', line)
                
                if path_match and method_match:
                    path = path_match.group(1)
                    http_method = method_match.group(1).lower()
                    full_path = self._combine_paths_java(class_level_prefix, path)
                    endpoints.append(Endpoint(
                        method=HTTPMethod(http_method),
                        path=full_path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.85,
                        source='regex',
                        metadata={'pattern': 'spring_requestmapping'}
                    ))
                elif path_match:
                    # Default to GET if method not specified
                    path = path_match.group(1)
                    full_path = self._combine_paths_java(class_level_prefix, path)
                    endpoints.append(Endpoint(
                        method=HTTPMethod.GET,
                        path=full_path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.80,
                        source='regex',
                        metadata={'pattern': 'spring_requestmapping_simple'}
                    ))

            # Micronaut method mappings: @Get("/x"), @Post("/x") etc.
            for method_annotation, http_method in [
                ("Get", "get"),
                ("Post", "post"),
                ("Put", "put"),
                ("Delete", "delete"),
                ("Patch", "patch"),
            ]:
                if f"@{method_annotation}" in line:
                    m = re.search(rf"@{method_annotation}\s*\(\s*[\"']([^\"']*)[\"']\s*\)", line)
                    if m:
                        path = m.group(1) or "/"
                        full_path = self._combine_paths_java(micronaut_controller_prefix, path)
                        endpoints.append(Endpoint(
                            method=HTTPMethod(http_method),
                            path=full_path,
                            file_path=str(file_path),
                            line_number=line_num,
                            confidence=0.82,
                            source='regex',
                            metadata={'pattern': 'micronaut_mapping'}
                        ))

            # JAX-RS / Quarkus method annotations: @GET/@POST + optional @Path
            jaxrs_http = None
            if "@GET" in line:
                jaxrs_http = "get"
            elif "@POST" in line:
                jaxrs_http = "post"
            elif "@PUT" in line:
                jaxrs_http = "put"
            elif "@DELETE" in line:
                jaxrs_http = "delete"
            elif "@PATCH" in line:
                jaxrs_http = "patch"

            if jaxrs_http:
                # Look ahead a few lines for a method-level @Path
                method_path = ""
                for k in range(line_num, min(line_num + 4, len(lines) + 1)):
                    ll = lines[k - 1]
                    if "@Path" in ll:
                        mm = re.search(r"@Path\s*\(\s*[\"']([^\"']+)[\"']\s*\)", ll)
                        if mm:
                            method_path = mm.group(1)
                            break
                    if "(" in ll and ")" in ll and ("public" in ll or "Response" in ll):
                        break

                full_path = self._combine_paths_java(jaxrs_class_path, method_path or "/")
                endpoints.append(Endpoint(
                    method=HTTPMethod(jaxrs_http),
                    path=full_path,
                    file_path=str(file_path),
                    line_number=line_num,
                    confidence=0.78,
                    source='regex',
                    metadata={'pattern': 'jaxrs_mapping'}
                ))

        return endpoints

    def extract_from_go(self, code: str, file_path: Path) -> List[Endpoint]:
        """Extract endpoints from Go code (Gin/Echo style)."""
        endpoints: List[Endpoint] = []
        lines = code.split("\n")

        # Gin/Echo commonly: r.GET("/path", ...) or e.POST("/path", ...)
        pattern = re.compile(r"\.\s*(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\s*\(\s*[\"']([^\"']+)[\"']")
        for line_num, line in enumerate(lines, 1):
            for m in pattern.finditer(line):
                method = m.group(1).lower()
                path = m.group(2)
                try:
                    endpoints.append(Endpoint(
                        method=HTTPMethod(method),
                        path=path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.75,
                        source='regex',
                        metadata={'pattern': 'go_router_method'}
                    ))
                except Exception:
                    continue

        return endpoints

    def extract_from_csharp(self, code: str, file_path: Path) -> List[Endpoint]:
        """Extract endpoints from C# ASP.NET Core attributes."""
        endpoints: List[Endpoint] = []
        lines = code.split("\n")

        class_prefix: str = ""
        pending_route_attr: Optional[str] = None

        def parse_attr_path(attr_line: str) -> Optional[str]:
            m = re.search(r"\(\s*[\"']([^\"']*)[\"']\s*\)", attr_line)
            if m:
                return m.group(1)
            return None

        http_attr_map = {
            "HttpGet": "get",
            "HttpPost": "post",
            "HttpPut": "put",
            "HttpDelete": "delete",
            "HttpPatch": "patch",
        }

        for line_num, line in enumerate(lines, 1):
            stripped = line.strip()

            if stripped.startswith("[Route"):
                pending_route_attr = stripped
                continue

            # Class-level route
            if "class " in stripped and pending_route_attr:
                p = parse_attr_path(pending_route_attr)
                if p is not None:
                    class_prefix = p
                pending_route_attr = None

            # Method-level route and verb attributes
            for attr, http_method in http_attr_map.items():
                if stripped.startswith(f"[{attr}"):
                    method_path = parse_attr_path(stripped) or "/"
                    endpoints.append(Endpoint(
                        method=HTTPMethod(http_method),
                        path=method_path,
                        file_path=str(file_path),
                        line_number=line_num,
                        router_prefix=class_prefix or None,
                        confidence=0.80,
                        source='regex',
                        metadata={'pattern': 'aspnet_attribute'}
                    ))
                    break

        return endpoints

    def extract_from_php(self, code: str, file_path: Path) -> List[Endpoint]:
        """Extract endpoints from PHP code (Symfony routes via attributes/annotations)."""
        endpoints: List[Endpoint] = []
        lines = code.split("\n")

        class_prefix: str = ""

        i = 0
        while i < len(lines):
            line = lines[i]

            # ========== Laravel: Route::get('/path', ...) etc. ==========
            if "Route::" in line:
                m = re.search(r"\bRoute::(get|post|put|delete|patch|options|match)\s*\(\s*['\"]([^'\"]+)['\"]", line, flags=re.IGNORECASE)
                if m:
                    method = m.group(1).lower()
                    path = m.group(2)
                    if method == "match":
                        # Best-effort: treat as GET for discovery when via list isn't parsed
                        method = "get"
                    if method in self.http_methods:
                        endpoints.append(Endpoint(
                            method=HTTPMethod(method),
                            path=path,
                            file_path=str(file_path),
                            line_number=i + 1,
                            confidence=0.80,
                            source="regex",
                            metadata={"pattern": "laravel_route_facade"},
                        ))

            # ========== PHP 8 attributes: #[Route(...)] ==========
            if "#[" in line and "Route" in line:
                attr_block, end_index = self._collect_php_attribute_block(lines, i)
                if attr_block and self._looks_like_symfony_route_attr(attr_block):
                    route_info = self._parse_symfony_route_args(attr_block)
                    if route_info:
                        target_kind, target_name = self._peek_php_next_definition(lines, end_index + 1)
                        if target_kind == "class" and route_info.get("path"):
                            class_prefix = route_info["path"]
                        else:
                            endpoints.extend(
                                self._symfony_route_to_endpoints(
                                    route_info=route_info,
                                    file_path=file_path,
                                    line_number=i + 1,
                                    router_prefix=class_prefix,
                                    function_name=target_name if target_kind == "function" else None,
                                    source_pattern="symfony_attribute",
                                )
                            )
                i = max(i + 1, end_index + 1)
                continue

            # ========== Docblock annotations: @Route(...) ==========
            if "@Route" in line:
                ann_block, end_index = self._collect_php_annotation_block(lines, i)
                if ann_block and "@Route" in ann_block:
                    route_info = self._parse_symfony_route_args(ann_block)
                    if route_info and route_info.get("path"):
                        target_kind, target_name = self._peek_php_next_definition(lines, end_index + 1)
                        if target_kind == "class":
                            class_prefix = route_info["path"]
                        else:
                            endpoints.extend(
                                self._symfony_route_to_endpoints(
                                    route_info=route_info,
                                    file_path=file_path,
                                    line_number=i + 1,
                                    router_prefix=class_prefix,
                                    function_name=target_name if target_kind == "function" else None,
                                    source_pattern="symfony_annotation",
                                )
                            )
                i = max(i + 1, end_index + 1)
                continue

            i += 1

        return endpoints

    def _collect_php_attribute_block(self, lines: List[str], start_index: int) -> tuple[Optional[str], int]:
        """Collect a PHP attribute block starting at start_index."""
        collected: List[str] = []
        i = start_index
        while i < len(lines):
            collected.append(lines[i])
            if "]" in lines[i]:
                return "\n".join(collected), i
            i += 1
        return None, start_index

    def _collect_php_annotation_block(self, lines: List[str], start_index: int) -> tuple[Optional[str], int]:
        """Collect a PHP annotation line (may span multiple lines until a closing ')')."""
        collected: List[str] = []
        i = start_index
        open_parens = 0
        while i < len(lines):
            chunk = lines[i]
            collected.append(chunk)
            open_parens += chunk.count("(")
            open_parens -= chunk.count(")")
            if "@Route" in "\n".join(collected) and open_parens <= 0 and ")" in chunk:
                return "\n".join(collected), i
            # In practice @Route(...) usually ends on same line; cap lookahead to avoid runaway
            if i - start_index >= 10:
                break
            i += 1
        return "\n".join(collected), min(i, len(lines) - 1)

    def _looks_like_symfony_route_attr(self, attr_block: str) -> bool:
        # Match #[Route(...)] or #[\Symfony\Component\Routing\Annotation\Route(...)]
        return bool(re.search(r"#\[\s*(?:\\?[\\\w]+\\)*Route\s*\(", attr_block))

    def _peek_php_next_definition(self, lines: List[str], start_index: int) -> tuple[str, Optional[str]]:
        """Find the next class or function declaration after a route definition."""
        for j in range(start_index, min(len(lines), start_index + 15)):
            l = lines[j].strip()
            if not l:
                continue
            if l.startswith("//"):
                continue
            if l.startswith("*") or l.startswith("/*") or l.startswith("*/"):
                continue

            class_match = re.search(r"\bclass\s+(\w+)", l)
            if class_match:
                return "class", class_match.group(1)

            fn_match = re.search(r"\bfunction\s+(\w+)\s*\(", l)
            if fn_match:
                return "function", fn_match.group(1)

        return "unknown", None

    def _parse_symfony_route_args(self, route_block: str) -> Optional[Dict[str, object]]:
        """Parse Symfony Route arguments from an attribute/annotation block."""
        # Extract the parentheses content
        paren_match = re.search(r"Route\s*\((.*)\)", route_block, flags=re.DOTALL)
        if not paren_match:
            paren_match = re.search(r"@Route\s*\((.*)\)", route_block, flags=re.DOTALL)
        if not paren_match:
            return None

        args = paren_match.group(1)

        # Remove trailing attribute/annotation noise
        args = args.strip()

        # Path: first positional string OR named path: "..." / path="..."
        path: Optional[str] = None
        pos_path = re.search(r"^\s*['\"]([^'\"]+)['\"]", args)
        if pos_path:
            path = pos_path.group(1)
        else:
            named_path = re.search(r"\bpath\s*[:=]\s*['\"]([^'\"]+)['\"]", args)
            if named_path:
                path = named_path.group(1)

        # Methods: methods: ['GET', 'POST'] or methods={"GET"} etc.
        methods: Optional[List[str]] = None
        methods_match = re.search(r"\bmethods\s*[:=]\s*(\[[^\]]*\]|\{[^}]*\}|['\"][^'\"]+['\"])", args, flags=re.DOTALL)
        if methods_match:
            raw = methods_match.group(1).strip()
            methods = self._parse_methods_list(raw)

        return {
            "path": path,
            "methods": methods,
        }

    def _parse_methods_list(self, raw: str) -> List[str]:
        """Parse methods from Symfony Route syntax into lowercase HTTP method strings."""
        # Normalize wrappers
        raw = raw.strip()
        if (raw.startswith("'") and raw.endswith("'")) or (raw.startswith('"') and raw.endswith('"')):
            raw = raw[1:-1]
            items = [raw]
        else:
            # Strip [] or {}
            if (raw.startswith("[") and raw.endswith("]")) or (raw.startswith("{") and raw.endswith("}")):
                raw = raw[1:-1]
            # Split on commas
            items = [p.strip() for p in raw.split(",") if p.strip()]

        normalized: List[str] = []
        for item in items:
            # Strip quotes
            item = item.strip()
            item = item.strip("'\"")
            item_upper = item.upper()
            if item_upper:
                normalized.append(item_upper.lower())
        # Filter to supported methods
        return [m for m in normalized if m in self.http_methods]

    def _symfony_route_to_endpoints(
        self,
        route_info: Dict[str, object],
        file_path: Path,
        line_number: int,
        router_prefix: str,
        function_name: Optional[str],
        source_pattern: str,
    ) -> List[Endpoint]:
        path = str(route_info.get("path") or "")
        methods = route_info.get("methods")
        if not path:
            return []

        method_list: List[str]
        if isinstance(methods, list) and methods:
            method_list = [m for m in methods if isinstance(m, str)]
        else:
            method_list = ["get"]

        results: List[Endpoint] = []
        for method in method_list:
            results.append(
                Endpoint(
                    method=HTTPMethod(method),
                    path=path,
                    file_path=str(file_path),
                    line_number=line_number,
                    router_prefix=router_prefix or None,
                    function_name=function_name,
                    confidence=0.88 if method_list != ["get"] else 0.80,
                    source="regex",
                    metadata={"pattern": source_pattern},
                )
            )
        return results

    def _combine_paths_java(self, prefix: str, path: str) -> str:
        """Combine class-level and method-level paths in Java"""
        if not prefix:
            return path
        if not path:
            return prefix
        
        prefix = prefix.rstrip("/")
        path = path.lstrip("/")
        return f"{prefix}/{path}" if path else prefix

    def extract_endpoints(self, code: str, file_path: Path, language: str) -> List[Endpoint]:
        """
        Extract endpoints from code using regex patterns.

        Args:
            code: Source code
            file_path: Path to source file
            language: Programming language ('javascript', 'python', 'java', etc.)

        Returns:
            List of Endpoint objects
        """
        if language in ['javascript', 'typescript', 'js', 'ts', 'jsx', 'tsx']:
            return self.extract_from_javascript(code, file_path)
        elif language in ['python', 'py']:
            return self.extract_from_python(code, file_path)
        elif language in ['java']:
            return self.extract_from_java(code, file_path)
        elif language in ['php']:
            return self.extract_from_php(code, file_path)
        elif language in ['go']:
            return self.extract_from_go(code, file_path)
        elif language in ['csharp', 'cs']:
            return self.extract_from_csharp(code, file_path)
        else:
            self.logger.debug("Regex extraction not supported for language", language=language)
            return []
