package com.zhineng.platform.evaluation.performance.controller;
import com.zhineng.platform.evaluation.performance.dto.PerformanceDtos;import com.zhineng.platform.evaluation.performance.service.PerformanceService;import java.nio.charset.StandardCharsets;import java.util.*;import org.springframework.core.io.*;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/org-performance") public class PerformanceController{
 private final PerformanceService service;public PerformanceController(PerformanceService s){service=s;}
 @GetMapping("/mappings")public List<Map<String,Object>>mappings(){return service.mappings();}
 @PostMapping("/mappings")@ResponseStatus(HttpStatus.CREATED)public Map<String,Object>createMapping(@RequestBody PerformanceDtos.MappingRequest q){return service.saveMapping(null,q);}
 @PutMapping("/mappings/{id}")public Map<String,Object>updateMapping(@PathVariable long id,@RequestBody PerformanceDtos.MappingRequest q){return service.saveMapping(id,q);}
 @PutMapping("/mappings/{id}/status")public Map<String,Object>mappingStatus(@PathVariable long id,@RequestBody PerformanceDtos.StatusRequest q){return service.mappingStatus(id,q);}
 @GetMapping("/import-template")public ResponseEntity<byte[]>template(){HttpHeaders h=new HttpHeaders();h.setContentDisposition(ContentDisposition.attachment().filename("组织部绩效导入模板.xlsx",StandardCharsets.UTF_8).build());h.setContentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));return new ResponseEntity<>(service.template(),h,HttpStatus.OK);}
 @PostMapping(value="/imports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)@ResponseStatus(HttpStatus.CREATED)public Map<String,Object>upload(@RequestPart("file")MultipartFile f){return service.importFile(f);}
 @GetMapping("/imports")public List<Map<String,Object>>batches(){return service.batches();}
 @GetMapping("/imports/{id}")public Map<String,Object>batch(@PathVariable long id){return service.batch(id);}
 @GetMapping("/records")public List<Map<String,Object>>records(@RequestParam(required=false)Integer year){return service.records(year);}
 @GetMapping("/corrections")public List<Map<String,Object>>corrections(){return service.corrections();}
 @GetMapping("/corrections/{id}")public Map<String,Object>correction(@PathVariable long id){return service.correction(id);}
 @PostMapping("/corrections")@ResponseStatus(HttpStatus.CREATED)public Map<String,Object>createCorrection(@RequestBody PerformanceDtos.CorrectionRequest q){return service.saveCorrection(null,q);}
 @PutMapping("/corrections/{id}")public Map<String,Object>updateCorrection(@PathVariable long id,@RequestBody PerformanceDtos.CorrectionRequest q){return service.saveCorrection(id,q);}
 @PostMapping("/corrections/{id}/submit")public Map<String,Object>submit(@PathVariable long id,@RequestBody PerformanceDtos.ReviewRequest q){return service.submit(id,q);}
 @PostMapping("/corrections/{id}/review")public Map<String,Object>review(@PathVariable long id,@RequestBody PerformanceDtos.ReviewRequest q){return service.review(id,q);}
 @PostMapping(value="/corrections/{id}/materials",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)@ResponseStatus(HttpStatus.CREATED)public Map<String,Object>material(@PathVariable long id,@RequestPart(value="remarks",required=false)String remarks,@RequestPart("file")MultipartFile file){return service.upload(id,remarks,file);}
 @GetMapping("/materials/{id}/download")public ResponseEntity<InputStreamResource>download(@PathVariable long id)throws java.io.IOException{var f=service.download(id);HttpHeaders h=new HttpHeaders();h.setContentDisposition(ContentDisposition.attachment().filename(f.name(),StandardCharsets.UTF_8).build());h.setContentLength(java.nio.file.Files.size(f.path()));try{h.setContentType(MediaType.parseMediaType(f.type()));}catch(Exception e){h.setContentType(MediaType.APPLICATION_OCTET_STREAM);}return new ResponseEntity<>(new InputStreamResource(java.nio.file.Files.newInputStream(f.path())),h,HttpStatus.OK);}
}
