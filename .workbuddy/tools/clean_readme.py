f = '/Users/yinan/workspace/yinan/boheng/boheng-frame/web/README.md'

with open(f, encoding='utf-8') as fp:
    lines = fp.readlines()

kept = []
removed = 0
for line in lines:
    low = line.lower()
    if 'iocoder.cn' in low or 'yudao' in low or 'ruoyi' in low:
        removed += 1
        continue
    kept.append(line)

with open(f, 'w', encoding='utf-8') as fp:
    fp.writelines(kept)

print(f'removed {removed} lines')
