import os

base = '/Users/yinan/workspace/yinan/boheng/boheng-frame'
# 目录名排除（target/dist 为构建产物，node_modules/.git/.workbuddy 为元数据）
exclude_names = {'node_modules', '.git', 'target', 'dist', '.workbuddy', '.idea', '.vscode'}
# 完整路径排除（前端 web 目录整体跳过）
exclude_paths = {os.path.join(base, 'web')}

count = 0
files_changed = 0
for root, dirs, files in os.walk(base):
    dirs[:] = [d for d in dirs
               if d not in exclude_names and os.path.join(root, d) not in exclude_paths]
    for f in files:
        if not f.endswith('.java'):
            continue
        path = os.path.join(root, f)
        try:
            with open(path, encoding='utf-8') as fp:
                content = fp.read()
        except Exception:
            continue
        n = content.count('@author 芋道源码')
        if n:
            new = content.replace('@author 芋道源码', '@author 伯珩')
            with open(path, 'w', encoding='utf-8') as fp:
                fp.write(new)
            count += n
            files_changed += 1

print(f'replaced {count} "@author 芋道源码" in {files_changed} files')
