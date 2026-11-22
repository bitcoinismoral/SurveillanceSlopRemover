"""Checks every stub member against AOSP's public API list (frameworks/base/core/api/current.txt)."""
import re, subprocess, sys, glob, os

ROOT = os.path.expanduser("~/XLinkCleaner-build")
API_FILE = os.environ.get("API_FILE", os.path.join(ROOT, "current.txt"))
api = open(API_FILE, encoding="utf-8").read()

def strip_ann(s):
    # remove annotations like @NonNull, @FlaggedApi("x"), @RequiresPermission(anyOf={...})
    prev = None
    while prev != s:
        prev = s
        s = re.sub(r'@[\w.]+(\([^()]*\))?\s*', '', s)
    return s

def norm_type(t):
    t = strip_ann(t).strip()
    t = re.sub(r'<.*>', '', t)          # generics
    t = t.replace('...', '[]')
    t = re.sub(r'\bjava\.lang\.(?=[A-Z])', '', t)   # API file writes String, javap writes java.lang.String
    return t.strip()

classes = {}   # fqcn -> {"extends": fq or None, "members": set()}
pkg = None; cur = None
for raw in api.splitlines():
    line = strip_ann(raw.split(' // ')[0]).strip()
    m = re.match(r'package ([\w.]+) \{', line)
    if m: pkg = m.group(1); continue
    m = re.match(r'(?:public|protected)\s+(?:[\w]+\s+)*(?:class|interface|enum)\s+([\w.]+)(?:<[^>]*>)?(?:\s+extends\s+([\w.]+))?', line)
    if m and line.endswith('{'):
        cur = pkg + '.' + m.group(1)
        ext = m.group(2)
        if ext and '.' not in ext: ext = pkg + '.' + ext
        classes[cur] = {"extends": ext, "members": set()}
        continue
    m = re.match(r'(method|ctor|field|enum_constant)\s+(.*);$', line)
    if m and cur:
        kind, rest = m.groups()
        if kind == 'enum_constant': kind = 'field'
        if kind == 'field':
            decl, _, val = rest.partition('=')
            words = norm_type(decl).split()
            if len(words) >= 2:
                classes[cur]["members"].add(('field', words[-1], '', words[-2], val.strip()))
        else:
            mm = re.match(r'(.*?)\s*([\w.]+)\((.*)\)(?:\s+throws.*)?$', rest)
            if not mm: continue
            pre, name, params = mm.groups()
            ptypes = []
            depth = 0; buf = ''
            for ch in params:
                if ch == '<': depth += 1
                if ch == '>': depth -= 1
                if ch == ',' and depth == 0: ptypes.append(buf); buf = ''
                else: buf += ch
            if buf.strip(): ptypes.append(buf)
            ptypes = [norm_type(re.sub(r'\s+\w+$', '', p.strip()) if re.search(r'\s\w+$', strip_ann(p).strip()) and len(strip_ann(p).split()) > 1 else p) for p in ptypes]
            words = strip_ann(pre).split()
            ret = '' if kind == 'ctor' else norm_type(words[-1]) if words else ''
            nm = name.split('.')[-1]
            classes[cur]["members"].add((kind, nm, ','.join(ptypes), ret, rest))

def find(cls, kind, name, params, ret):
    seen = set()
    c = cls
    while c and c not in seen:
        seen.add(c)
        info = classes.get(c)
        if not info: break
        for (k, n, p, r, raw) in info["members"]:
            if k == kind and n == name and p == params and (kind == 'ctor' or r == ret):
                return c, raw
        if kind == 'ctor': break
        c = info["extends"] or ('java.lang.Object' if c != 'java.lang.Object' else None)
    return None, None

stub_root = os.environ.get("STUB_DIR", os.path.join(ROOT, "oss-out", "stubs"))
classfiles = sorted(glob.glob(stub_root + "/**/*.class", recursive=True))
names = [os.path.relpath(f, stub_root)[:-6].replace('/', '.') for f in classfiles]
jbin = os.environ.get("JAVAP") or glob.glob(ROOT + "/tools/jdk/zulu*/zulu-17.jdk/Contents/Home/bin/javap")[0]
out = subprocess.run([jbin, "-public", "-constants", "-cp", stub_root] + names, capture_output=True, text=True).stdout

EXPLICIT_NOARG_CTORS = {"android.app.Activity", "android.content.Intent",
                        "android.accessibilityservice.AccessibilityServiceInfo"}
ok = bad = 0
cur = None
for line in out.splitlines():
    line = line.strip()
    m = re.match(r'public .*?(?:class|interface) ([\w.$]+)', line)
    if m: cur = m.group(1).replace('$', '.'); continue
    if not cur or not line.endswith(';') or 'static {}' in line: continue
    body = line[:-1]
    mm = re.match(r'(.*?)([\w.$]+)\((.*)\)$', body)
    if mm:
        pre, name, params = mm.groups()
        if name in ('values', 'valueOf') and 'static' in pre: continue  # compiler-generated enum methods
        params = ','.join(norm_type(p.replace('$', '.')) for p in params.split(',') if p.strip())
        short = name.split('.')[-1].replace('$', '.')
        if short == cur.split('.')[-1] or name.replace('$', '.') == cur:
            # javac adds an implicit no-arg ctor to stubs where none was written; the app never calls those
            if params == '' and cur not in EXPLICIT_NOARG_CTORS: continue
            where, raw = find(cur, 'ctor', cur.split('.')[-1], params, '')
            label = f"{cur}({params})"
        else:
            ret = norm_type(pre.split()[-1].replace('$', '.'))
            where, raw = find(cur, 'method', name, params, ret)
            label = f"{cur}.{name}({params}) -> {ret}"
    else:
        decl, _, sval = body.partition(' = ')
        fm = re.match(r'(.*)\s(\w+)$', decl)
        if not fm: continue
        ret = norm_type(fm.group(1).split()[-1].replace('$', '.'))
        where, raw = find(cur, 'field', fm.group(2), '', ret)
        label = f"{cur}.{fm.group(2)} : {ret}"
        if where and sval:
            def canon(v):
                v = v.strip().rstrip('L')
                try: return str(int(v, 0))
                except ValueError: return v.strip('"')
            if canon(sval) != canon(raw):
                print(f"VALUE MISMATCH: {label} stub={sval} aosp={raw}"); bad += 1; continue
            label += f" = {sval} (value matches)"
    if where: ok += 1
    else: bad += 1; print("NOT IN AOSP API:", label)

print(f"{ok} stub members match AOSP's public API, {bad} mismatches")
sys.exit(1 if bad else 0)
