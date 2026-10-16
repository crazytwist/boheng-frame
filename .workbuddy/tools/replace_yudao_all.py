import os

base = '/Users/yinan/workspace/yinan/boheng/boheng-frame'
exclude_names = {'node_modules', '.git', 'target', 'dist', '.workbuddy', '.idea', '.vscode'}
exclude_paths = {os.path.join(base, 'web')}

extensions = {
    '.java', '.xml', '.yaml', '.yml', '.properties', '.sql', '.http',
    '.md', '.py', '.txt', '.json', '.vue', '.ts', '.tsx', '.js', '.vm', '.ftl'
}

# 顺序：先长后短，避免「芋道源码」被「芋道」先替换成「伯珩源码」
repls = [('芋道源码', '伯珩'), ('芋道', '伯珩'), ('芋艿', '伯珩')]

count = 0
files_changed = 0
for root, dirs, files in os.walk(base):
    dirs[:] = [d for d in dirs
               if d not in exclude_names and os.path.join(root, d) not in exclude_paths]
    for f in files:
        if not any(f.endswith(ext) for ext in extensions):
            continue
        path = os.path.join(root, f)
        try:
            with open(path, encoding='utf-8') as fp:
                content = fp.read()
        except Exception:
            continue
        new = content
        n = 0
        for a, b in repls:
            n += new.count(a)
            new = new.replace(a, b)
        if n:
            with open(path, 'w', encoding='utf-8') as fp:
                fp.write(new)
            count += n
            files_changed += 1

print(f'replaced {count} occurrences in {files_changed} files')
