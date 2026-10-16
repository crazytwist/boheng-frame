import re
import glob

base = '/Users/yinan/workspace/yinan/boheng/boheng-frame/web/src'
# 匹配 <doc-alert ... /> 单行与多行（自闭合，属性仅 title/url 字符串，不会嵌套 />）
pattern = re.compile(r'<doc-alert\b.*?/>', re.DOTALL)

files = sorted(glob.glob(base + '/**/*.vue', recursive=True))
total = 0
changed = 0
for f in files:
    with open(f, encoding='utf-8') as fp:
        content = fp.read()
    new_content, n = pattern.subn('', content)
    if n:
        with open(f, 'w', encoding='utf-8') as fp:
            fp.write(new_content)
        total += n
        changed += 1

print(f'removed {total} doc-alert tags from {changed} files')
