"""Tests for Symfony/PHP regex extraction"""

from pathlib import Path

from src.regex_extractor import RegexExtractor
from src.models.endpoint import HTTPMethod


def test_symfony_attributes_and_annotations_extraction():
    code = """
<?php

namespace App\\Controller;

use Symfony\\Component\\Routing\\Annotation\\Route;

#[Route('/api')]
class UserController
{
    #[Route('/users', name: 'users_list', methods: ['GET'])]
    public function list() {}

    /**
     * @Route("/users/{id}", methods={"GET", "PUT"})
     */
    public function item($id) {}
}
""".strip()

    extractor = RegexExtractor()
    endpoints = extractor.extract_endpoints(code, Path("src/Controller/UserController.php"), "php")

    got = {(e.method, e.full_path) for e in endpoints}

    assert (HTTPMethod.GET, "/api/users") in got
    assert (HTTPMethod.GET, "/api/users/{id}") in got
    assert (HTTPMethod.PUT, "/api/users/{id}") in got
