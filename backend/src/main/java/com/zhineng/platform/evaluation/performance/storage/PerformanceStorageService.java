package com.zhineng.platform.evaluation.performance.storage;
import java.io.*;import java.nio.file.*;import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;import org.springframework.stereotype.Service;
@Service public class PerformanceStorageService{
 private final Path root;
 public PerformanceStorageService(@Value("${app.storage.org-performance-path:backend/storage/org-performance}")String configured)throws IOException{Path path=Path.of(configured);if(!path.isAbsolute()){Path cwd=Path.of("").toAbsolutePath().normalize();Path project="backend".equalsIgnoreCase(String.valueOf(cwd.getFileName()))?cwd.getParent():cwd;path=project.resolve(path);}root=path.toAbsolutePath().normalize();Files.createDirectories(root);}
 public Stored store(InputStream input,String ext)throws IOException{String name=UUID.randomUUID()+"."+ext;Files.copy(input,resolve(name),StandardCopyOption.REPLACE_EXISTING);return new Stored(name,name);}
 public Path resolve(String relative){Path p=root.resolve(relative).normalize();if(!p.startsWith(root))throw new IllegalArgumentException("非法附件路径");return p;}
 public Path read(String relative)throws IOException{Path p=resolve(relative),rr=root.toRealPath(),rp=p.toRealPath();if(!rp.startsWith(rr))throw new IllegalArgumentException("非法附件路径");return rp;}
 public void deleteQuietly(String relative){try{Files.deleteIfExists(resolve(relative));}catch(Exception ignored){}}
 public record Stored(String storedName,String relativePath){}
}
