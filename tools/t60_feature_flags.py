"""Map every GetFunctionList.isSupportFunction*(const) call in the GloryFit dexdump to
its caller, then mark which bits Paul's T60 has set (from the observed 33F1/34F1 reads)."""
import re, sys, collections

dump = sys.argv[1]
# Observed 2026-10-24/25. 33F1 parsed right-to-left in 3-byte groups; list7 = bytes 0..1.
f1 = bytes.fromhex("08 08 44 2A 01 24 39 43 75 6F FF FE D9 21 00 5F 78 4B E1 DC")
f4 = bytes.fromhex("00 F4 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 00 04 02")
g = lambda b, i: int.from_bytes(b[i:i + 3], "big")
lists = {1: g(f1, 17), 2: g(f1, 14), 3: g(f1, 11), 4: g(f1, 8), 5: g(f1, 5), 6: g(f1, 2),
         7: int.from_bytes(f1[0:2], "big"), 8: g(f4, 17)}
# 34F1 bytes 2..16 -> lists 9..13, all zero on this watch
for n in range(9, 14):
    lists[n] = 0

suffix = {"": 1, "_Second": 2, "_Third": 3, "_Fourth": 4, "_Fifth": 5, "_Sixth": 6, "_Seven": 7,
          "_8": 8, "_9": 9, "_10": 10, "_11": 11, "_12": 12, "_13": 13}

cls = meth = ""
consts = {}
hits = collections.defaultdict(set)  # (list, bit) -> callers
re_cls = re.compile(r"Class descriptor\s+: 'L([^;]+);'")
re_meth = re.compile(r"^\s+name\s+: '([^']+)'")
re_const = re.compile(r"\|[0-9a-f]+: const(?:/4|/16|/high16)? (v\d+), #int (-?\d+)")
re_call = re.compile(r"\|[0-9a-f]+: invoke-static(?:/range)? \{([^}]*)\}, Lcom/yc/pedometer/column/GetFunctionList;\.isSupportFunction(\w*):\(I\)Z")
with open(dump, encoding="utf-8", errors="replace") as fh:
    for line in fh:
        m = re_cls.search(line)
        if m:
            cls = m.group(1).split("/")[-1]; continue
        m = re_meth.match(line)
        if m:
            meth = m.group(1); consts = {}; continue
        m = re_const.search(line)
        if m:
            consts[m.group(1)] = int(m.group(2)); continue
        m = re_call.search(line)
        if m:
            reg = m.group(1).strip().split(",")[0].strip()
            if reg in consts and m.group(2) in suffix:
                hits[(suffix[m.group(2)], consts[reg] & 0xFFFFFFFF)].add(f"{cls}.{meth}")

for (lst, bit), callers in sorted(hits.items()):
    on = "YES" if lists.get(lst, 0) & bit == bit else " - "
    print(f"{on}  L{lst:<2} 0x{bit:06X}  " + "; ".join(sorted(callers))[:220])
