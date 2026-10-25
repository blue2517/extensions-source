import importlib.util
import tempfile
import unittest
from pathlib import Path

SCRIPT = Path(__file__).resolve().parents[1] / "prepare-personal-build.py"
SPEC = importlib.util.spec_from_file_location("personal_build", SCRIPT)
PERSONAL_BUILD = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(PERSONAL_BUILD)


class PersonalBuildTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.root = Path(self.temp.name)
        self.write(".github/personal-sources.txt", "zh/jinmantiantang\n")
        self.write("src/zh/jinmantiantang/build.gradle.kts", "")
        self.write("gradle/kei.versions.toml", '[versions]\nandroid-sdk-compile = "37"\n')
        self.original_settings = (
            'loadAllIndividualExtensions()\ninclude(":core")\n'
            'include(":compiler")\n// shared libraries remain loaded\n'
        )
        self.write("settings.gradle.kts", self.original_settings)

    def write(self, name, content):
        path = self.root / name
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8")

    def test_only_selected_extensions_replace_the_all_extensions_call(self):
        modules, sdk = PERSONAL_BUILD.prepare_build(self.root)
        self.assertEqual(modules, [":src:zh:jinmantiantang"])
        self.assertEqual(sdk, "37")
        settings = (self.root / "settings.gradle.kts").read_text("utf-8")
        self.assertEqual(
            settings,
            self.original_settings.replace(
                "loadAllIndividualExtensions()",
                'loadIndividualExtension("zh", "jinmantiantang")',
            ),
        )

    def test_comments_duplicates_and_multiple_sources(self):
        self.write(
            ".github/personal-sources.txt",
            "# maintained sources\nzh/jinmantiantang # comment\n\n"
            "zh/jinmantiantang\npt-BR/example\n",
        )
        self.write("src/pt-BR/example/build.gradle.kts", "")
        modules, _ = PERSONAL_BUILD.prepare_build(self.root)
        self.assertEqual(modules, [":src:zh:jinmantiantang", ":src:pt-BR:example"])

    def test_empty_list_is_rejected(self):
        self.write(".github/personal-sources.txt", "# no sources\n")
        with self.assertRaisesRegex(ValueError, "empty"):
            PERSONAL_BUILD.prepare_build(self.root)

    def test_invalid_source_paths_are_rejected_without_changing_settings(self):
        for source in ("../core", "zh/../../core", "zh/name:assemble", "zh/name;echo", "/zh/name"):
            with self.subTest(source=source):
                self.write(".github/personal-sources.txt", source)
                with self.assertRaisesRegex(ValueError, "Invalid personal source"):
                    PERSONAL_BUILD.prepare_build(self.root)
                self.assertEqual(
                    (self.root / "settings.gradle.kts").read_text("utf-8"),
                    self.original_settings,
                )

    def test_missing_extension_is_rejected(self):
        self.write(".github/personal-sources.txt", "zh/missing")
        with self.assertRaisesRegex(ValueError, "Missing extension"):
            PERSONAL_BUILD.prepare_build(self.root)

    def test_settings_layout_changes_are_rejected(self):
        for settings in ('include(":core")\n', "loadAllIndividualExtensions()\n" * 2):
            with self.subTest(settings=settings):
                self.write("settings.gradle.kts", settings)
                with self.assertRaisesRegex(ValueError, "Expected one"):
                    PERSONAL_BUILD.prepare_build(self.root)
                self.assertEqual((self.root / "settings.gradle.kts").read_text("utf-8"), settings)

    def test_compile_sdk_is_read_from_the_repository(self):
        self.write("gradle/kei.versions.toml", '[versions]\nandroid-sdk-compile = "38"\n')
        _, sdk = PERSONAL_BUILD.prepare_build(self.root)
        self.assertEqual(sdk, "38")

    def test_invalid_compile_sdk_is_rejected(self):
        self.write("gradle/kei.versions.toml", '[versions]\nandroid-sdk-compile = "37;echo"\n')
        with self.assertRaisesRegex(ValueError, "integer"):
            PERSONAL_BUILD.prepare_build(self.root)


if __name__ == "__main__":
    unittest.main()
