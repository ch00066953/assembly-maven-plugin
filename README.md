# assembly-maven-plugin

## 增量打包工具

1. 配合maven-assembly-plugin
生成打包使用的xml
```
<plugin>
     <groupId>org.apache.maven.plugins</groupId>
     <artifactId>maven-assembly-plugin</artifactId>
     <configuration>
         <finalName>${project.build.name}</finalName>
         <!-- <appendAssemblyId>false</appendAssemblyId> -->  <!-- 如果只想有finalName,不需要连接release.xml中的id -->
         <tarLongFileMode>posix</tarLongFileMode>  <!-- 解决tar大小的限制问题 -->
         <descriptors>
             <descriptor>release.xml</descriptor>
             <descriptor>src.xml</descriptor>
         </descriptors>
         <outputDirectory>output</outputDirectory>
         <attach>false</attach>
     </configuration>
     <executions>
         <execution>
             <phase>package</phase>
             <goals>
                 <goal>single</goal>
             </goals>
         </execution>
     </executions>
 </plugin>
```
2. 执行compile生成对应的文件
```
<plugin>
    <groupId>org.archer.maven.plugins</groupId>
    <artifactId>assembly-maven-plugin</artifactId>
    <version>1.0.7</version>
    <configuration>
        <classOutputDirectory>./WEB-INF/classes</classOutputDirectory>
        <patchPath>patch</patchPath>
    </configuration>  
    <executions>
         <execution>
             <phase>compile</phase>
             <goals>
                 <goal>genxml</goal>
             </goals>
         </execution>
     </executions>
</plugin>
```
3. 编写对应的patch文件
patch文件在对应的patchPath标签下编写
```
   <patchPath>patch</patchPath>
```
patch文件内容：
```
--这是注释
src/com/asda.java
```