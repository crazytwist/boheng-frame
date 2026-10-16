import re

files = [
    '/Users/yinan/workspace/yinan/boheng/boheng-frame/web/src/components/DiyEditor/components/mobile/TabBar/config.ts',
    '/Users/yinan/workspace/yinan/boheng/boheng-frame/web/src/components/DiyEditor/components/mobile/NoticeBar/config.ts',
    '/Users/yinan/workspace/yinan/boheng/boheng-frame/web/src/components/DiyEditor/components/mobile/Carousel/config.ts',
    '/Users/yinan/workspace/yinan/boheng/boheng-frame/web/src/views/mp/draft/mock.js',
    '/Users/yinan/workspace/yinan/boheng/boheng-frame/web/src/views/infra/skywalking/index.vue',
]

pattern = re.compile(
    r"""['"](https?://(?:mall\.yudao\.iocoder\.cn|static\.iocoder\.cn|test\.yudao\.iocoder\.cn|skywalking\.shop\.iocoder\.cn|www\.iocoder\.cn)[^'"]*)['"]"""
)

total = 0
for f in files:
    with open(f, encoding='utf-8') as fp:
        content = fp.read()
    new, n = pattern.subn("''", content)
    if n:
        with open(f, 'w', encoding='utf-8') as fp:
            fp.write(new)
        total += n
        print(f'{n} -> {f}')
print(f'TOTAL {total}')
