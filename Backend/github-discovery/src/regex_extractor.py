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

        # Look for common patterns
        for line_num, line in enumerate(lines, 1):
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
                        method=HTTPMethod.get,  # Default to GET
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
                        method=HTTPMethod.get,  # Django handlers handle all methods
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
                        method=HTTPMethod.get,  # Default to GET
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
                        method=HTTPMethod.get,  # Default to GET
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

        for line_num, line in enumerate(lines, 1):
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
                        method=HTTPMethod.get,
                        path=full_path,
                        file_path=str(file_path),
                        line_number=line_num,
                        confidence=0.80,
                        source='regex',
                        metadata={'pattern': 'spring_requestmapping_simple'}
                    ))

        return endpoints

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
        else:
            self.logger.debug("Regex extraction not supported for language", language=language)
            return []
