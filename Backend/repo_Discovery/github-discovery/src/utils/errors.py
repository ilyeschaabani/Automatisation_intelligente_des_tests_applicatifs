"""Custom error classes for the pipeline"""


class PipelineError(Exception):
    """Base exception for pipeline errors"""

    def __init__(self, message: str, details: dict = None):
        super().__init__(message)
        self.details = details or {}

    def __str__(self):
        if self.details:
            return f"{self.__class__.__name__}: {super().__str__()} | Details: {self.details}"
        return f"{self.__class__.__name__}: {super().__str__()}"


class RepositoryError(PipelineError):
    """Repository-related errors"""


class CloneError(RepositoryError):
    """Failed to clone repository"""


class TechStackDetectionError(PipelineError):
    """Failed to detect tech stack"""


class FileProcessingError(PipelineError):
    """File processing errors"""


class ExtractionError(PipelineError):
    """Endpoint extraction errors"""


class ASTError(ExtractionError):
    """AST parsing errors"""


class LLMError(ExtractionError):
    """LLM extraction errors"""


class RouteResolutionError(PipelineError):
    """Route prefix resolution errors"""


class OpenAPIGenerationError(PipelineError):
    """OpenAPI generation errors"""


class ConfigurationError(PipelineError):
    """Configuration errors"""