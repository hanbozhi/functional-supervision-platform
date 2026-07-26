package com.zhineng.platform.evaluation.publicservice.storage;
import java.io.*;import java.nio.file.*;import java.util.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;
@Component public class PublicEvaluationStorage {
 private final Path root;
 public PublicEvaluationStorage(@Value("${platform.storage.public-evaluation-path:backend/storage/public-evaluation}")String configured){
  Path p=Path.of(configured);root=(p.isAbsolute()?p:Path.of(System.getProperty("user.dir")).resolve(p)).normalize().toAbsolutePath();
  try{Files.createDirectories(root);}catch(IOException e){throw new IllegalStateException("无法创建群众评价存储目录",e);}
 }
 public Stored store(InputStream input,String ext)throws IOException{
  String name=UUID.randomUUID().toString().replace("-","")+(ext.isBlank()?"":"."+ext);Path target=root.resolve(name).normalize();
  if(!target.startsWith(root))throw new IOException("非法存储路径");Files.copy(input,target,StandardCopyOption.REPLACE_EXISTING);return new Stored(name,name);
 }
 public Path resolve(String relative)throws IOException{Path p=root.resolve(relative).normalize();if(!p.startsWith(root)||!Files.isRegularFile(p))throw new IOException("附件路径无效");return p;}
 public void cleanup(String relative){try{Path p=root.resolve(relative).normalize();if(p.startsWith(root))Files.deleteIfExists(p);}catch(Exception ignored){}}
 public record Stored(String storedName,String relativePath){}
}
