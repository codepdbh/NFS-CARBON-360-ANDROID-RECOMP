"""Ensure direct call optimization cannot bypass hooks or unresolved entries."""
import importlib.util
from pathlib import Path
spec = importlib.util.spec_from_file_location("calls", Path(__file__).resolve().parents[1] / "tools/android_direct_calls.py")
calls = importlib.util.module_from_spec(spec)
spec.loader.exec_module(calls)
source = """sub_824FFD30(ctx, base);
sub_82123456(ctx, base);
sub_82999999(ctx, base);
__imp__sub_82123456(ctx, base);
__savegprlr_29(ctx, base);
{0x82123456, sub_82123456},
"""
result = calls.transform(source, {"sub_824FFD30", "sub_82123456", "__savegprlr_29"}, {"sub_824FFD30"})
assert result == """sub_824FFD30(ctx, base);
__imp__sub_82123456(ctx, base);
sub_82999999(ctx, base);
__imp__sub_82123456(ctx, base);
__imp____savegprlr_29(ctx, base);
{0x82123456, sub_82123456},
"""
assert calls.transform(result, {"sub_82123456"}, set()) == result
print("Direct calls: hooks, unresolved entries, helpers, dispatch table and repeated transformation passed")
