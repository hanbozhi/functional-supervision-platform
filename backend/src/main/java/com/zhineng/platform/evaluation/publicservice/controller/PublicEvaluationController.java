package com.zhineng.platform.evaluation.publicservice.controller;
import com.zhineng.platform.evaluation.publicservice.dto.PublicEvaluationDtos.*;import com.zhineng.platform.evaluation.publicservice.service.PublicEvaluationService;
import java.nio.charset.StandardCharsets;import java.nio.file.Files;import java.util.*;import org.springframework.core.io.InputStreamResource;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import org.springframework.web.multipart.MultipartFile;
@RestController @RequestMapping("/api/public-evaluations") public class PublicEvaluationController {
 private final PublicEvaluationService service;public PublicEvaluationController(PublicEvaluationService s){service=s;}
 @GetMapping("/options/organizations")List<Map<String,Object>>orgs(){return service.orgs();}
 @GetMapping("/service-items")List<Map<String,Object>>items(){return service.items();}
 @PostMapping("/service-items")@ResponseStatus(HttpStatus.CREATED)Map<String,Object>createItem(@RequestBody ServiceItemRequest q){return service.saveItem(null,q);}
 @PutMapping("/service-items/{id}")Map<String,Object>updateItem(@PathVariable long id,@RequestBody ServiceItemRequest q){return service.saveItem(id,q);}
 @PostMapping(consumes=MediaType.MULTIPART_FORM_DATA_VALUE)@ResponseStatus(HttpStatus.CREATED)Map<String,Object>submit(@RequestPart("data")EvaluationRequest q,@RequestPart(value="image",required=false)MultipartFile image){return service.submit(q,image);}
 @GetMapping List<Map<String,Object>>list(@RequestParam(required=false)Long orgUnitId,@RequestParam(required=false)String source,@RequestParam(required=false)String status,@RequestParam(required=false)Integer minScore,@RequestParam(required=false)Boolean anonymous){return service.list(orgUnitId,source,status,minScore,anonymous);}
 @GetMapping("/{id}")Map<String,Object>detail(@PathVariable long id){return service.detail(id);}
 @PutMapping("/{id}/process")Map<String,Object>process(@PathVariable long id,@RequestBody ProcessRequest q){return service.process(id,q);}
 @PutMapping("/{id}/sentiment")Map<String,Object>sentiment(@PathVariable long id,@RequestBody SentimentRequest q){return service.sentiment(id,q);}
 @PostMapping("/{id}/privacy-requests")@ResponseStatus(HttpStatus.CREATED)Map<String,Object>request(@PathVariable long id,@RequestBody AccessRequest q){return service.requestAccess(id,q);}
 @GetMapping("/privacy-requests")List<Map<String,Object>>requests(){return service.requests();}
 @PostMapping("/privacy-requests/{id}/review")Map<String,Object>review(@PathVariable long id,@RequestBody ReviewRequest q){return service.review(id,q);}
 @PostMapping("/privacy-requests/{id}/reveal")Map<String,Object>reveal(@PathVariable long id){return service.reveal(id);}
 @GetMapping("/privacy-audits")List<Map<String,Object>>audits(){return service.audits();}
 @PostMapping(value="/imports",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)@ResponseStatus(HttpStatus.CREATED)Map<String,Object>importFile(@RequestPart("source")String source,@RequestPart("file")MultipartFile file){return service.importFile(source,file);}
 @GetMapping("/imports")List<Map<String,Object>>batches(){return service.batches();}
 @GetMapping("/imports/{id}")Map<String,Object>batch(@PathVariable long id){return service.batch(id);}
 @GetMapping("/stats")Map<String,Object>stats(){return service.stats();}
 @GetMapping("/attachments/{id}/download")ResponseEntity<InputStreamResource>download(@PathVariable long id)throws Exception{var f=service.download(id);HttpHeaders h=new HttpHeaders();h.setContentDisposition(ContentDisposition.attachment().filename(f.name(),StandardCharsets.UTF_8).build());h.setContentLength(Files.size(f.path()));try{h.setContentType(MediaType.parseMediaType(f.type()));}catch(Exception e){h.setContentType(MediaType.APPLICATION_OCTET_STREAM);}return new ResponseEntity<>(new InputStreamResource(Files.newInputStream(f.path())),h,HttpStatus.OK);}
}
