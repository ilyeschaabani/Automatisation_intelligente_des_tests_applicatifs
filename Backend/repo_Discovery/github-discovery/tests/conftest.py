"""Pytest configuration and fixtures"""

import pytest
from src.config import Config
from src.utils.logger import setup_logger


def pytest_configure(config):
    """Configure pytest"""
    setup_logger("WARNING")  # Reduce log noise during tests


@pytest.fixture
def test_config():
    """Provide test configuration"""
    return Config()