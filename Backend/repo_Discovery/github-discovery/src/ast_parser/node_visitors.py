"""AST node visitors for different languages"""

from abc import ABC, abstractmethod
from typing import List, Optional, Any, Dict
from pathlib import Path

import tree_sitter

from ..models.endpoint import Endpoint, HTTPMethod
from ..utils.logger import get_logger


class NodeVisitor(ABC):
    """Base AST node visitor"""

    def __init__(self, file_path: Path, router_prefix: Optional[str] = None, logger=None):
        self.file_path = file_path
        self.router_prefix = router_prefix
        self.logger = logger or get_logger(__name__)
        self.endpoints: List[Endpoint] = []
        self.errors: List[Dict[str, Any]] = []
        self.current_router_prefix = router_prefix or ""

    @abstractmethod
    def visit(self, node: tree_sitter.Node) -> None:
        """Visit a node and extract endpoints"""
        pass

    def visit_children(self, node: tree_sitter.Node) -> None:
        """Recursively visit all children"""
        for child in node.children:
            self.visit(child)

    def add_endpoint(
        self,
        method: str,
        path: str,
        line_number: int,
        function_name: Optional[str] = None,
        parameters: Optional[List[str]] = None,
        middleware: Optional[List[str]] = None,
        confidence: float = 1.0,
        metadata: Optional[dict] = None,
        router_prefix: Optional[str] = None,
    ) -> None:
        """Add an extracted endpoint"""
        try:
            endpoint = Endpoint(
                method=HTTPMethod(method.lower()),
                path=path,
                file_path=str(self.file_path),
                line_number=line_number,
                router_prefix=self.current_router_prefix if router_prefix is None else router_prefix,
                function_name=function_name,
                parameters=parameters or [],
                middleware=middleware or [],
                confidence=confidence,
                source="ast",
                metadata=metadata or {},
            )
            self.endpoints.append(endpoint)
        except Exception as e:
            self.errors.append({
                "file": str(self.file_path),
                "line": line_number,
                "error": str(e),
                "type": "endpoint_creation"
            })


class JavaScriptVisitor(NodeVisitor):
    """Visitor for JavaScript/TypeScript (Node.js) files"""

    # Language-specific queries
    EXPRESS_ROUTE_QUERY = """
        (call_expression
            function: (member_expression object:(identifier) property:(property_identifier))
            arguments: (arguments (string) @route)
        ) @call
    """

    def visit(self, node: tree_sitter.Node) -> None:
        """Visit node and extract Express.js routes"""
        if node.type == "call_expression":
            self._visit_call_expression(node)
        # Recurse to children
        for child in node.children:
            self.visit(child)

    def _visit_call_expression(self, node: tree_sitter.Node) -> None:
        """Extract routes from call expressions like app.get('/path', handler)"""
        try:
            # Check if it's a method call on app/router
            if node.child_count < 2:
                return

            # Get the function part (app.get, router.post, etc.)
            func_node = node.child_by_field_name("function")
            if not func_node:
                return

            # Check if it's a member expression (app.get)
            if func_node.type == "member_expression":
                prop_node = func_node.child_by_field_name("property")
                if prop_node and prop_node.type == "property_identifier":
                    method = prop_node.text.decode("utf8").lower()
                    # Expanded method list: add 'route', 'request', 'handle' for custom patterns
                    if method in ["get", "post", "put", "delete", "patch", "all", "route", "request", "handle"]:
                        # Extract route path from arguments
                        args_node = node.child_by_field_name("arguments")
                        if args_node and args_node.child_count > 0:
                            route_arg = args_node.children[0]
                            path = self._extract_string(route_arg)
                            if path:
                                line = node.start_point[0] + 1
                                # For 'route' without method, default to GET
                                http_method = "get" if method == "route" else method
                                self.add_endpoint(
                                    method=http_method,
                                    path=path,
                                    line_number=line,
                                    function_name=self._find_function_name(node)
                                )

            # Check for custom registration functions: registerEndpoint, addRoute, createRoute
            if func_node.type == "identifier":
                func_name = func_node.text.decode("utf8").lower()
                
                # Middleware/prefix detection
                if func_name == "use":
                    # This could be app.use('/api', router)
                    args_node = node.child_by_field_name("arguments")
                    if args_node and args_node.child_count >= 1:
                        prefix_arg = args_node.children[0]
                        prefix = self._extract_string(prefix_arg)
                        if prefix:
                            # Update current router prefix
                            old_prefix = self.current_router_prefix
                            self.current_router_prefix = self._combine_paths(old_prefix, prefix)
                            self.logger.debug(
                                "Updated router prefix",
                                file=str(self.file_path),
                                new_prefix=self.current_router_prefix
                            )
                
                # Custom route registration functions
                elif func_name in ["registerendpoint", "addroute", "createroute"]:
                    args_node = node.child_by_field_name("arguments")
                    if args_node and args_node.child_count >= 2:
                        # Try to extract path and method
                        path_arg = args_node.children[0]
                        method_arg = args_node.children[1]
                        
                        path = self._extract_string(path_arg)
                        method = self._extract_string(method_arg)
                        
                        if path and method:
                            line = node.start_point[0] + 1
                            self.add_endpoint(
                                method=method.lower(),
                                path=path,
                                line_number=line,
                                confidence=0.7  # Lower confidence for custom patterns
                            )

        except Exception as e:
            self.errors.append({
                "file": str(self.file_path),
                "line": node.start_point[0] + 1,
                "error": str(e),
                "type": "call_expression_processing"
            })

    def _extract_string(self, node: tree_sitter.Node) -> Optional[str]:
        """Extract string value from node"""
        if node.type == "string":
            # Remove quotes
            text = node.text.decode("utf8")
            return text[1:-1] if len(text) >= 2 else text
        elif node.type == "template_string":
            # Handle template literals (basic extraction)
            return node.text.decode("utf8")[1:-1]
        return None

    def _find_function_name(self, node: tree_sitter.Node) -> Optional[str]:
        """Try to find the handler function name"""
        # Look for the second argument which is usually the handler
        args_node = node.child_by_field_name("arguments")
        if args_node and args_node.child_count >= 2:
            handler = args_node.children[1]
            # Check if it's a function expression or identifier
            if handler.type == "function_expression":
                # Try to get name from parent binding
                return None  # Anonymous function
            elif handler.type == "identifier":
                return handler.text.decode("utf8")
        return None

    def _combine_paths(self, prefix: str, path: str) -> str:
        """Combine two path segments"""
        if not prefix:
            return path
        if not path:
            return prefix
        return f"{prefix.rstrip('/')}/{path.lstrip('/')}"


class PythonVisitor(NodeVisitor):
    """Visitor for Python files (Flask, FastAPI)"""

    def visit(self, node: tree_sitter.Node) -> None:
        """Visit node and extract Flask/FastAPI routes"""
        if node.type == "decorated_definition":
            self._visit_decorated_definition(node)
        elif node.type == "expression_statement":
            # Detect function calls like api.add_resource('/users', handler)
            self._visit_call_expression(node)
        for child in node.children:
            self.visit(child)

    def _visit_call_expression(self, node: tree_sitter.Node) -> None:
        """Extract routes from function calls like api.add_resource() or register_endpoint()"""
        try:
            # Look for call expressions inside expression statements
            if node.child_count == 0:
                return
            
            call_node = node.children[0]
            if call_node.type != "call":
                return
            
            # Get function being called
            func_node = call_node.child_by_field_name("function")
            if not func_node:
                return
            
            func_text = func_node.text.decode("utf8").lower()
            
            # Detect patterns like: api.add_resource, blueprint.route, register_endpoint
            if "add_resource" in func_text or "register_endpoint" in func_text or "add_route" in func_text:
                # Try to extract path from arguments (usually second argument for add_resource)
                args_node = call_node.child_by_field_name("arguments")
                if args_node and args_node.child_count >= 2:
                    # For add_resource(Handler, '/path') - path is second arg
                    path_arg = args_node.children[1]
                    path = self._extract_string(path_arg)
                    
                    if path:
                        line_number = node.start_point[0] + 1
                        # Default to GET for Flask-RESTful patterns
                        self.add_endpoint(
                            method="get",
                            path=path,
                            line_number=line_number,
                            confidence=0.7,  # Lower confidence for custom patterns
                            metadata={"pattern": "add_resource"}
                        )
        
        except Exception as e:
            self.errors.append({
                "file": str(self.file_path),
                "line": node.start_point[0] + 1,
                "error": str(e),
                "type": "call_expression_processing"
            })

    def _visit_decorated_definition(self, node: tree_sitter.Node) -> None:
        """Extract routes from decorated function definitions"""
        try:
            # A decorated_definition node has child decorators followed by a function/class definition
            # Iterate through all decorator children (not just the first one)
            decorators = []
            for child in node.children:
                if child.type == "decorator":
                    decorators.append(child)

            if not decorators:
                return

            # Process each decorator
            for decorator_node in decorators:
                decorator_text = decorator_node.text.decode("utf8")

                # Check for Flask/FastAPI route decorators
                # @app.route('/path')
                # @router.get('/path')
                # @app.get('/path')
                import re

                # Pattern: @<object>.<method>(<path>, ...)
                match = re.search(
                    r"@(\w+)\.(get|post|put|delete|patch|route)\s*\(\s*['\"`]([^'\"`]+)['\"`]",
                    decorator_text
                )

                if match:
                    obj_name = match.group(1)
                    method_or_route = match.group(2)
                    path = match.group(3)

                    # Determine HTTP method
                    if method_or_route == "route":
                        # Need to check methods parameter
                        methods_match = re.search(r"methods\s*=\s*\[([^\]]+)\]", decorator_text)
                        if methods_match:
                            methods_str = methods_match.group(1)
                            # Extract method names
                            methods = [m.strip().strip("'\"") for m in methods_str.split(",")]
                            # For simplicity, create separate endpoints for each method
                            for method in methods:
                                if method.lower() in ["get", "post", "put", "delete", "patch"]:
                                    self._add_endpoint_from_function(node, method, path)
                        else:
                            # Default to GET for @route without methods
                            self._add_endpoint_from_function(node, "get", path)
                    else:
                        # Direct method decorator like @app.get
                        self._add_endpoint_from_function(node, method_or_route, path)

                    # Process only the first matching decorator
                    break

        except Exception as e:
            self.errors.append({
                "file": str(self.file_path),
                "line": node.start_point[0] + 1,
                "error": str(e),
                "type": "decorated_definition_processing"
            })

    def _add_endpoint_from_function(self, node: tree_sitter.Node, method: str, path: str) -> None:
        """Add endpoint from function definition node"""
        # Get function name
        func_name_node = node.child_by_field_name("name")
        function_name = func_name_node.text.decode("utf8") if func_name_node else None

        line_number = node.start_point[0] + 1

        # Extract parameters from function signature
        parameters = self._extract_parameters(node)

        self.add_endpoint(
            method=method,
            path=path,
            line_number=line_number,
            function_name=function_name,
            parameters=parameters
        )

    def _extract_parameters(self, node: tree_sitter.Node) -> List[str]:
        """Extract parameter names from function signature"""
        parameters = []
        try:
            # Find the parameters node (function parameters)
            for child in node.children:
                if child.type == "parameters":
                    for param in child.children:
                        if param.type == "identifier":
                            param_name = param.text.decode("utf8")
                            parameters.append(param_name)
                        elif param.type == "typed_parameter":
                            # typed_parameter: identifier type
                            name_node = param.child_by_field_name("name")
                            if name_node:
                                parameters.append(name_node.text.decode("utf8"))
        except Exception as e:
            self.logger.debug("Error extracting parameters", error=str(e))

        return parameters


class JavaVisitor(NodeVisitor):
    """Visitor for Java files (Spring Boot)"""

    _PARAM_ANNOTATION_RE = None

    def visit(self, node: tree_sitter.Node) -> None:
        """Visit node and extract Spring routes"""
        if node.type == "class_declaration":
            # Maintain class-level prefix scoping.
            old_prefix = self.current_router_prefix
            self._visit_class_declaration(node)
            for child in node.children:
                self.visit(child)
            self.current_router_prefix = old_prefix
            return

        if node.type == "method_declaration":
            self._visit_method_declaration(node)

        for child in node.children:
            self.visit(child)

    def _visit_class_declaration(self, node: tree_sitter.Node) -> None:
        """Extract class-level request mappings"""
        try:
            # Get class name for potential use
            class_name_node = node.child_by_field_name("name")
            class_name = class_name_node.text.decode("utf8") if class_name_node else None
            
            modifiers_node = None
            for child in node.children:
                if child.type == "modifiers":
                    modifiers_node = child
                    break

            if modifiers_node is None:
                return

            import re

            modifiers_text = modifiers_node.text.decode("utf8")
            match = re.search(
                r"@RequestMapping\s*\(\s*(?:value\s*=\s*|path\s*=\s*)?[\"\']([^\"\']+)[\"\']",
                modifiers_text,
            )
            if match:
                self.current_router_prefix = match.group(1)

        except Exception as e:
            self.logger.debug(f"Class declaration processing failed: {e}")

    def _extract_annotations_from_modifiers(self, modifiers_node: tree_sitter.Node) -> List[str]:
        """Extract annotation texts from modifiers node"""
        annotations = []
        try:
            text = modifiers_node.text.decode("utf8")
            # Find all @... patterns
            import re
            # Split by @ and reconstruct
            parts = text.split("@")[1:]  # Skip everything before first @
            for part in parts:
                # Find the annotation up to the next @ or end
                ann_text = "@" + part.split("@")[0]
                annotations.append(ann_text)
        except Exception as e:
            self.logger.debug(f"Failed to extract annotations: {e}")
        return annotations

    def _visit_method_declaration(self, node: tree_sitter.Node) -> None:
        """Extract routes from Spring annotations on methods"""
        try:
            # Get full text leading to method declaration (includes annotations)
            start_byte = node.start_byte
            parent = node.parent
            
            if parent:
                parent_text = parent.text.decode("utf8")
                # Find the method within parent
                method_text_start = parent_text.rfind("@", 0, len(parent_text) - (parent.end_byte - start_byte))
                if method_text_start >= 0:
                    annotation_text = parent_text[method_text_start:parent_text.find("{", method_text_start)]
                else:
                    # Look at just before this node
                    annotation_text = ""
            else:
                annotation_text = ""

            # Also check direct children for annotations (modifiers)
            for child in node.children:
                if child.type == "modifiers":
                    annotation_text = child.text.decode("utf8")
                    break

            # Search for Spring annotations
            import re
            
            # @GetMapping, @PostMapping, etc. with value parameter
            mapping_patterns = [
                (r'@(GetMapping|PostMapping|PutMapping|DeleteMapping|PatchMapping|RequestMapping)\s*\(\s*(?:value\s*=\s*|path\s*=\s*)?["\']([^"\']+)["\']', 
                 lambda m: (m.group(1).replace("Mapping", "").lower() if m.group(1) != "RequestMapping" else "get", m.group(2))),
                # @RequestMapping with explicit method
                (r'@RequestMapping\s*\(\s*(?:value\s*=\s*|path\s*=\s*)?["\']([^"\']+)["\'].*?method\s*=\s*RequestMethod\.(\w+)', 
                 lambda m: (m.group(2).lower(), m.group(1))),
                # Simplified @RequestMapping
                (r'@RequestMapping\s*\(\s*(?:value\s*=\s*|path\s*=\s*)?["\']([^"\']+)["\']',
                 lambda m: ("get", m.group(1))),
            ]

            found_endpoint = False
            for pattern, extractor in mapping_patterns:
                match = re.search(pattern, annotation_text, re.DOTALL)
                if match:
                    method, path = extractor(match)
                    
                    # Get method name
                    identifier_node = node.child_by_field_name("name")
                    function_name = identifier_node.text.decode("utf8") if identifier_node else None

                    request_meta, parameters = self._extract_spring_request_metadata(node)

                    line_number = node.start_point[0] + 1

                    # Spring: store method-level path in `path` and keep class-level prefix in `router_prefix`.
                    # This avoids duplicated prefixes like /api/auth/api/auth/... when later combining paths.
                    router_prefix = self.current_router_prefix or ""
                    method_level_path = path

                    # If the method-level path already includes the class-level prefix, avoid double-combining.
                    if router_prefix and method_level_path.startswith(router_prefix.rstrip("/") + "/"):
                        router_prefix = ""

                    self.add_endpoint(
                        method=method,
                        path=method_level_path,
                        line_number=line_number,
                        function_name=function_name,
                        parameters=parameters,
                        confidence=0.87,
                        metadata={
                            "annotation": match.group(0)[:50],
                            **(request_meta or {}),
                        },
                        router_prefix=router_prefix,
                    )
                    found_endpoint = True
                    break

        except Exception as e:
            self.errors.append({
                "file": str(self.file_path),
                "line": node.start_point[0] + 1,
                "error": str(e),
                "type": "java_method_processing"
            })

    def _extract_spring_request_metadata(self, node: tree_sitter.Node) -> tuple[dict, List[str]]:
        """Extract Spring request metadata (path/query/header/body) from method parameters.

        Returns:
            (metadata_fragment, flat_parameter_names)
        """
        import re

        params_node = node.child_by_field_name("parameters")
        if params_node is None:
            return {}, []

        raw = params_node.text.decode("utf8")
        inner = raw.strip()
        if inner.startswith("(") and inner.endswith(")"):
            inner = inner[1:-1]

        def split_top_level(s: str) -> List[str]:
            parts: List[str] = []
            cur: List[str] = []
            depth_paren = 0
            depth_angle = 0
            depth_brack = 0
            in_str: Optional[str] = None
            esc = False
            for ch in s:
                if in_str:
                    cur.append(ch)
                    if esc:
                        esc = False
                    elif ch == "\\":
                        esc = True
                    elif ch == in_str:
                        in_str = None
                    continue

                if ch in {"\"", "'"}:
                    in_str = ch
                    cur.append(ch)
                    continue

                if ch == "(":
                    depth_paren += 1
                elif ch == ")":
                    depth_paren = max(0, depth_paren - 1)
                elif ch == "<":
                    depth_angle += 1
                elif ch == ">":
                    depth_angle = max(0, depth_angle - 1)
                elif ch == "[":
                    depth_brack += 1
                elif ch == "]":
                    depth_brack = max(0, depth_brack - 1)

                if ch == "," and depth_paren == 0 and depth_angle == 0 and depth_brack == 0:
                    part = "".join(cur).strip()
                    if part:
                        parts.append(part)
                    cur = []
                    continue

                cur.append(ch)

            tail = "".join(cur).strip()
            if tail:
                parts.append(tail)
            return parts

        def extract_first_quoted(arg_text: str) -> Optional[str]:
            m = re.search(r'["\']([^"\']+)["\']', arg_text)
            return m.group(1) if m else None

        def strip_annotations(param_text: str) -> str:
            # Remove annotations like @RequestParam(...)
            return re.sub(r"@\w+(?:\s*\([^)]*\))?", " ", param_text)

        request: dict = {"request": {"path": [], "query": [], "header": [], "body": None}}
        flat_names: List[str] = []

        for p in split_top_level(inner):
            if not p:
                continue

            annotations = re.findall(r"@\w+(?:\s*\([^)]*\))?", p)
            ann_join = " ".join(annotations)

            location: Optional[str] = None
            if "@PathVariable" in ann_join:
                location = "path"
            elif "@RequestParam" in ann_join:
                location = "query"
            elif "@RequestHeader" in ann_join:
                location = "header"
            elif "@RequestBody" in ann_join or "@RequestPart" in ann_join:
                location = "body"

            required = True
            if re.search(r"required\s*=\s*false", ann_join):
                required = False

            # Resolve name: prefer annotation's value/name/path/value=...
            name_from_ann: Optional[str] = None
            m_named = re.search(r"(?:name|value|path)\s*=\s*([\"\'][^\"\']+[\"\'])", ann_join)
            if m_named:
                name_from_ann = extract_first_quoted(m_named.group(1))
            if name_from_ann is None and ("@PathVariable" in ann_join or "@RequestParam" in ann_join or "@RequestHeader" in ann_join):
                # handle @PathVariable("id") style
                name_from_ann = extract_first_quoted(ann_join)

            # Extract type/name from remaining text
            stripped = strip_annotations(p)
            stripped = re.sub(r"\b(final)\b", " ", stripped)
            stripped = re.sub(r"\s+", " ", stripped).strip()
            if not stripped:
                continue
            tokens = stripped.split(" ")
            if len(tokens) < 2:
                continue
            var_name = tokens[-1].rstrip("...")
            java_type = " ".join(tokens[:-1]).strip()

            param_name = name_from_ann or var_name

            # Special-case file uploads: @RequestParam MultipartFile should be requestBody (multipart/form-data)
            if location == "query" and "MultipartFile" in java_type:
                location = "body"

            # Save
            if location in {"path", "query", "header"}:
                request["request"][location].append({
                    "name": param_name,
                    "java_type": java_type,
                    "required": required,
                })
                flat_names.append(param_name)
            elif location == "body":
                # Only one body per endpoint; keep the first
                if request["request"]["body"] is None:
                    content_type = "application/json"
                    if "MultipartFile" in java_type or "@RequestPart" in ann_join:
                        content_type = "multipart/form-data"
                    request["request"]["body"] = {
                        "param": var_name,
                        "dto_type": java_type,
                        "content_type": content_type,
                        "required": required,
                        # Used later to name the multipart field correctly.
                        "multipart_field": param_name if content_type == "multipart/form-data" else None,
                    }
                    flat_names.append(var_name)

        # Remove empty structure if nothing detected
        if not request["request"]["path"] and not request["request"]["query"] and not request["request"]["header"] and request["request"]["body"] is None:
            return {}, flat_names

        return request, flat_names

    def _combine_paths(self, prefix: str, path: str) -> str:
        """Combine prefix and path"""
        if not prefix:
            return path
        if not path:
            return prefix
        
        prefix = prefix.rstrip("/")
        path = path.lstrip("/")
        return f"{prefix}/{path}"


class CSharpVisitor(NodeVisitor):
    """Visitor for C# files (ASP.NET Core)"""

    def visit(self, node: tree_sitter.Node) -> None:
        """Visit node and extract ASP.NET routes"""
        if node.type == "method_declaration":
            self._visit_method_declaration(node)
        for child in node.children:
            self.visit(child)

    def _visit_method_declaration(self, node: tree_sitter.Node) -> None:
        """Extract routes from ASP.NET attributes"""
        try:
            # Check for attributes on method
            for child in node.children:
                if child.type == "attribute":
                    attribute_text = child.text.decode("utf8")
                    import re

                    # [HttpGet("path")] or [HttpPost("path")] or [Route("path")]
                    match = re.search(
                        r"\[(HttpGet|HttpPost|HttpPut|HttpDelete|HttpPatch|Route)\s*\(\s*['\"`]([^'\"`]+)['\"`]",
                        attribute_text
                    )

                    if match:
                        attr_type = match.group(1)
                        path = match.group(2)

                        if attr_type == "Route":
                            # Could be on controller - need to check method attribute
                            # For simplicity, skip Route-only on methods
                            return

                        # Determine method
                        if attr_type.startswith("Http"):
                            method = attr_type[4:].lower()  # Remove "Http"
                        else:
                            method = attr_type.lower()

                        # Get method name
                        identifier_node = node.child_by_field_name("name")
                        function_name = identifier_node.text.decode("utf8") if identifier_node else None

                        parameters = self._extract_parameters(node)

                        line_number = node.start_point[0] + 1

                        self.add_endpoint(
                            method=method,
                            path=path,
                            line_number=line_number,
                            function_name=function_name,
                            parameters=parameters
                        )

        except Exception as e:
            self.errors.append({
                "file": str(self.file_path),
                "line": node.start_point[0] + 1,
                "error": str(e),
                "type": "method_declaration_processing"
            })

    def _extract_parameters(self, node: tree_sitter.Node) -> List[str]:
        """Extract parameter names from a C# method declaration."""
        parameters: List[str] = []
        try:
            params_node = node.child_by_field_name("parameters")
            candidates = []
            if params_node is not None:
                candidates.append(params_node)
            for child in node.children:
                if child.type in {"parameter_list", "parameters"}:
                    candidates.append(child)

            for pn in candidates:
                for ch in pn.children:
                    name_node = ch.child_by_field_name("name") if hasattr(ch, "child_by_field_name") else None
                    if name_node and name_node.type in {"identifier", "variable_identifier"}:
                        parameters.append(name_node.text.decode("utf8"))
                        continue
                    for grand in ch.children:
                        if grand.type in {"identifier", "variable_identifier"}:
                            parameters.append(grand.text.decode("utf8"))
                            break
        except Exception as e:
            self.logger.debug("Error extracting C# parameters", error=str(e))

        seen = set()
        ordered: List[str] = []
        for p in parameters:
            if p not in seen:
                ordered.append(p)
                seen.add(p)
        return ordered


class GoVisitor(NodeVisitor):
    """Visitor for Go files (Gin, Echo, Gorilla Mux)"""

    def visit(self, node: tree_sitter.Node) -> None:
        """Visit node and extract Go routes"""
        if node.type == "call_expression":
            self._visit_call_expression(node)
        for child in node.children:
            self.visit(child)

    def _visit_call_expression(self, node: tree_sitter.Node) -> None:
        """Extract routes from Go HTTP handlers"""
        try:
            # Look for patterns like: router.GET("/path", handler)
            # or: app.Post("/path", handler)
            func_node = node.child_by_field_name("function")
            if not func_node:
                return

            # Check if it's a method call
            if func_node.type == "selector_expression":
                # Get the property (GET, POST, etc.)
                field_node = func_node.child_by_field_name("field")
                if field_node:
                    method_name = field_node.text.decode("utf8").upper()
                    if method_name in ["GET", "POST", "PUT", "DELETE", "PATCH"]:
                        method = method_name.lower()

                        # Get arguments
                        args_node = node.child_by_field_name("arguments")
                        if args_node and args_node.child_count >= 1:
                            path_arg = args_node.children[0]
                            path = self._extract_string(path_arg)
                            if path:
                                line = node.start_point[0] + 1
                                self.add_endpoint(
                                    method=method,
                                    path=path,
                                    line_number=line
                                )

        except Exception as e:
            self.errors.append({
                "file": str(self.file_path),
                "line": node.start_point[0] + 1,
                "error": str(e),
                "type": "call_expression_processing"
            })

    def _extract_string(self, node: tree_sitter.Node) -> Optional[str]:
        """Extract string from Go node"""
        if node.type == "raw_string_literal" or node.type == "interpreted_string_literal":
            text = node.text.decode("utf8")
            # Remove quotes
            if len(text) >= 2:
                return text[1:-1]
        return None


def get_visitor_for_language(language: str) -> Optional[NodeVisitor]:
    """Get the appropriate visitor class for a language"""
    visitors = {
        "javascript": JavaScriptVisitor,
        "typescript": JavaScriptVisitor,  # Same visitor for TypeScript
        "python": PythonVisitor,
        "java": JavaVisitor,
        "csharp": CSharpVisitor,
        "go": GoVisitor,
        # Add more as implemented
    }
    return visitors.get(language)