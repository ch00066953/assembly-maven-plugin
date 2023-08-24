package org.archer.maven.plugins;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.plugins.annotations.ResolutionScope;
import org.dom4j.*;
import org.dom4j.io.OutputFormat;
import org.dom4j.io.SAXReader;
import org.dom4j.io.XMLWriter;

import java.io.*;
import java.net.URL;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
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

    Set<String> codePaths = new LinkedHashSet<String>();
    Set<String> classPaths = new LinkedHashSet<String>();
    Set<String> webPaths = new LinkedHashSet<String>();
    Set<String> warnPaths = new LinkedHashSet<String>();
    Set<String> onFilePaths = new LinkedHashSet<String>();

    public void execute()
            throws MojoExecutionException {
        initConfig();
        genxml("release.xml");
        genxml("src.xml");

    }

    public void initConfig() throws MojoExecutionException {
        getLog().info("读取配置文件patch.txt...");

        String pathname = "./patch.txt";
        String baseFilePath = "patch";
        if(patchPath != null)
            baseFilePath = patchPath;
        File f = new File(baseFilePath);
        String[] fs = f.list();
        if(fs != null){
            for (String file : fs) {
                if(file.startsWith("patch"))
                    initPath(baseFilePath+"/"+file);
            }
        } else{
            getLog().info("配置路径:"+baseFilePath+" 不存在，使用默认路径："+pathname);
            initPath(pathname);
        }

    }

    private void initPath(String pathname) throws MojoExecutionException {
        getLog().info("正在加载文件:"+pathname);
        File config = new File(pathname);

        if (!config.exists()) {
            getLog().warn("配置文件"+pathname+"不存在！");
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

        if (warnPaths.size() > 0 || onFilePaths.size() > 0){
            for (String w:warnPaths) {
                getLog().error("非法的注释信息:"+w);
            }
            for (String w:onFilePaths) {
                getLog().error("无效的文件路径:"+w);
            }
//            warnPaths.forEach((s) -> getLog().error("非法的注释信息:"+s));
//            onFilePaths.forEach((s) -> getLog().error("无效的文件路径:"+s));
            throw new MojoExecutionException("异常的路径");
        }
    }

    public void genxml(String urlxml){
        //获取XML文件路径
        URL url= this.getClass().getClassLoader().getResource(urlxml);
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
            if("release.xml".equals(urlxml)){
                //CLASS
                genClass(filesets);
                //WEB
                genWeb(filesets);
            }else{
                //Resources
                genResources(filesets);
            }
            OutputFormat format = OutputFormat.createPrettyPrint();
            XMLWriter xmlWriter = new XMLWriter(new FileOutputStream("./"+urlxml),format);
            xmlWriter.write(document);
            xmlWriter.close();
        } catch (DocumentException e) {
            e.printStackTrace();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        } catch (UnsupportedEncodingException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 生成class部分的xml
     * @param filesets
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
        }else if(classPaths.isEmpty()){
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
     * @param filesets
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

    public boolean startWithChar(String s) {
        if (s != null && s.length() > 0) {
            String start = s.trim().substring(0, 1);
            Pattern pattern = Pattern.compile("^[A-Za-z]+$");
            return pattern.matcher(start).matches();
        } else {
            return false;
        }
    }

    public boolean isAnnotation(String s) {
        if (s != null && s.length() > 0) {
            boolean b = s.startsWith("-") || s.startsWith("#");
            return b;
        } else {
            return false;
        }
    }

    public boolean isFile(String s){
        File file = new File(s);
        return file.exists();
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
//    @Parameter( defaultValue = "${project}", readonly = true, required = true )
//    protected MavenProject project;
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
