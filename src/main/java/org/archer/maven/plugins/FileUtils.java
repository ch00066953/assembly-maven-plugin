package org.archer.maven.plugins;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/**
 * @className: 文件工具类
 * @author: lgh
 * @date: 2023/7/13
 **/

public class FileUtils {

    /**
     * 获取指定文件夹下的所有文件
     *
     * @param dirPath 文件夹路径
     * @return 所有文件
     */
    public static List<File> getAllFiles(File dirPath) throws Exception {
        if (!dirPath.isFile() && dirPath.exists()) {
            List<File> fileList = new ArrayList<>();
            return recursion(dirPath, fileList);
        } else {
            throw new Exception("请输入正确的文件夹路径");
        }
    }

    private static List<File> recursion(File file, List<File> fileList) {
        File[] files = file.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) {
                    recursion(f, fileList);
                } else {
                    fileList.add(f);
                }

            }
        }

        return fileList;
    }


}

