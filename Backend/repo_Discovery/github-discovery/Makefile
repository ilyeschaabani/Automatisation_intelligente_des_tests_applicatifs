.PHONY: help install test lint format clean run

help:
	@echo "GitHub API Discovery Pipeline"
	@echo ""
	@echo "Usage:"
	@echo "  make install     Install dependencies"
	@echo "  make test        Run tests with coverage"
	@echo "  make lint        Run linters"
	@echo "  make format      Format code with black"
	@echo "  make clean       Clean build artifacts"
	@echo "  make run         Run pipeline on example repo"
	@echo ""
	@echo "Environment:"
	@echo "  export OPENAI_API_KEY=your-key  # Optional LLM augmentation"

install:
	pip install -r requirements.txt
	pip install -e ".[dev,llm]"

test:
	pytest tests/ -v --cov=src --cov-report=html --cov-report=term

lint:
	flake8 src/
	mypy src/

format:
	black src/ tests/

clean:
	rm -rf repos/ output/
	rm -rf .coverage htmlcov/
	find . -type d -name "__pycache__" -exec rm -rf {} +
	find . -type f -name "*.pyc" -delete

run:
	python -m src.cli https://github.com/expressjs/express

run-test:
	python -m src.cli https://github.com/pallets/flask --keep-repo