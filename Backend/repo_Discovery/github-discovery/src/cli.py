"""Command-line interface"""

import argparse
import os
import sys
from pathlib import Path
from typing import Any, Dict

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
        "--complete-discovery",
        action="store_true",
        help=(
            "Interactively ask for missing/low-confidence x-discovery fields in the terminal, "
            "apply answers, and write a runnable *_openapi.json in one run."
        ),
    )

    parser.add_argument(
        "--complete-discovery-patch-out",
        type=str,
        default=None,
        help="If --complete-discovery is used, write the generated JSON Merge Patch to this file.",
    )

    parser.add_argument(
        "--contract-interview",
        action="store_true",
        help=(
            "If the generated OpenAPI is not runnable (missing discovery fields), emit an LLM interview prompt "
            "that asks the user for the minimum missing info and outputs a JSON Merge Patch."
        ),
    )

    parser.add_argument(
        "--contract-interview-out",
        type=str,
        default=None,
        help="Write the contract interview prompt to a file instead of stdout",
    )

    parser.add_argument(
        "--contract-interview-llm",
        action="store_true",
        help=(
            "Call the configured LLM to generate the actual question list + a JSON Merge Patch template "
            "for completing x-discovery. Prints JSON to stdout."
        ),
    )

    parser.add_argument(
        "--contract-interview-llm-out",
        type=str,
        default=None,
        help="Write the LLM-produced interview JSON to a file instead of stdout",
    )

    parser.add_argument(
        "--apply-discovery-patch",
        type=str,
        default=None,
        help=(
            "Apply an RFC 7396 JSON Merge Patch to the generated OpenAPI (e.g. from contract interview answers). "
            "This updates servers[0].url from x-discovery.run.base_url and recomputes x-discovery.discovery.missing."
        ),
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

        def _set_patch_value(patch: Dict[str, Any], json_path: str, value: Any) -> None:
            """Set a value in a dict patch using a simple $.a.b.c path."""

            if not json_path.startswith("$."):
                raise ValueError(f"Unsupported json_path: {json_path}")
            parts = [p for p in json_path[2:].split(".") if p]
            cur: Dict[str, Any] = patch
            for part in parts[:-1]:
                if part not in cur or not isinstance(cur.get(part), dict):
                    cur[part] = {}
                cur = cur[part]
            cur[parts[-1]] = value

        # Optional: interactively complete missing discovery info before final output.
        if args.complete_discovery:
            from .contract_interview import build_findings, render_questionnaire
            from .contract_patch import apply_discovery_patch
            import json as _json

            openapi = result.get("openapi") if isinstance(result, dict) else None
            if not isinstance(openapi, dict):
                raise RuntimeError("Pipeline did not return an OpenAPI object")

            findings = build_findings(openapi)
            questionnaire = render_questionnaire(openapi, findings)
            questions = questionnaire.get("questions")
            if not isinstance(questions, list):
                questions = []

            if questions:
                print("\n" + "=" * 60)
                print("DISCOVERY COMPLETION (interactive)")
                print("Answer the following to make the OpenAPI runnable.")
                print("Leave blank to keep current value.")
                print("=" * 60)

                patch: Dict[str, Any] = {}

                for idx, q in enumerate(questions, start=1):
                    if not isinstance(q, dict):
                        continue
                    jp = str(q.get("json_path") or "")
                    if not jp:
                        continue

                    print(f"\n[{idx}] {jp}")
                    reason = q.get("reason")
                    if reason:
                        print(f"Reason: {reason}")
                    ef = q.get("expected_format")
                    if ef:
                        print(f"Expected: {ef}")
                    ex = q.get("example")
                    if ex:
                        print(f"Example: {ex}")
                    htf = q.get("how_to_find")
                    if htf:
                        print(f"How to find: {htf}")

                    opts = q.get("options")
                    if isinstance(opts, list) and opts:
                        print("Options:")
                        for o in opts:
                            print(f"  - {o}")

                    raw = input("Your value: ").strip()
                    if raw == "":
                        continue

                    # Basic normalization: allow entering "null" to unset.
                    if raw.lower() == "null":
                        value: Any = None
                    else:
                        value = raw

                    _set_patch_value(patch, jp, value)

                if patch:
                    if args.complete_discovery_patch_out:
                        Path(args.complete_discovery_patch_out).write_text(
                            _json.dumps(patch, indent=2, ensure_ascii=False),
                            encoding="utf-8",
                        )
                        print(f"\nDiscovery patch written to: {args.complete_discovery_patch_out}")

                    openapi = apply_discovery_patch(openapi, patch)
                    result["openapi"] = openapi

                    if result.get("repo_id"):
                        openapi_path = Path(config.output_dir) / f"{result['repo_id']}_openapi.json"
                        openapi_path.write_text(
                            _json.dumps(openapi, indent=2, ensure_ascii=False, default=str),
                            encoding="utf-8",
                        )
                        print(f"\nRunnable OpenAPI overwritten at: {openapi_path}")
            else:
                print("\nDiscovery completion: no questions needed (already complete).")

        # Print summary
        print("\n" + "="*60)
        print("PIPELINE COMPLETED SUCCESSFULLY")
        print("="*60)
        print(f"Repository: {args.repo_url}")
        if result.get("repo_id"):
            print(f"Repo ID: {result['repo_id']}")
        print(f"Endpoints found: {result['stats']['endpoints_found']}")
        print(f"Files processed: {result['stats']['files_processed']}/{result['stats']['total_files']}")
        print(f"Duplicates removed: {result['stats']['duplicates_removed']}")
        print(f"Duration: {result['stats']['duration_seconds']:.2f}s")
        print("\nOutput files:")
        print(f"  OpenAPI: {config.output_dir}/<repo_id>_openapi.json")
        print(f"  Raw endpoints: {config.output_dir}/<repo_id>_endpoints.json")
        print(f"  Stats: {config.output_dir}/<repo_id>_stats.json")
        print("="*60)

        # If x-discovery is incomplete, guide the user to fill the missing fields.
        openapi = result.get("openapi") if isinstance(result, dict) else None
        xdisc = openapi.get("x-discovery") if isinstance(openapi, dict) else None
        missing = None
        if isinstance(xdisc, dict):
            discovery = xdisc.get("discovery")
            if isinstance(discovery, dict):
                missing = discovery.get("missing")

        missing_list = [str(x) for x in (missing or []) if x]
        if missing_list:
            print("\nDISCOVERY CONFIG INCOMPLETE (spec not fully runnable for auto-run + auto-test)")
            for item in sorted(set(missing_list)):
                print(f"  - {item}")

            if result.get("repo_id"):
                openapi_path = Path(config.output_dir) / f"{result['repo_id']}_openapi.json"
                print("\nTo generate an LLM interview prompt:")
                print(f"  python -m src.contract_interview {openapi_path}")

        if args.contract_interview:
            from .contract_interview import build_findings, render_llm_prompt

            if not isinstance(openapi, dict):
                print("\nContract interview requested, but OpenAPI output is missing.", file=sys.stderr)
                return 1

            findings = build_findings(openapi)
            prompt = render_llm_prompt(openapi, findings)

            if args.contract_interview_out:
                Path(args.contract_interview_out).write_text(prompt, encoding="utf-8")
                print(f"\nContract interview prompt written to: {args.contract_interview_out}")
            else:
                print("\n" + "-" * 60)
                print("CONTRACT INTERVIEW PROMPT")
                print("-" * 60)
                print(prompt)

        if args.contract_interview_llm:
            from .contract_interview import build_findings, render_llm_json_request

            if not isinstance(openapi, dict):
                print("\nContract interview (LLM) requested, but OpenAPI output is missing.", file=sys.stderr)
                return 1

            if not getattr(pipeline, "llm_extractor", None) or not pipeline.llm_extractor.is_available():
                print(
                    "\nContract interview (LLM) requested, but LLM extractor is not available. "
                    "Configure LLM_BASE_URL/LLM_MODEL (or OPENAI_API_KEY/OPENROUTER_API_KEY).",
                    file=sys.stderr,
                )
                return 1

            findings = build_findings(openapi)
            prompts = render_llm_json_request(openapi, findings)
            data = pipeline.llm_extractor.generate_json(
                system_prompt=prompts["system"],
                user_prompt=prompts["user"],
                temperature=0.0,
                max_tokens=2000,
            )

            import json as _json

            out_text = _json.dumps(data, indent=2, ensure_ascii=False)
            if args.contract_interview_llm_out:
                Path(args.contract_interview_llm_out).write_text(out_text, encoding="utf-8")
                print(f"\nContract interview (LLM) JSON written to: {args.contract_interview_llm_out}")
            else:
                print("\n" + "-" * 60)
                print("CONTRACT INTERVIEW (LLM) JSON")
                print("-" * 60)
                print(out_text)

        if args.apply_discovery_patch:
            if not result.get("repo_id"):
                print("\nCannot apply patch: repo_id not available.", file=sys.stderr)
                return 1

            from .contract_patch import apply_discovery_patch
            import json as _json

            openapi_path = Path(config.output_dir) / f"{result['repo_id']}_openapi.json"
            patch_path = Path(args.apply_discovery_patch)
            if not patch_path.exists():
                print(f"\nPatch file not found: {patch_path}", file=sys.stderr)
                return 1

            patch_obj = _json.loads(patch_path.read_text(encoding="utf-8"))
            if not isinstance(patch_obj, dict):
                print("\nPatch must be a JSON object (RFC 7396).", file=sys.stderr)
                return 1

            updated = apply_discovery_patch(openapi, patch_obj)
            openapi_path.write_text(_json.dumps(updated, indent=2, ensure_ascii=False, default=str), encoding="utf-8")
            print(f"\nPatched OpenAPI written to: {openapi_path}")

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