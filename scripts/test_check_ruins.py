"""Python-only regressions for read-only checks and explicit ruin regeneration."""
import contextlib
import gzip
import hashlib
import io
import json
from pathlib import Path
import runpy
import shutil
import subprocess
import sys
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
CHECK = runpy.run_path(str(ROOT / "scripts/check-ruins.py"))["check"]
LAYOUT = Path("src/main/resources/orimod/ruin_layout/forest_spring.json")
MODEL = Path("src/main/resources/assets/orimod/models/block/forest_spring_shrine.json")
NBT = Path("src/main/resources/data/orimod/structure/ruins/forest_spring.nbt")
BLOCKSTATE = Path("src/main/resources/assets/orimod/blockstates/forest_spring_shrine.json")


def snapshot(root):
    return {p.relative_to(root): (p.read_bytes(), p.stat().st_mtime_ns)
            for p in root.rglob("*") if p.is_file()}


class RuinCheckTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.generated_dir = tempfile.TemporaryDirectory(prefix="orimod-ruins-test-source-")
        cls.addClassCleanup(cls.generated_dir.cleanup)
        cls.generated = Path(cls.generated_dir.name)
        subprocess.run([sys.executable, "-B", str(ROOT / "scripts/art/build_ruins.py"),
                        "--output-root", str(cls.generated)], check=True, capture_output=True)
        cls.assets = {p: data for p, (data, _) in snapshot(cls.generated).items()}

    def setUp(self):
        directory = tempfile.TemporaryDirectory(prefix="orimod-ruins-test-")
        self.addCleanup(directory.cleanup)
        self.root = Path(directory.name)
        shutil.copytree(self.generated, self.root, dirs_exist_ok=True)

    def check(self, write=False):
        output = io.StringIO()
        with contextlib.redirect_stdout(output):
            code = CHECK(self.root, write=write)
        return code, output.getvalue()

    def assert_read_only(self, expected_code):
        before = snapshot(self.root)
        code, output = self.check()
        self.assertEqual(code, expected_code, output)
        self.assertEqual(snapshot(self.root), before)
        return output

    def test_generated_bytes_match_packaged_assets(self):
        self.assertEqual(len(self.assets), 17)
        for path, data in self.assets.items():
            with self.subTest(path=path):
                self.assertEqual(data, (ROOT / path).read_bytes())
                if path.suffix == ".nbt":
                    self.assertEqual(data[:10], bytes.fromhex("1f8b080000000000020a"))

    def test_default_check_preserves_bytes_and_mtimes(self):
        self.assert_read_only(0)

    def test_json_format_and_gzip_metadata_are_ignored_without_rewriting(self):
        layout = json.loads((self.root / LAYOUT).read_text(encoding="utf-8"))
        (self.root / LAYOUT).write_text(json.dumps(layout, sort_keys=True), encoding="utf-8")
        raw = gzip.decompress(self.assets[NBT])
        changed = bytearray(gzip.compress(raw, compresslevel=1, mtime=12345))
        changed[9] = 255
        (self.root / NBT).write_bytes(changed)
        self.assert_read_only(0)

    def test_layout_model_and_nbt_content_drift_fail_without_writing(self):
        layout = json.loads(self.assets[LAYOUT])
        del layout["storm_altar"]
        (self.root / LAYOUT).write_text(json.dumps(layout), encoding="utf-8")
        model = json.loads(self.assets[MODEL])
        model["textures"]["top"] = "minecraft:block/respawn_anchor_top"
        (self.root / MODEL).write_text(json.dumps(model), encoding="utf-8")
        # Keep valid NBT with one different block name of the same byte length.
        raw = gzip.decompress(self.assets[NBT]).replace(b"minecraft:stone", b"minecraft:glass")
        (self.root / NBT).write_bytes(gzip.compress(raw, mtime=0))
        output = self.assert_read_only(1)
        for path in (LAYOUT, MODEL, NBT):
            self.assertIn(f"{path}: content differs", output)

    def test_missing_and_malformed_assets_fail_without_writing(self):
        (self.root / BLOCKSTATE).unlink()
        (self.root / MODEL).write_text("{", encoding="utf-8")
        (self.root / NBT).write_bytes(b"not gzip")
        output = self.assert_read_only(1)
        for path in (BLOCKSTATE, MODEL, NBT):
            self.assertIn(str(path), output)

    def test_write_repairs_assets_and_is_idempotent(self):
        (self.root / BLOCKSTATE).unlink()
        (self.root / LAYOUT).write_text("{}", encoding="utf-8")
        (self.root / MODEL).write_text("{}", encoding="utf-8")
        (self.root / NBT).write_bytes(b"not gzip")
        code, output = self.check(write=True)
        self.assertEqual(code, 0, output)
        self.assertIn('"written": 4', output)
        self.assertEqual({p: data for p, (data, _) in snapshot(self.root).items()}, self.assets)
        before = snapshot(self.root)
        code, output = self.check(write=True)
        self.assertEqual(code, 0, output)
        self.assertIn('"written": 0', output)
        self.assertEqual(snapshot(self.root), before)

    def test_invalid_generated_contract_cannot_be_published(self):
        def invalid_generator(command, **kwargs):
            destination = Path(command[-1])
            shutil.copytree(self.generated, destination, dirs_exist_ok=True)
            layout = json.loads(self.assets[LAYOUT])
            layout["size"][0] += 1
            (destination / LAYOUT).write_text(json.dumps(layout), encoding="utf-8")

        before = snapshot(self.root)
        with patch("subprocess.run", side_effect=invalid_generator):
            with self.assertRaisesRegex(ValueError, "template/layout size mismatch"):
                self.check(write=True)
        self.assertEqual(snapshot(self.root), before)

    def test_generator_requires_explicit_output_root(self):
        before = snapshot(self.root)
        result = subprocess.run([sys.executable, "-B", str(ROOT / "scripts/art/build_ruins.py")],
                                cwd=self.root, capture_output=True, text=True)
        self.assertEqual(result.returncode, 2)
        self.assertIn("--output-root", result.stderr)
        self.assertEqual(snapshot(self.root), before)

    def test_secret_additions_preserve_the_entire_pre_r103_route_contract(self):
        # Batch 5 dressing may raise a template (headroom for the tree crown) and restyle an anchor's material;
        # every route position, footprint and feature stays pinned.
        expected = {
            "forest_spring": "208c361fe14c348b8704bfc4696779a80bce0a2f678ed25482316d22d7abba66",
            "creek_ruins": "08ec57863acbc97f285497b9835af92b1d99dfd4beb1a86b3f9f6066adf9da45",
            "cave_altar": "6ce92249500e3f10ab0603931cfb23e47ccbcd86dddaed6b5956609b04818f1b"}
        for name,digest in expected.items():
            layout=json.loads(self.assets[Path(f"src/main/resources/orimod/ruin_layout/{name}.json")])
            del layout["secret"];del layout["vessels"];del layout["bridges"];layout.pop("beetles",None)
            layout["size"]=[layout["size"][0],layout["size"][2]]
            layout["critical"]=[{"position":c["position"]} for c in layout["critical"]]
            self.assertEqual(hashlib.sha256(json.dumps(layout,sort_keys=True,separators=(",",":")).encode()).hexdigest(),digest,name)

    def test_invalid_secret_seal_cannot_be_published(self):
        def invalid_generator(command, **kwargs):
            destination=Path(command[-1]);shutil.copytree(self.generated,destination,dirs_exist_ok=True)
            layout=json.loads(self.assets[LAYOUT]);layout["secret"]["entrance"]=[0,0,0]
            (destination/LAYOUT).write_text(json.dumps(layout),encoding="utf-8")
        before=snapshot(self.root)
        with patch("subprocess.run",side_effect=invalid_generator):
            with self.assertRaisesRegex(ValueError,"entrance not in seal"):self.check(write=True)
        self.assertEqual(snapshot(self.root),before)

    def test_invalid_bridge_contract_cannot_be_published(self):
        for field,value in (("platforms",[]),("entry",[0,0,0]),("escape",[[0,0,0]])):
            with self.subTest(field=field):
                def invalid_generator(command, **kwargs):
                    destination=Path(command[-1]);shutil.copytree(self.generated,destination,dirs_exist_ok=True)
                    layout=json.loads(self.assets[LAYOUT]);layout["bridges"][0][field]=value
                    (destination/LAYOUT).write_text(json.dumps(layout),encoding="utf-8")
                before=snapshot(self.root)
                with patch("subprocess.run",side_effect=invalid_generator):
                    with self.assertRaisesRegex(ValueError,"bridge|escape"):self.check(write=True)
                self.assertEqual(snapshot(self.root),before)

    def test_invalid_beetle_points_cannot_be_published(self):
        layout_path=Path("src/main/resources/orimod/ruin_layout/creek_ruins.json")
        for points in ([],[[15,6,5],[15,6,5]],[[0,0,0],[1,6,20]]):
            with self.subTest(points=points):
                def invalid_generator(command, **kwargs):
                    destination=Path(command[-1]);shutil.copytree(self.generated,destination,dirs_exist_ok=True)
                    layout=json.loads(self.assets[layout_path]);layout["beetles"]=points
                    (destination/layout_path).write_text(json.dumps(layout),encoding="utf-8")
                before=snapshot(self.root)
                with patch("subprocess.run",side_effect=invalid_generator):
                    with self.assertRaisesRegex(ValueError,"beetle"):self.check(write=True)
                self.assertEqual(snapshot(self.root),before)


if __name__ == "__main__":
    unittest.main()
