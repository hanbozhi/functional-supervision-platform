package com.zhineng.platform.evaluation.selfevaluation.storage;
import java.io.*;import java.nio.file.*;import java.util.*;import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Component;
@Component public class SelfEvaluationStorage{
 private final Path root;public SelfEvaluationStorage(@Value("${platform.storage.self-evaluation-path:backend/storage/self-evaluation}")String c){Path p=Path.of(c);root=(p.isAbsolute()?p:Path.of(System.getProperty("user.dir")).resolve(p)).normalize().toAbsolutePath();try{Files.createDirectories(root);}catch(IOException e){throw new IllegalStateException(e);}}
 public Stored store(InputStream in,String ext)throws IOException{String n=UUID.randomUUID().toString().replace("-","")+(ext.isBlank()?"":"."+ext);Path p=root.resolve(n).normalize();if(!p.startsWith(root))throw new IOException("路径越界");Files.copy(in,p);return new Stored(n,n);}
 public Path resolve(String r)throws IOException{Path p=root.resolve(r).normalize();if(!p.startsWith(root)||!Files.isRegularFile(p))throw new IOException("材料路径无效");return p;}public void cleanup(String r){try{Path p=root.resolve(r).normalize();if(p.startsWith(root))Files.deleteIfExists(p);}catch(Exception ignored){}}
 public record Stored(String storedName,String relativePath){}
}
