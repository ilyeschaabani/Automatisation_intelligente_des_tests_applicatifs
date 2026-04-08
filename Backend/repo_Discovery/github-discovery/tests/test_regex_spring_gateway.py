import pytest
from pathlib import Path

from src.regex_extractor import RegexExtractor


def test_spring_cloud_gateway_route_dsl_extraction():
    extractor = RegexExtractor()

    code = """
package demo;

import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;

public class GatewayApplication {
  public RouteLocator gatewayRoutes(RouteLocatorBuilder builder){
    return builder.routes()
      .route(\"auth\", r -> r.path(\"/api/auth/**\").uri(\"lb://auth\"))
      .route(\"ticket\", r -> r.path(\"/api/ticket/**\", \"/api/categories/**\").uri(\"lb://ticket\"))
      .build();
  }
}
"""

    endpoints = extractor.extract_from_java(code, Path("GatewayApplication.java"))
    paths = sorted({e.path for e in endpoints})

    assert "/api/auth/**" in paths
    assert "/api/ticket/**" in paths
    assert "/api/categories/**" in paths

    # These are gateway routes, method isn't explicit in DSL -> default GET
    assert all(e.method.value == "get" for e in endpoints)
    assert all(e.source == "regex" for e in endpoints)
