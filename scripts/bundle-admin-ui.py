#!/usr/bin/env python3
"""Replace bundled Next export and regenerate CSP hashes. Pass frontend out/ directory."""
import base64, hashlib, pathlib, re, shutil, sys
src = pathlib.Path(sys.argv[1]).resolve()
root = pathlib.Path(__file__).resolve().parent.parent
out = root / 'src/main/resources/static/admin-ui'
if not (src / 'login/index.html').is_file():
    raise SystemExit('Expected Next export with basePath=/admin-ui and trailingSlash=true')
shutil.rmtree(out, ignore_errors=True)
shutil.copytree(src, out)
(out / 'staticwebapp.config.json').unlink(missing_ok=True)
hashes = set()
for p in out.rglob('*.html'):
    for attrs, body in re.findall(r'<script([^>]*)>(.*?)</script>', p.read_text(), re.S):
        if 'src=' not in attrs and body:
            hashes.add("'sha256-" + base64.b64encode(hashlib.sha256(body.encode()).digest()).decode() + "'")
policy = "default-src 'self'; script-src 'self' " + ' '.join(sorted(hashes)) + " https://apis.google.com; style-src 'self' 'unsafe-inline'; img-src 'self' data: https:; connect-src 'self' https://*.googleapis.com https://loyal-optics-388515.firebaseapp.com; frame-src https://loyal-optics-388515.firebaseapp.com https://accounts.google.com; object-src 'none'; base-uri 'self'; form-action 'self'; frame-ancestors 'none'"
(root / 'src/main/resources/admin-ui-csp.txt').write_text(policy + '\n')
print('Bundled export; inline script hashes:', len(hashes))
