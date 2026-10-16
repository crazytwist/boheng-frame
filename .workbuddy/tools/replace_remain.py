import os

# 1) web/src 目录「芋艿/芋道」注释 →「伯珩」
src_base = '/Users/yinan/workspace/yinan/boheng/boheng-frame/web/src'
web_root = '/Users/yinan/workspace/yinan/boheng/boheng-frame/web'
exts = {'.vue', '.ts', '.tsx', '.js', '.mjs', '.md', '.json', '.html'}

targets = []
for root, dirs, files in os.walk(src_base):
    for f in files:
        if any(f.endswith(e) for e in exts):
            targets.append(os.path.join(root, f))
for f in os.listdir(web_root):
    p = os.path.join(web_root, f)
    if os.path.isfile(p) and any(f.endswith(e) for e in exts):
        targets.append(p)

c1 = f1 = 0
for p in targets:
    try:
        with open(p, encoding='utf-8') as fp:
            s = fp.read()
    except Exception:
        continue
    n = s.count('芋艿') + s.count('芋道')
    if n:
        s = s.replace('芋道', '伯珩').replace('芋艿', '伯珩')
        with open(p, 'w', encoding='utf-8') as fp:
            fp.write(s)
        c1 += n
        f1 += 1
print(f'web: replaced {c1} in {f1} files')

# 2) dump.sql 用 bytes 级别替换
dump = '/Users/yinan/workspace/yinan/boheng/boheng-frame/deploy/boheng/boheng-frame-dump.sql'
try:
    with open(dump, 'rb') as fp:
        data = fp.read()
    n2 = data.count('芋道'.encode('utf-8')) + data.count('芋艿'.encode('utf-8'))
    data = data.replace('芋道'.encode('utf-8'), '伯珩'.encode('utf-8'))
    data = data.replace('芋艿'.encode('utf-8'), '伯珩'.encode('utf-8'))
    with open(dump, 'wb') as fp:
        fp.write(data)
    print(f'dump.sql: replaced {n2} occurrences')
except Exception as e:
    print(f'dump.sql error: {e}')
