"""For GloryFit menu-building methods, pair each isSupportFunction*(bit) check with the
next view field it toggles, and test the bit against Paul's T60 FunctionList values."""
import re, sys

dump, targets = sys.argv[1], sys.argv[2:]  # targets like FragmentSmartPlay.isSupportFunction
f1 = bytes.fromhex("08 08 44 2A 01 24 39 43 75 6F FF FE D9 21 00 5F 78 4B E1 DC")
f4 = bytes.fromhex("00 F4 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 04 02")
g = lambda b, i: int.from_bytes(b[i:i + 3], "big")
lists = {1: g(f1, 17), 2: g(f1, 14), 3: g(f1, 11), 4: g(f1, 8), 5: g(f1, 5), 6: g(f1, 2),
         7: int.from_bytes(f1[0:2], "big"), 8: g(f4, 17), 9: 0, 10: 0, 11: 0, 12: 0, 13: 0}
suffix = {"": 1, "_Second": 2, "_Third": 3, "_Fourth": 4, "_Fifth": 5, "_Sixth": 6, "_Seven": 7,
          "_8": 8, "_9": 9, "_10": 10, "_11": 11, "_12": 12, "_13": 13}

re_cls = re.compile(r"Class descriptor\s+: 'L([^;]+);'")
re_meth = re.compile(r"^\s+name\s+: '([^']+)'")
re_int = re.compile(r"\|[0-9a-f]+: const(?:/4|/16)? (v\d+), #int (-?\d+)")
re_high = re.compile(r"\|[0-9a-f]+: const/high16 (v\d+), #int (-?\d+)")
re_flt = re.compile(r"\|[0-9a-f]+: const (v\d+), #float [^/]*// #([0-9a-f]+)")
re_call = re.compile(r"invoke-static(?:/range)? \{([^}]*)\}, Lcom/yc/pedometer/column/GetFunctionList;\.isSupportFunction(\w*):\(I\)Z")
re_view = re.compile(r"iget-object v\d+, v\d+, L[^;]+;\.(\w+):L(?:android|androidx|com)/[\w/]*(?:View|Layout|Text|Image|Relative|Linear|Constraint)\w*;")

cur, consts, pending = "", {}, []
with open(dump, encoding="utf-8", errors="replace") as fh:
    cls = ""
    for line in fh:
        m = re_cls.search(line)
        if m:
            cls = m.group(1).split("/")[-1]; continue
        m = re_meth.match(line)
        if m:
            cur = f"{cls}.{m.group(1)}"; consts = {}; pending = []; continue
        if cur not in targets:
            continue
        for rx, conv in ((re_int, int), (re_high, int), (re_flt, lambda h: int(h, 16))):
            m = rx.search(line)
            if m:
                consts[m.group(1)] = conv(m.group(2)) & 0xFFFFFFFF
        m = re_call.search(line)
        if m:
            reg = m.group(1).split(",")[0].strip()
            pending.append((suffix.get(m.group(2), 0), consts.get(reg)))
            continue
        m = re_view.search(line)
        if m and pending:
            for lst, bit in pending:
                on = "?" if bit is None or lst == 0 else ("YES" if lists[lst] & bit == bit else "no")
                print(f"{cur:40} {on:3} L{lst}:{'?' if bit is None else hex(bit):9} -> {m.group(1)}")
            pending = []
