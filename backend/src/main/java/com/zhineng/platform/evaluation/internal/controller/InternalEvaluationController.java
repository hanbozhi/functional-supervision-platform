package com.zhineng.platform.evaluation.internal.controller;

import com.zhineng.platform.evaluation.internal.dto.InternalEvaluationDtos;
import com.zhineng.platform.evaluation.internal.service.InternalEvaluationService;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/internal-evaluations")
public class InternalEvaluationController {
    private final InternalEvaluationService service;

    public InternalEvaluationController(InternalEvaluationService service) {
        this.service=service;
    }

    @GetMapping("/tasks")
    public List<Map<String,Object>> tasks(){return service.tasks();}
    @GetMapping("/tasks/{id}")
    public Map<String,Object> task(@PathVariable long id){return service.task(id);}
    @PostMapping("/tasks")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> create(@RequestBody InternalEvaluationDtos.TaskRequest request){return service.createTask(request);}
    @PostMapping("/tasks/{id}/copy")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> copy(@PathVariable long id,@RequestBody InternalEvaluationDtos.CopyRequest request){return service.copyTask(id,request);}
    @PostMapping("/tasks/{id}/publish")
    public Map<String,Object> publish(@PathVariable long id){return service.publish(id);}
    @PutMapping("/tasks/{id}/status")
    public Map<String,Object> taskStatus(@PathVariable long id,@RequestBody InternalEvaluationDtos.StatusRequest request){return service.taskStatus(id,request);}
    @GetMapping("/options/indicator-versions")
    public List<Map<String,Object>> versions(){return service.versions();}
    @GetMapping("/options/organizations")
    public List<Map<String,Object>> organizations(){return service.organizations();}
    @GetMapping("/options/users")
    public List<Map<String,Object>> users(){return service.users();}
    @GetMapping("/score-sheets/{id}")
    public Map<String,Object> sheet(@PathVariable long id){return service.scoreSheet(id);}
    @PutMapping("/score-sheets/{id}/scores")
    public Map<String,Object> save(@PathVariable long id,@RequestBody InternalEvaluationDtos.SaveScoresRequest request){return service.saveScores(id,request);}
    @PostMapping("/score-sheets/{id}/submit")
    public Map<String,Object> submit(@PathVariable long id,@RequestBody InternalEvaluationDtos.ReviewRequest request){return service.submit(id,request);}
    @PostMapping("/score-sheets/{id}/review")
    public Map<String,Object> review(@PathVariable long id,@RequestBody InternalEvaluationDtos.ReviewRequest request){return service.review(id,request);}
    @GetMapping("/score-entries/{id}/materials")
    public List<Map<String,Object>> materials(@PathVariable long id){return service.materials(id);}

    @PostMapping(value="/score-entries/{id}/materials",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String,Object> upload(@PathVariable long id,
            @RequestPart(value="remarks",required=false) String remarks,
            @RequestPart("file") MultipartFile file){return service.upload(id,remarks,file);}

    @GetMapping("/materials/{id}/download")
    public ResponseEntity<InputStreamResource> download(@PathVariable long id)throws java.io.IOException{return file(service.download(id,false),false);}
    @GetMapping("/materials/{id}/preview")
    public ResponseEntity<InputStreamResource> preview(@PathVariable long id)throws java.io.IOException{return file(service.download(id,true),true);}

    private ResponseEntity<InputStreamResource> file(InternalEvaluationService.Download file,boolean inline)throws java.io.IOException{
        HttpHeaders headers=new HttpHeaders();
        headers.set("X-Content-Type-Options","nosniff");
        headers.setContentDisposition((inline?ContentDisposition.inline():ContentDisposition.attachment())
                .filename(file.fileName(), StandardCharsets.UTF_8).build());
        try{headers.setContentType(MediaType.parseMediaType(file.contentType()));}
        catch(Exception ignored){headers.setContentType(MediaType.APPLICATION_OCTET_STREAM);}
        headers.setContentLength(java.nio.file.Files.size(file.path()));
        return new ResponseEntity<>(new InputStreamResource(java.nio.file.Files.newInputStream(file.path())),headers,HttpStatus.OK);
    }
}
