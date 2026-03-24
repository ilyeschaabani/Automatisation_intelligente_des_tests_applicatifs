"""Command-line interface"""

import argparse
import os
import sys
from pathlib import Path

from .config import Config
from .pipeline import Pipeline
from .utils.logger import get_logger, setup_logger


def main():
    """Main CLI entry point"""
    parser = argparse.ArgumentParser(
        description="Extract OpenAPI 3.0 specification from a GitHub repository",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
Examples:
  %(prog)s https://github.com/owner/repo
  %(prog)s https://github.com/owner/repo --branch develop
  %(prog)s https://github.com/owner/repo --keep-repo
        """
    )

    parser.add_argument(
        "repo_url",
        help="GitHub repository URL"
    )

    parser.add_argument(
        "-b", "--branch",
        help="Branch to clone (default: default branch)"
    )

    parser.add_argument(
        "--keep-repo",
        action="store_true",
        help="Keep cloned repository for debugging (default: cleanup after)"
    )

    parser.add_argument(
        "--log-level",
        choices=["DEBUG", "INFO", "WARNING", "ERROR"],
        default=Config().log_level,
        help="Logging level (default: %(default)s)"
    )

    parser.add_argument(
        "--output-dir",
        type=str,
        default=Config().output_dir,
        help="Output directory for results (default: %(default)s)"
    )

    parser.add_argument(
        "--repos-dir",
        type=str,
        default=Config().repos_dir,
        help=(
            "Directory for cloned repositories (default: %(default)s). "
            "Tip (Windows): use a short path like C:\\r to avoid 'Filename too long'."
        )
    )

    parser.add_argument(
        "--max-workers",
        type=int,
        default=Config().max_workers,
        help="Number of parallel workers (default: %(default)s)"
    )

    parser.add_argument(
        "--version",
        action="version",
        version="%(prog)s 1.0.0"
    )

    args = parser.parse_args()

    # Setup logging
    setup_logger(args.log_level)
    logger = get_logger(__name__)

    try:
        # Update config with CLI args
        config = Config()
        config.output_dir = args.output_dir
        config.repos_dir = args.repos_dir
        config.max_workers = args.max_workers

        # Ensure overridden directories exist (Config.__post_init__ ran before overrides)
        os.makedirs(config.output_dir, exist_ok=True)
        os.makedirs(config.repos_dir, exist_ok=True)

        # Run pipeline
        logger.info("Starting GitHub API Discovery", repo_url=args.repo_url, branch=args.branch)

        pipeline = Pipeline(config)
        result = pipeline.run(
            repo_url=args.repo_url,
            branch=args.branch,
            keep_repo=args.keep_repo,
        )

        # Print summary
        print("\n" + "="*60)
        print("PIPELINE COMPLETED SUCCESSFULLY")
        print("="*60)
        print(f"Repository: {args.repo_url}")
        print(f"Endpoints found: {result['stats']['endpoints_found']}")
        print(f"Files processed: {result['stats']['files_processed']}/{result['stats']['total_files']}")
        print(f"Duplicates removed: {result['stats']['duplicates_removed']}")
        print(f"Duration: {result['stats']['duration_seconds']:.2f}s")
        print("\nOutput files:")
        print(f"  OpenAPI: {config.output_dir}/<repo_id>_openapi.json")
        print(f"  Raw endpoints: {config.output_dir}/<repo_id>_endpoints.json")
        print(f"  Stats: {config.output_dir}/<repo_id>_stats.json")
        print("="*60)

        return 0

    except KeyboardInterrupt:
        logger.warning("Pipeline interrupted by user")
        print("\nPipeline interrupted.", file=sys.stderr)
        return 130

    except Exception as e:
        logger.error("Pipeline failed", error=str(e), exc_info=True)
        print(f"\nError: {str(e)}", file=sys.stderr)
        return 1


if __name__ == "__main__":
    sys.exit(main())