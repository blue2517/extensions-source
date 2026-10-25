import os
import re
import tomllib
from pathlib import Path


def prepare_build(root: Path) -> tuple[list[str], str]:
    sources = []
    for line in (root / ".github/personal-sources.txt").read_text("utf-8").splitlines():
        source = line.partition("#")[0].strip()
        if not source:
            continue
        if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]*/[a-z0-9_]+", source):
            raise ValueError(f"Invalid personal source: {source!r}; expected lang/name")
        if not (root / "src" / source / "build.gradle.kts").is_file():
            raise ValueError(f"Missing extension build file: src/{source}/build.gradle.kts")
        if source not in sources:
            sources.append(source)
    if not sources:
        raise ValueError("The personal source list is empty")

    versions = tomllib.loads((root / "gradle/kei.versions.toml").read_text("utf-8"))
    compile_sdk = versions["versions"]["android-sdk-compile"]
    if not re.fullmatch(r"[0-9]+", compile_sdk):
        raise ValueError("Expected an integer android-sdk-compile version")

    settings_file = root / "settings.gradle.kts"
    settings = settings_file.read_text("utf-8")
    includes = "\n".join(
        f'loadIndividualExtension("{lang}", "{name}")'
        for lang, name in (source.split("/") for source in sources)
    )
    settings, replacements = re.subn(
        r"^loadAllIndividualExtensions\(\)$", includes, settings, flags=re.MULTILINE
    )
    if replacements != 1:
        raise ValueError("Expected one loadAllIndividualExtensions() call in settings.gradle.kts")
    settings_file.write_text(settings, encoding="utf-8", newline="\n")
    return [":src:" + source.replace("/", ":") for source in sources], compile_sdk


if __name__ == "__main__":
    output_file = Path(os.environ["GITHUB_OUTPUT"])
    modules, compile_sdk = prepare_build(Path(__file__).resolve().parents[2])
    with output_file.open("a", encoding="utf-8", newline="\n") as output:
        output.write(f"modules={' '.join(modules)}\ncompile_sdk={compile_sdk}\n")
    print("Personal build modules:", " ".join(modules))
