package com.zhineng.platform.evaluation.internal.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.zhineng.platform.common.audit.OperationLogRepository;
import com.zhineng.platform.common.user.dto.CurrentUserResponse;
import com.zhineng.platform.common.user.service.CurrentUserService;
import com.zhineng.platform.evaluation.internal.dto.InternalEvaluationDtos;
import com.zhineng.platform.evaluation.internal.repository.InternalEvaluationRepository;
import com.zhineng.platform.evaluation.internal.storage.InternalEvaluationStorageService;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@Transactional
public class InternalEvaluationService {
    private static final Set<String> TASK_TYPES=Set.of("SPECIAL","ANNUAL","POST_ADJUSTMENT");
    private static final Set<String> BASIS_TYPES=Set.of("NONE","DEDUCTION","BONUS","VETO");
    private static final Set<String> FILE_EXTENSIONS=Set.of(
            "pdf","png","jpg","jpeg","doc","docx","xls","xlsx","zip");
    private static final long MAX_FILE_SIZE=10L*1024*1024;
    private final InternalEvaluationRepository repository;
    private final CurrentUserService currentUser;
    private final OperationLogRepository operationLog;
    private final InternalEvaluationStorageService storage;
    private final ObjectMapper objectMapper;

    public InternalEvaluationService(
            InternalEvaluationRepository repository,CurrentUserService currentUser,
            OperationLogRepository operationLog,InternalEvaluationStorageService storage,
            ObjectMapper objectMapper
    ) {
        this.repository=repository;
        this.currentUser=currentUser;
        this.operationLog=operationLog;
        this.storage=storage;
        this.objectMapper=objectMapper;
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> tasks(){return repository.tasks();}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> versions(){return repository.publishedVersions();}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> organizations(){return repository.activeOrgs();}
    @Transactional(readOnly=true)
    public List<Map<String,Object>> users(){return repository.activeUsers();}

    @Transactional(readOnly=true)
    public Map<String,Object> task(long id){
        Map<String,Object> task=requireTask(id);
        task.put("organizations",repository.taskOrgs(id));
        task.put("snapshots",repository.snapshots(id));
        task.put("history",repository.history("TASK",id));
        return task;
    }

    public Map<String,Object> createTask(InternalEvaluationDtos.TaskRequest request){
        TaskValues values=validateTask(request);
        CurrentUserResponse user=currentUser.getCurrentUser();
        long id=repository.insertTask(values.code,values.name,values.year,values.type,
                values.start,values.end,values.description,values.versionId,null,user.id());
        addOrganizations(id,values.orgIds,values.evaluatorId,values.reviewerId,user.id());
        repository.history("TASK",id,null,"DRAFT","创建任务",user.id());
        Map<String,Object> after=task(id);
        log("M2-5","INTERNAL_TASK",id,"CREATE",null,after);
        return after;
    }

    public Map<String,Object> copyTask(long id,InternalEvaluationDtos.CopyRequest request){
        Map<String,Object> source=requireTask(id);
        String code=code(request==null?null:request.taskCode());
        String name=required(request.taskName(),"任务名称");
        int year=request.evaluationYear()==null?LocalDate.now().getYear():request.evaluationYear();
        CurrentUserResponse user=currentUser.getCurrentUser();
        long newId=repository.insertTask(code,name,year,source.get("task_type").toString(),
                trim(source.get("start_date")),trim(source.get("end_date")),
                trim(source.get("description")),number(source.get("indicator_version_id")),
                id,user.id());
        for(Map<String,Object> org:repository.taskOrgs(id)){
            long taskOrgId=repository.insertTaskOrg(newId,number(org.get("org_unit_id")),user.id());
            repository.insertAssignment(taskOrgId,number(org.get("evaluator_id")),
                    number(org.get("reviewer_id")),user.id());
        }
        repository.history("TASK",newId,null,"DRAFT","复制任务"+id,user.id());
        Map<String,Object> after=task(newId);
        log("M2-5","INTERNAL_TASK",newId,"COPY",source,after);
        return after;
    }

    public Map<String,Object> publish(long id){
        Map<String,Object> before=requireTask(id);
        if(!"DRAFT".equals(before.get("status"))) conflict("TASK_READ_ONLY","只有草稿任务可以发布");
        if(!"PUBLISHED".equals(before.get("indicator_version_status")))
            bad("INDICATOR_VERSION_NOT_PUBLISHED","任务必须选择已发布指标版本");
        List<Map<String,Object>> orgs=repository.taskOrgs(id);
        List<Map<String,Object>> indicators=repository.sourceIndicators(
                number(before.get("indicator_version_id")));
        if(orgs.isEmpty()) bad("TASK_ORGS_REQUIRED","发布前至少选择一个参评机构");
        if(indicators.isEmpty()) bad("SCORE_ITEMS_REQUIRED","已发布指标版本没有启用的三级评分项");
        long userId=currentUser.getCurrentUser().id();
        for(Map<String,Object> indicator:indicators){
            repository.insertSnapshot(id,indicator,json(repository.sourceRules(number(indicator.get("id")))));
        }
        List<Map<String,Object>> snapshots=repository.snapshots(id);
        for(Map<String,Object> org:orgs){
            long sheetId=repository.insertSheet(number(org.get("id")),userId);
            for(Map<String,Object> snapshot:snapshots)
                repository.insertEntry(sheetId,number(snapshot.get("id")),userId);
            repository.history("SCORE_SHEET",sheetId,null,"NOT_STARTED","任务发布生成",userId);
        }
        if(repository.publishTask(id,userId)!=1) conflict("TASK_STATE_CHANGED","任务状态已变化");
        repository.history("TASK",id,"DRAFT","PUBLISHED","发布任务并冻结指标快照",userId);
        Map<String,Object> after=task(id);
        log("M2-5","INTERNAL_TASK",id,"PUBLISH",before,after);
        return after;
    }

    public Map<String,Object> taskStatus(long id,InternalEvaluationDtos.StatusRequest request){
        Map<String,Object> before=requireTask(id);
        String status=upper(request==null?null:request.status());
        if(!"CANCELLED".equals(status)) bad("INVALID_TASK_STATUS","此接口仅支持取消任务");
        if(Set.of("COMPLETED","CANCELLED").contains(before.get("status")))
            conflict("TASK_FINALIZED","已完成或已取消任务不能再次变更");
        String reason=required(request.reason(),"取消原因");
        if(repository.taskStatus(id,status,reason,rowVersion(request.rowVersion()),
                currentUser.getCurrentUser().id())!=1) conflict("STALE_TASK","任务已变化");
        Map<String,Object> after=task(id);
        log("M2-5","INTERNAL_TASK",id,"CANCEL",before,after);
        return after;
    }

    @Transactional(readOnly=true)
    public Map<String,Object> scoreSheet(long id){
        Map<String,Object> sheet=requireSheet(id);
        sheet.put("entries",repository.entries(id));
        sheet.put("reviews",repository.reviews(id));
        sheet.put("history",repository.history("SCORE_SHEET",id));
        return sheet;
    }

    public Map<String,Object> saveScores(long sheetId,InternalEvaluationDtos.SaveScoresRequest request){
        Map<String,Object> sheet=requireEditableSheet(sheetId);
        if(request==null||request.entries()==null) bad("SCORES_REQUIRED","评分项不能为空");
        long userId=currentUser.getCurrentUser().id();
        for(InternalEvaluationDtos.ScoreInput input:request.entries()){
            Map<String,Object> entry=requireEntry(input.entryId());
            if(number(entry.get("sheet_id"))!=sheetId) bad("ENTRY_NOT_IN_SHEET","评分项不属于当前评分表");
            double standard=decimal(entry.get("standard_score"));
            double score=input.score()==null?0:input.score();
            if(score<0||score>standard) bad("INVALID_SCORE","得分必须在0到标准分之间");
            String basis=upper(input.basisType());
            if(basis==null) basis="NONE";
            if(!BASIS_TYPES.contains(basis)) bad("INVALID_BASIS_TYPE","依据类型无效");
            if(repository.updateEntry(input.entryId(),score,basis,trim(input.scoreBasis()),
                    trim(input.remarks()),Boolean.TRUE.equals(input.vetoTriggered()),
                    rowVersion(input.rowVersion()),userId)!=1)
                conflict("STALE_SCORE_ENTRY","评分项已变化，请刷新后重试");
        }
        double total=repository.entries(sheetId).stream()
                .mapToDouble(row->decimal(row.get("score"))).sum();
        if(repository.saveSheet(sheetId,total,rowVersion(request.sheetRowVersion()),userId)!=1)
            conflict("STALE_SCORE_SHEET","评分表已变化，请刷新后重试");
        repository.history("SCORE_SHEET",sheetId,sheet.get("status").toString(),
                "DRAFT","暂存评分",userId);
        repository.updateTaskProgress(number(sheet.get("task_id")),userId);
        Map<String,Object> after=scoreSheet(sheetId);
        log("M2-6","INTERNAL_SCORE_SHEET",sheetId,"SAVE",sheet,after);
        return after;
    }

    public Map<String,Object> submit(long sheetId,InternalEvaluationDtos.ReviewRequest request){
        Map<String,Object> before=requireSheet(sheetId);
        if(!"DRAFT".equals(before.get("status")))
            conflict("SHEET_NOT_EDITABLE","只有已暂存评分表可以提交复核");
        long userId=currentUser.getCurrentUser().id();
        if(repository.sheetStatus(sheetId,"DRAFT","SUBMITTED",trim(request==null?null:request.opinion()),
                rowVersion(request==null?null:request.rowVersion()),userId)!=1)
            conflict("STALE_SCORE_SHEET","评分表已变化");
        repository.history("SCORE_SHEET",sheetId,"DRAFT","SUBMITTED","提交复核",userId);
        repository.updateTaskProgress(number(before.get("task_id")),userId);
        Map<String,Object> after=scoreSheet(sheetId);
        log("M2-6","INTERNAL_SCORE_SHEET",sheetId,"SUBMIT",before,after);
        return after;
    }

    public Map<String,Object> review(long sheetId,InternalEvaluationDtos.ReviewRequest request){
        Map<String,Object> before=requireSheet(sheetId);
        if(!"SUBMITTED".equals(before.get("status")))
            conflict("SHEET_NOT_SUBMITTED","只有已提交评分表可以复核");
        String action=upper(request==null?null:request.action());
        if(!Set.of("RETURN","CONFIRM").contains(action))
            bad("INVALID_REVIEW_ACTION","复核结果必须为RETURN或CONFIRM");
        String opinion=required(request.opinion(),"复核意见");
        String to="RETURN".equals(action)?"RETURNED":"CONFIRMED";
        long userId=currentUser.getCurrentUser().id();
        if(repository.sheetStatus(sheetId,"SUBMITTED",to,opinion,rowVersion(request.rowVersion()),
                userId)!=1) conflict("STALE_SCORE_SHEET","评分表已变化");
        repository.history("SCORE_SHEET",sheetId,"SUBMITTED",to,opinion,userId);
        repository.updateTaskProgress(number(before.get("task_id")),userId);
        Map<String,Object> after=scoreSheet(sheetId);
        log("M2-6","INTERNAL_SCORE_SHEET",sheetId,action,before,after);
        return after;
    }

    public Map<String,Object> upload(long entryId,String remarks,MultipartFile file){
        Map<String,Object> entry=requireEntry(entryId);
        if(!Set.of("NOT_STARTED","DRAFT","RETURNED").contains(entry.get("sheet_status")))
            conflict("SHEET_NOT_EDITABLE","提交后不能上传材料，需退回后修改");
        validateFile(file);
        String ext=extension(file.getOriginalFilename());
        InternalEvaluationStorageService.StoredFile stored=null;
        try{
            stored=storage.store(file.getInputStream(),ext);
            long id=repository.insertAttachment(entryId,safeName(file.getOriginalFilename()),
                    stored.storedName(),stored.relativePath(),file.getContentType(),ext,
                    file.getSize(),sha256(storage.resolve(stored.relativePath())),
                    currentUser.getCurrentUser().id(),trim(remarks));
            Map<String,Object> after=repository.attachment(id);
            log("M2-6","INTERNAL_SCORE_MATERIAL",id,"UPLOAD",null,after);
            return after;
        }catch(IOException|RuntimeException exception){
            if(stored!=null) storage.deleteQuietly(stored.relativePath());
            if(exception instanceof InternalEvaluationException business) throw business;
            throw new InternalEvaluationException(HttpStatus.BAD_REQUEST,"FILE_UPLOAD_FAILED",
                    "材料上传失败："+exception.getMessage());
        }
    }

    @Transactional(readOnly=true)
    public List<Map<String,Object>> materials(long entryId){
        requireEntry(entryId);return repository.materials(entryId);
    }

    @Transactional(readOnly=true)
    public Download download(long attachmentId,boolean preview)throws IOException{
        Map<String,Object> attachment=repository.attachment(attachmentId);
        if(attachment==null) notFound("ATTACHMENT_NOT_FOUND","评分材料不存在");
        String ext=String.valueOf(attachment.get("extension")).toLowerCase(Locale.ROOT);
        if(preview&&!Set.of("pdf","png","jpg","jpeg").contains(ext))
            bad("PREVIEW_NOT_SUPPORTED","此文件类型仅支持下载");
        Path path;
        try{path=storage.resolveForRead(attachment.get("storage_path").toString());}
        catch(IllegalArgumentException exception){bad("INVALID_FILE_PATH","附件路径非法");return null;}
        return new Download(path,attachment.get("original_name").toString(),
                attachment.get("content_type")==null?"application/octet-stream":
                        attachment.get("content_type").toString());
    }

    private TaskValues validateTask(InternalEvaluationDtos.TaskRequest request){
        if(request==null||request.indicatorVersionId()==null) bad("TASK_FIELDS_REQUIRED","任务和指标版本不能为空");
        Map<String,Object> version=repository.one("SELECT * FROM indicator_versions WHERE id=?",request.indicatorVersionId());
        if(version==null||!"PUBLISHED".equals(version.get("status")))
            bad("INDICATOR_VERSION_NOT_PUBLISHED","只能选择已发布指标版本");
        String type=upper(request.taskType());
        if(!TASK_TYPES.contains(type)) bad("INVALID_TASK_TYPE","任务类型无效");
        int year=request.evaluationYear()==null?LocalDate.now().getYear():request.evaluationYear();
        String start=date(request.startDate(),"开始日期"),end=date(request.endDate(),"结束日期");
        if(start!=null&&end!=null&&start.compareTo(end)>0) bad("INVALID_DATE_RANGE","开始日期不能晚于结束日期");
        if(request.orgUnitIds()==null||request.orgUnitIds().isEmpty()) bad("TASK_ORGS_REQUIRED","请选择参评机构");
        if(request.evaluatorId()==null||request.reviewerId()==null) bad("ASSIGNEES_REQUIRED","请选择评分人和复核人");
        return new TaskValues(code(request.taskCode()),required(request.taskName(),"任务名称"),year,type,
                start,end,trim(request.description()),request.indicatorVersionId(),
                request.orgUnitIds().stream().distinct().toList(),request.evaluatorId(),request.reviewerId());
    }

    private void addOrganizations(long taskId,List<Long> orgIds,long evaluator,long reviewer,long userId){
        for(Long orgId:orgIds){
            Map<String,Object> org=repository.one("SELECT * FROM org_units WHERE id=?",orgId);
            if(org==null||!"ACTIVE".equals(org.get("status"))||
                    Set.of("ROOT","GROUP").contains(org.get("unit_type")))
                bad("INVALID_ORGANIZATION","参评对象必须为启用业务机构");
            long taskOrgId=repository.insertTaskOrg(taskId,orgId,userId);
            repository.insertAssignment(taskOrgId,evaluator,reviewer,userId);
        }
    }

    private Map<String,Object> requireTask(long id){Map<String,Object> row=repository.task(id);if(row==null)notFound("TASK_NOT_FOUND","评价任务不存在");return row;}
    private Map<String,Object> requireSheet(long id){Map<String,Object> row=repository.sheet(id);if(row==null)notFound("SHEET_NOT_FOUND","评分表不存在");return row;}
    private Map<String,Object> requireEditableSheet(long id){Map<String,Object> row=requireSheet(id);if(!Set.of("NOT_STARTED","DRAFT","RETURNED").contains(row.get("status")))conflict("SHEET_NOT_EDITABLE","评分提交后只读，退回后才能修改");return row;}
    private Map<String,Object> requireEntry(Long id){if(id==null)bad("ENTRY_REQUIRED","评分项不能为空");Map<String,Object> row=repository.entry(id);if(row==null)notFound("ENTRY_NOT_FOUND","评分项不存在");return row;}
    private void validateFile(MultipartFile file){if(file==null||file.isEmpty())bad("EMPTY_FILE","请选择材料文件");if(file.getSize()>MAX_FILE_SIZE)bad("FILE_TOO_LARGE","单文件不能超过10MB");if(!FILE_EXTENSIONS.contains(extension(file.getOriginalFilename())))bad("UNSUPPORTED_FILE","不支持的材料格式");}
    private String extension(String name){if(name==null||!name.contains("."))return "";return name.substring(name.lastIndexOf('.')+1).toLowerCase(Locale.ROOT);}
    private String safeName(String name){String value=name==null?"material":Path.of(name).getFileName().toString();return value.replaceAll("[\\r\\n]","_");}
    private String sha256(Path path)throws IOException{try{MessageDigest digest=MessageDigest.getInstance("SHA-256");return HexFormat.of().formatHex(digest.digest(Files.readAllBytes(path)));}catch(Exception e){throw new IOException(e);}}
    private String date(String value,String label){if(value==null||value.isBlank())return null;try{return LocalDate.parse(value.trim()).toString();}catch(DateTimeParseException e){bad("INVALID_DATE",label+"格式应为YYYY-MM-DD");return null;}}
    private String code(String value){String result=required(value,"任务编码").toUpperCase(Locale.ROOT);if(!result.matches("[A-Z0-9][A-Z0-9_-]{0,63}"))bad("INVALID_CODE","编码只能使用字母、数字、下划线和连字符");return result;}
    private String required(Object value,String label){String text=trim(value);if(text==null)bad("REQUIRED_FIELD",label+"不能为空");return text;}
    private String trim(Object value){if(value==null)return null;String text=value.toString().trim();return text.isEmpty()?null:text;}
    private String upper(String value){return value==null||value.isBlank()?null:value.trim().toUpperCase(Locale.ROOT);}
    private int rowVersion(Integer value){if(value==null||value<0)bad("ROW_VERSION_REQUIRED","缺少有效版本号");return value;}
    private long number(Object value){return ((Number)value).longValue();}
    private double decimal(Object value){return ((Number)value).doubleValue();}
    private String json(Object value){try{return value==null?null:objectMapper.writeValueAsString(value);}catch(JsonProcessingException e){throw new IllegalStateException(e);}}
    private void log(String module,String type,long id,String action,Object before,Object after){operationLog.success(module,type,id,action,currentUser.getCurrentUser().id(),"LOCAL","/api/internal-evaluations",json(before),json(after));}
    private void bad(String code,String message){throw new InternalEvaluationException(HttpStatus.BAD_REQUEST,code,message);}
    private void conflict(String code,String message){throw new InternalEvaluationException(HttpStatus.CONFLICT,code,message);}
    private void notFound(String code,String message){throw new InternalEvaluationException(HttpStatus.NOT_FOUND,code,message);}
    private record TaskValues(String code,String name,int year,String type,String start,String end,String description,long versionId,List<Long> orgIds,long evaluatorId,long reviewerId){}
    public record Download(Path path,String fileName,String contentType){}
}
