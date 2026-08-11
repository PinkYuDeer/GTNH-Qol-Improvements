from __future__ import annotations

import argparse
import zipfile
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RESOURCE_PACK = ROOT / "resourcepack/modernity-dark-ui"
DEFAULT_OUTPUT_DIRECTORY = ROOT / "release-assets"


def package(version: str, output_directory: Path) -> Path:
    if not RESOURCE_PACK.joinpath("pack.mcmeta").is_file():
        raise FileNotFoundError(f"Resource pack metadata not found: {RESOURCE_PACK / 'pack.mcmeta'}")

    files = sorted(path for path in RESOURCE_PACK.rglob("*") if path.is_file())
    if not files or not any(path.suffix.lower() == ".png" for path in files):
        raise RuntimeError(f"Resource pack contains no PNG assets: {RESOURCE_PACK}")

    output_directory.mkdir(parents=True, exist_ok=True)
    output = output_directory / f"GTNH-QoL-Improvements-Modernity-Dark-UI-{version}.zip"
    with zipfile.ZipFile(output, "w", compression=zipfile.ZIP_DEFLATED, compresslevel=9) as archive:
        for path in files:
            archive.write(path, path.relative_to(RESOURCE_PACK).as_posix())

    with zipfile.ZipFile(output) as archive:
        corrupt_entry = archive.testzip()
        if corrupt_entry is not None:
            raise RuntimeError(f"Corrupt resource-pack entry: {corrupt_entry}")

    return output


def main() -> None:
    parser = argparse.ArgumentParser(description="Package the Modernity Dark UI release asset.")
    parser.add_argument("version", help="Release tag used in the output filename.")
    parser.add_argument(
        "--output-directory",
        type=Path,
        default=DEFAULT_OUTPUT_DIRECTORY,
        help="Directory for the generated ZIP (default: release-assets).",
    )
    arguments = parser.parse_args()
    print(package(arguments.version, arguments.output_directory.resolve()))


if __name__ == "__main__":
    main()
