package cn.boheng.frame;

import cn.hutool.core.io.FileTypeUtil;
import cn.hutool.core.io.FileUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.core.collection.CollUtil;
import lombok.extern.slf4j.Slf4j;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.util.Collection;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.stream.Collectors;

import static java.io.File.separator;

/**
 * 项目修改器（伯珩基座版），一键替换 Maven 的 groupId、artifactId、package、系统标题等。
 * <p>
 * 本文件是「伯珩基础平台」的派生起点：把一份干净的芋道（ruoyi-vue-pro）项目改名为自己的项目。
 * 以后每次派生新项目，只改 main() 里的四个 New 变量即可。
 * <p>
 * 使用方式（在芋道源码根目录执行）：
 *   mvn test -Dtest=ProjectReactor  或  直接运行 main 方法
 * 会在同目录生成一个「原名-new」的新项目目录，包名 / artifactId / 标题全部替换完成。
 *
 * @author 伯珩 (Boheng)
 */
@Slf4j
public class ProjectReactor {

    // ========== 芋道原值（源，用于匹配替换） ==========
    private static final String GROUP_ID = "cn.boheng";
    private static final String ARTIFACT_ID = "boheng";
    private static final String PACKAGE_NAME = "cn.boheng.frame";
    private static final String TITLE = "伯珩基础平台";

    /**
     * 白名单文件类型，不重写内容、直接拷贝，避免破坏二进制文件
     */
    private static final Set<String> WHITE_FILE_TYPES = CollUtil.newHashSet(
            "gif", "jpg", "svg", "png",        // 图片
            "eot", "woff2", "ttf", "woff",     // 字体
            "xdb");                            // IP 库

    public static void main(String[] args) {
        long start = System.currentTimeMillis();
        String projectBaseDir = getProjectBaseDir();
        log.info("[main][原项目路径改地址 ({})]", projectBaseDir);

        // ========== 配置：每次派生只改下面 4 个变量 ==========
        String groupIdNew = "cn.boheng";            // groupId（基座固定 cn.boheng）
        String artifactIdNew = "boheng";            // artifactId（基座：boheng；必须单词，首字母大写后作类名前缀）
        String packageNameNew = "cn.boheng.frame";  // 包名根（cn.boheng.frame.*）
        String titleNew = "伯珩基础平台";            // 系统标题
        // 输出目录：必须不含 GROUP_ID / PACKAGE_NAME / ARTIFACT_ID 关键字，否则路径被替换导致输出错位。
        // 芋道源码目录通常叫 boheng-boot-xxx，含「boheng」，直接用 projectBaseDir + "-new" 会触发冲突，
        // 故这里写死成一个干净的同级目录。
        String projectBaseDirNew = "/Volumes/External_1TB/Projects/boheng-frame"; // 新项目输出目录

        log.info("[main][检测新项目目录 ({}) 是否存在]", projectBaseDirNew);
        if (FileUtil.exist(projectBaseDirNew)) {
            log.error("[main][新项目目录 ({}) 已存在，请更改新的目录！程序退出]", projectBaseDirNew);
            return;
        }
        // 新目录路径若含 PACKAGE_NAME / ARTIFACT_ID 关键字，会被替换导致输出错位
        if (StrUtil.containsAny(projectBaseDirNew, PACKAGE_NAME, ARTIFACT_ID, StrUtil.upperFirst(ARTIFACT_ID))) {
            log.error("[main][新项目目录 ({}) 存在冲突名称「{}」或「{}」，请更改新的目录！程序退出]",
                    projectBaseDirNew, PACKAGE_NAME, ARTIFACT_ID);
            return;
        }
        log.info("[main][新项目目录检测完成，输出路径 ({})]", projectBaseDirNew);

        log.info("[main][开始收集需要重写的文件，预计 10-20 秒]");
        Collection<File> files = listFiles(projectBaseDir);
        log.info("[main][需要重写的文件数量：{}，预计 15-30 秒]", files.size());

        files.forEach(file -> {
            String fileType = getFileType(file);
            if (WHITE_FILE_TYPES.contains(fileType)) {
                copyFile(file, projectBaseDir, projectBaseDirNew, packageNameNew, artifactIdNew);
                return;
            }
            String content = replaceFileContent(file, groupIdNew, artifactIdNew, packageNameNew, titleNew);
            writeFile(file, content, projectBaseDir, projectBaseDirNew, packageNameNew, artifactIdNew);
        });
        log.info("[main][重写完成] 共耗时 {} 秒", (System.currentTimeMillis() - start) / 1000);
    }

    private static String getProjectBaseDir() {
        String baseDir = System.getProperty("user.dir");
        if (StrUtil.isEmpty(baseDir)) {
            throw new NullPointerException("项目基础路径不存在");
        }
        return baseDir;
    }

    private static Collection<File> listFiles(String projectBaseDir) {
        Collection<File> files = FileUtil.loopFiles(projectBaseDir);
        return files.stream()
                .filter(file -> !file.getPath().contains(separator + "target" + separator)
                        && !file.getPath().contains(separator + "node_modules" + separator)
                        && !file.getPath().contains(separator + ".idea" + separator)
                        && !file.getPath().contains(separator + ".git" + separator)
                        && !file.getPath().contains(separator + "dist" + separator)
                        && !file.getPath().contains(".iml")
                        && !file.getPath().contains(".html.gz"))
                .collect(Collectors.toList());
    }

    private static String replaceFileContent(File file, String groupIdNew,
                                             String artifactIdNew, String packageNameNew,
                                             String titleNew) {
        String content = FileUtil.readString(file, StandardCharsets.UTF_8);
        String fileType = getFileType(file);
        if (WHITE_FILE_TYPES.contains(fileType)) {
            return content;
        }
        return content.replaceAll(GROUP_ID, groupIdNew)
                .replaceAll(PACKAGE_NAME, packageNameNew)
                .replaceAll(ARTIFACT_ID, artifactIdNew) // 必须最后替换，ARTIFACT_ID 太短！
                .replaceAll(StrUtil.upperFirst(ARTIFACT_ID), StrUtil.upperFirst(artifactIdNew))
                .replaceAll(TITLE, titleNew);
    }

    private static void writeFile(File file, String fileContent, String projectBaseDir,
                                  String projectBaseDirNew, String packageNameNew, String artifactIdNew) {
        String newPath = buildNewFilePath(file, projectBaseDir, projectBaseDirNew, packageNameNew, artifactIdNew);
        FileUtil.writeUtf8String(fileContent, newPath);
    }

    private static void copyFile(File file, String projectBaseDir,
                                 String projectBaseDirNew, String packageNameNew, String artifactIdNew) {
        String newPath = buildNewFilePath(file, projectBaseDir, projectBaseDirNew, packageNameNew, artifactIdNew);
        FileUtil.copyFile(file, new File(newPath));
    }

    private static String buildNewFilePath(File file, String projectBaseDir,
                                           String projectBaseDirNew, String packageNameNew, String artifactIdNew) {
        return file.getPath().replace(projectBaseDir, projectBaseDirNew)
                .replace(PACKAGE_NAME.replaceAll("\\.", Matcher.quoteReplacement(separator)),
                        packageNameNew.replaceAll("\\.", Matcher.quoteReplacement(separator)))
                .replace(ARTIFACT_ID, artifactIdNew)
                .replaceAll(StrUtil.upperFirst(ARTIFACT_ID), StrUtil.upperFirst(artifactIdNew));
    }

    private static String getFileType(File file) {
        return file.length() > 0 ? FileTypeUtil.getType(file) : "";
    }

}
