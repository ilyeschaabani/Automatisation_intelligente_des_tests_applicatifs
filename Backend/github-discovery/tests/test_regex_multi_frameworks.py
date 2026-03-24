from pathlib import Path

from src.regex_extractor import RegexExtractor


def test_regex_nestjs_controller_and_methods(tmp_path: Path):
    code = """
import { Controller, Get, Post } from '@nestjs/common';

@Controller('users')
export class UsersController {
  @Get(':id')
  getUser() {}

  @Post('')
  create() {}
}
"""
    extractor = RegexExtractor()
    eps = extractor.extract_endpoints(code, tmp_path / "users.controller.ts", "typescript")
    paths = {(e.method.value, e.full_path) for e in eps}
    assert ("get", "/users/:id") in paths or ("get", "/users/{id}") in paths
    assert ("post", "/users") in paths


def test_regex_laravel_route_facade(tmp_path: Path):
    code = """
<?php
use Illuminate\\Support\\Facades\\Route;

Route::get('/health', function () { return 'ok'; });
Route::post('api/login', [AuthController::class, 'login']);
"""
    extractor = RegexExtractor()
    eps = extractor.extract_endpoints(code, tmp_path / "routes.php", "php")
    paths = {(e.method.value, e.path) for e in eps}
    assert ("get", "/health") in paths
    assert ("post", "api/login") in paths or ("post", "/api/login") in paths


def test_regex_go_gin_echo_style(tmp_path: Path):
    code = """
package main

func main() {
  r := gin.Default()
  r.GET("/health", handler)
  e := echo.New()
  e.POST("/api/login", handler)
}
"""
    extractor = RegexExtractor()
    eps = extractor.extract_endpoints(code, tmp_path / "main.go", "go")
    paths = {(e.method.value, e.path) for e in eps}
    assert ("get", "/health") in paths
    assert ("post", "/api/login") in paths


def test_regex_csharp_aspnet_attributes(tmp_path: Path):
    code = """
using Microsoft.AspNetCore.Mvc;

[ApiController]
[Route("api/users")]
public class UsersController : ControllerBase
{
    [HttpGet("{id}")]
    public IActionResult GetById(int id) => Ok();

    [HttpPost]
    public IActionResult Create() => Ok();
}
"""
    extractor = RegexExtractor()
    eps = extractor.extract_endpoints(code, tmp_path / "UsersController.cs", "csharp")
    # We combine by router_prefix in Endpoint.__post_init__, so check full_path
    paths = {(e.method.value, e.full_path) for e in eps}
    assert ("get", "/api/users/{id}") in paths or ("get", "/api/users/{id}") in paths
    assert ("post", "/api/users") in paths


def test_regex_java_micronaut_and_jaxrs(tmp_path: Path):
    code = """
import io.micronaut.http.annotation.*;
import javax.ws.rs.*;

@Controller("/api")
class Mic {
  @Get("/health")
  String h(){ return "ok"; }
}

@Path("/v1")
public class Jax {
  @GET
  @Path("/users")
  public String list(){ return ""; }
}
"""
    extractor = RegexExtractor()
    eps = extractor.extract_endpoints(code, tmp_path / "X.java", "java")
    paths = {(e.method.value, e.path) for e in eps}
    assert ("get", "/api/health") in paths
    assert ("get", "/v1/users") in paths
