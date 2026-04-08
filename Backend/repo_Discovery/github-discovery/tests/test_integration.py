"""Integration tests with sample repositories"""

import pytest
from pathlib import Path
from src.pipeline import Pipeline
from src.config import Config


@pytest.mark.integration
class TestIntegration:
    """Integration tests with real repositories"""

    def test_simple_express_repo(self, tmp_path: Path):
        """Test with a simple Express.js repository"""
        # This would clone a test repo and verify extraction
        # For now, we'll create a mock repo structure

        repo_dir = tmp_path / "test-repo"
        repo_dir.mkdir()

        # Create package.json
        package_json = repo_dir / "package.json"
        package_json.write_text("""
{
  "name": "test-api",
  "dependencies": {
    "express": "^4.18.0"
  }
}
""")

        # Create app.js with routes
        app_js = repo_dir / "app.js"
        app_js.write_text("""
const express = require('express');
const app = express();

app.get('/users', (req, res) => {
  res.json({ users: [] });
});

app.post('/users', (req, res) => {
  res.status(201).json({ id: 1 });
});

app.get('/users/:id', (req, res) => {
  res.json({ id: req.params.id });
});

app.use('/admin', require('./admin/routes'));

module.exports = app;
""")

        # Create admin routes
        admin_dir = repo_dir / "admin"
        admin_dir.mkdir()
        admin_routes = admin_dir / "routes.js"
        admin_routes.write_text("""
const router = require('express').Router();

router.get('/dashboard', (req, res) => {
  res.json({ admin: true });
});

router.post('/users', (req, res) => {
  res.status(201).json({ message: 'User created' });
});

module.exports = router;
""")

        # Run pipeline on local directory (simulate clone)
        # In real test, we'd use a test repo URL
        # For now, skip actual cloning

        # config = Config()
        # config.repos_dir = str(tmp_path / "repos")
        # pipeline = Pipeline(config)
        # result = pipeline.run(str(repo_dir))  # Would need to adapt for local path

        # assert result["success"]
        # assert len(result["final_endpoints"]) >= 4
        # paths = [ep["full_path"] for ep in result["final_endpoints"]]
        # assert "/users" in paths
        # assert "/users/:id" in paths or "/users/{id}" in paths
        # assert "/admin/dashboard" in paths
        # assert "/admin/users" in paths

        pytest.skip("Integration test requires actual repository cloning")

    def test_flask_repo(self, tmp_path: Path):
        """Test with a Flask repository"""
        pytest.skip("Integration test requires actual repository cloning")


if __name__ == "__main__":
    pytest.main([__file__, "-v"])