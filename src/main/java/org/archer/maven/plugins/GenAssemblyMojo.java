package org.archer.maven.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.codehaus.plexus.util.StringUtils;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.*;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.regex.Pattern;

/**
 * 读取patch文件夹或patch.txt文件
 * 生成assembly的配置文件
 * 用于动态增量打包
 *
 */
@Mojo( name = "genxml", defaultPhase = LifecyclePhase.COMPILE, requiresProject = true, threadSafe = true,
        requiresDependencyResolution = ResolutionScope.RUNTIME )
public class GenAssemblyMojo
        extends AbstractMojo
{

    Set<String> codePaths = new LinkedHashSet<>();  //源代码路径
    Set<String> classPaths = new LinkedHashSet<>(); //class路径
    Set<String> webPaths = new LinkedHashSet<>();   //web路径
    Set<String> warnPaths = new LinkedHashSet<>();  //非法的注释信息路径
    Set<String> onFilePaths = new LinkedHashSet<>();    //无效的文件路径
    Map<String,String> allFilePaths = new HashMap<>();   //记录已经加入的所有文件，key：文件路径，value 文件

    Map<String,String> repeatFileMap = new HashMap<>(); //记录重复的文件，key：文件路径，value：文件，用，分割

    public void execute()
            throws MojoExecutionException {
        initConfig();
        genXml("release.xml");
        genXml("src.xml");

    }

    /**
     * 初始化
     */
    private void initConfig() throws MojoExecutionException {
        getLog().info("读取配置文件patch.txt...");

        String pathname = "./patch.txt";
        String baseFilePath = "patch";
        if(patchPath != null)
            baseFilePath = patchPath;
        File f = new File(baseFilePath);

        if(f.isFile()){
            getLog().info("配置路径:"+baseFilePath+" 为文件");
            initPath(baseFilePath);
        } else if (f.isDirectory()){
            getLog().info("配置路径:"+baseFilePath+" 为文件夹");
            try{
                List<File> allFiles = FileUtils.getAllFiles(f);
                getLog().info("配置路径:"+baseFilePath+" 为文件夹，文件数:"+allFiles.size());
                for (File file:allFiles) {
                    initFile(file);
                }
            } catch (Exception e) {
                getLog().error(e);
                throw new MojoExecutionException(e.getMessage());
            }
            String[] fs = f.list();
            for (String file : fs) {
                if(file.startsWith("patch"))
                    initPath(baseFilePath+"/"+file);
            }
        }else{
            getLog().info("配置路径:"+baseFilePath+" 不存在，使用默认路径："+pathname);
            initPath(pathname);
        }
        checkFilePaths();

    }

    /**
     * 初始文件
     * @param pathname 文件路径
     * @throws MojoExecutionException
     */
    private void initPath(String pathname) throws MojoExecutionException {
//        getLog().info("正在加载文件:"+pathname);
        File config = new File(pathname);
        initFile(config);
    }

    /**
     * 初始化文件
     * @param config 配置文件
     * @throws MojoExecutionException
     */
    private void initFile(File config) throws MojoExecutionException {
        getLog().info("正在加载文件:"+config.getAbsolutePath());
        if (!config.exists()) {
            getLog().warn("配置文件"+config.getAbsolutePath()+"不存在！");
            return;
//            throw new IllegalArgumentException( "配置文件patch.txt不存在！程序将退出...");
        }
        FileReader fr = null;
        BufferedReader br = null;
        try {
            fr = new FileReader(config);
            br = new BufferedReader(fr);
            String str = null;
            String srcstr = null;
            while ((str = srcstr = br.readLine()) != null ) {
                getLog().debug("存入读取数据"+str);
                if(!"".equals(str)) {
                    codePaths.add(str);
                    if(str.endsWith(".java")&&str.startsWith("src/main/java/")){//maven标准的java
                        str = str.substring("src/main/java/".length(),str.length() - 5) + ".class";
                        classPaths.add(str);
                        str = str.substring(0,str.length() - 6) + "$*.class";
                        classPaths.add(str);
                    }else if (str.startsWith("src/main/resources/")){//maven标准的resources
                        str = str.substring("src/main/resources/".length());
                        classPaths.add(str);
                    }else if (str.endsWith(".java")&&str.startsWith("src")){//web标准的java
                        str = str.substring(str.indexOf("/"),str.length() - 5) + ".class";
                        classPaths.add(str);
                        str = str.substring(0,str.length() - 6) + "$*.class";
                        classPaths.add(str);
                    }else if (str.startsWith("src")){//其他的resources
                        str = str.substring(str.indexOf("/"));
                        classPaths.add(str);
                    }else if (str.startsWith("WebContent")){//其他的resources
                        str = str.substring(str.indexOf("/"));
                        webPaths.add(str);
                    }

                    if(!startWithChar(srcstr)&&!isAnnotation(srcstr)){
                        warnPaths.add(srcstr);
                    }else if(startWithChar(srcstr)&&!isFile(srcstr)){
                        onFilePaths.add(srcstr);
                    }else if(startWithChar(srcstr)){
                        checkFileRepeat(srcstr,config.getPath());
                    }
                }
            }
//            includes = codePaths.toArray(new String[codePaths.size()]);
        } catch (Exception e) {
            getLog().error("初始化配置文件出错！程序将退出...");
            throw new IllegalArgumentException( "初始化配置文件出错！程序将退出...");
        } finally {
            try {
                if (br != null) br.close();
                if (fr != null) fr.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    /**
     * 校验文件是否正确
     * @throws MojoExecutionException
     */
    private void checkFilePaths() throws MojoExecutionException {
        if (warnPaths.size() > 0 || onFilePaths.size() > 0){
            for (String w:warnPaths) {
                getLog().error("非法的注释信息:"+w);
            }
            for (String w:onFilePaths) {
                getLog().error("无效的文件路径:"+w);
            }
            throw new MojoExecutionException("异常的路径");
        }
        if (!repeatFileMap.isEmpty()){
            for (Map.Entry repeatEntry:repeatFileMap.entrySet()) {
                getLog().warn("【patch:"+repeatEntry.getValue());
                getLog().warn("存在重复的文件路径:"+repeatEntry.getKey()+"】");
            }
        }
    }

    /**
     * 生成xml
     * @param urlXml Xml路径
     */
    public void genXml(String urlXml){
        //获取XML文件路径
        URL url= this.getClass().getClassLoader().getResource(urlXml);
        try {
            //创建SAXReader对象
            SAXReader saxReader=new SAXReader();
            //生成文档对应实体
            Document document=saxReader.read(url);

            Element root = document.getRootElement();
            List<Element> list=document.getRootElement().elements("fileSets");

            for (Element element : list) {
                String id=element.getName();
                getLog().debug(id);
            }

            Element filesets = root.element("fileSets");
            if("release.xml".equals(urlXml)){
                //CLASS
                genClass(filesets);
                //WEB
                genWeb(filesets);
            }else{
                //Resources
                genResources(filesets);
            }
            OutputFormat format = OutputFormat.createPrettyPrint();
            XMLWriter xmlWriter = new XMLWriter(Files.newOutputStream(Paths.get("./" + urlXml)),format);
            xmlWriter.write(document);
            xmlWriter.close();
        } catch (DocumentException | IOException e) {
            e.printStackTrace();
            getLog().error(e.getMessage(),e);
        }
    }

    /**
     * 生成class部分的xml
     * @param filesets 文件组
     */
    private void genClass(Element filesets) {
        Element fileSet = filesets.addElement("fileSet");
        Element directory = fileSet.addElement("directory");
        if(null != classesDirectory ){
            getLog().debug("classesDirectory:"+classesDirectory.getAbsolutePath());
            directory.setText(classesDirectory.getAbsolutePath());
        }
        else
            directory.setText("./");
        Element outputDirectory = fileSet.addElement("outputDirectory");
        if(null != classOutputDirectory ){
            getLog().debug("classOutputDirectory:"+classOutputDirectory);
            outputDirectory.setText(classOutputDirectory);
        }
        else
            outputDirectory.setText("./");
        if(!classPaths.isEmpty()){
            Element includes = fileSet.addElement("includes");
            //内容
            for (String classPath : classPaths) {

                getLog().debug("classPath:"+classPath);
                Element include = includes.addElement("include");
                include.setText(classPath);
            }
        }else {
            //Excludes
            Element excludes = fileSet.addElement("excludes");
            Element exclude = excludes.addElement("exclude");
            exclude.setText("**/*");
        }

        Element fileMode = fileSet.addElement("fileMode");
        fileMode.setText("0755");
    }

    /**
     * 生成Resources部分的xml
     * @param filesets 文件组
     */
    private void genResources(Element filesets) {
        Element fileSet = filesets.addElement("fileSet");
        Element directory = fileSet.addElement("directory");
        directory.setText("./");
        Element outputDirectory = fileSet.addElement("outputDirectory");
        outputDirectory.setText("./");
        getLog().debug("codePaths size:"+codePaths.size());
        if(!codePaths.isEmpty()){
            Element includes = fileSet.addElement("includes");
            //内容
            for (String codePath : codePaths) {

                getLog().debug("codePath:"+codePath);
                Element include = includes.addElement("include");
                include.setText(codePath);
            }
        }else{
            //Excludes
            Element excludes = fileSet.addElement("excludes");
            Element exclude = excludes.addElement("exclude");
            exclude.setText("**/*");
        }

        Element fileMode = fileSet.addElement("fileMode");
        fileMode.setText("0755");
    }

    /**
     * 生成Web部分的xml
     * @param filesets 文件组
     */
    private void genWeb(Element filesets) {
        Element fileSet = filesets.addElement("fileSet");
        Element directory = fileSet.addElement("directory");
//        getLog().debug(" project.getName():"+ project.getName());
        directory.setText("./WebContent");
        Element outputDirectory = fileSet.addElement("outputDirectory");
        outputDirectory.setText("./");
        getLog().debug("webPaths size:"+webPaths.size());
        if(!webPaths.isEmpty()){
            Element includes = fileSet.addElement("includes");
            //内容
            for (String codePath : webPaths) {
                getLog().debug("webPaths:"+codePath);
                Element include = includes.addElement("include");
                include.setText(codePath);
            }
        }else{
            //Excludes
            Element excludes = fileSet.addElement("excludes");
            Element exclude = excludes.addElement("exclude");
            exclude.setText("**/*");
        }


        Element fileMode = fileSet.addElement("fileMode");
        fileMode.setText("0755");
    }

    /**
     * 判断开始字段是否英文字符
     * @param s 字段
     * @return
     */
    public boolean startWithChar(String s) {
        if (s != null && s.length() > 0) {
            String start = s.trim().substring(0, 1);
            Pattern pattern = Pattern.compile("^[A-Za-z]+$");
            return pattern.matcher(start).matches();
        } else {
            return false;
        }
    }

    /**
     * 判断是否注释
     * @param s 判断字段
     * @return
     */
    public boolean isAnnotation(String s) {
        if (s != null && s.length() > 0) {
            return s.startsWith("-") || s.startsWith("#");
        } else {
            return false;
        }
    }

    /**
     * 判断是否文件
     * @param s 判断字段
     * @return
     */
    public boolean isFile(String s){
        File file = new File(s);
        return file.exists();
    }

    /**
     * 检查重复文件，放入重复记录map
     * @param sPath 代码文件路径
     * @param patchPaths 版本文件路径
     * @return 未重复返回true
     */
    private boolean checkFileRepeat(String sPath,String patchPaths){
        String s = allFilePaths.get(sPath);
        if (StringUtils.isEmpty(s))
            allFilePaths.put(sPath,patchPaths);
        else {
            String sRepeatPatchPath = repeatFileMap.get(sPath);
            if(StringUtils.isEmpty(sRepeatPatchPath))
                repeatFileMap.put(sPath,s+","+patchPaths);
            else
                repeatFileMap.put(sPath,sRepeatPatchPath+","+patchPaths);
        }
        return StringUtils.isEmpty(s);
    }

    /**
     * Directory containing the classes and resource files that should be packaged into the JAR.
     */
    @Parameter( defaultValue = "${project.build.outputDirectory}", required = true )
    private File classesDirectory;

    @Parameter( defaultValue = "${project.build.classOutputDirectory}", required = true )
    private String classOutputDirectory;


    @Parameter( defaultValue = "${project.build.patchPath}", required = true )
    private String patchPath;

    /**
     * Classifier to add to the artifact generated. If given, the artifact will be attached
     * as a supplemental artifact.
     * If not given this will create the main artifact which is the default behavior.
     * If you try to do that a second time without using a classifier the build will fail.
     */
    @Parameter
    private String classifier;

    /**
     * The {@link {MavenProject}.
     */
    /**
     * {@inheritDoc}
     */
    protected String getClassifier()
    {
        return classifier;
    }


    /**
     * {@inheritDoc}
     */
    protected File getClassesDirectory()
    {
        return classesDirectory;
    }

    protected String classOutputDirectory()
    {
        return classOutputDirectory;
    }
}
