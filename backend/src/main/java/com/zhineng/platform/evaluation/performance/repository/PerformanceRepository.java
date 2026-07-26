package com.zhineng.platform.evaluation.performance.repository;
import java.sql.*;import java.util.*;import org.springframework.jdbc.core.JdbcTemplate;import org.springframework.jdbc.support.*;import org.springframework.stereotype.Repository;
@Repository public class PerformanceRepository{
 private final JdbcTemplate jdbc;public PerformanceRepository(JdbcTemplate jdbc){this.jdbc=jdbc;}
 public Map<String,Object>activeOrg(){return one("SELECT * FROM org_units WHERE status='ACTIVE' AND unit_type NOT IN('ROOT','GROUP') ORDER BY id LIMIT 1");}
 public List<Map<String,Object>> mappings(){return jdbc.queryForList("SELECT * FROM org_performance_field_mappings ORDER BY sort_order,id");}
 public Map<String,Object> mapping(long id){return one("SELECT * FROM org_performance_field_mappings WHERE id=?",id);}
 public long insertMapping(String source,String target,boolean required,int order,long user){return insert("INSERT INTO org_performance_field_mappings(source_field,target_field,required,sort_order,created_by,updated_by)VALUES(?,?,?,?,?,?)",source,target,required?1:0,order,user,user);}
 public int updateMapping(long id,String source,String target,boolean required,int order,int rv,long user){return jdbc.update("UPDATE org_performance_field_mappings SET source_field=?,target_field=?,required=?,sort_order=?,updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),row_version=row_version+1 WHERE id=? AND row_version=?",source,target,required?1:0,order,user,id,rv);}
 public int mappingStatus(long id,String status,int rv,long user){return jdbc.update("UPDATE org_performance_field_mappings SET status=?,updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),row_version=row_version+1 WHERE id=? AND row_version=?",status,user,id,rv);}
 public long insertBatch(String code,String name,long size,long user){return insert("INSERT INTO org_performance_import_batches(batch_code,original_file_name,file_size,imported_by)VALUES(?,?,?,?)",code,name,size,user);}
 public void finishBatch(long id,int total,int success,int failed,int warnings){String status=failed==0?"COMPLETED":success==0?"FAILED":"PARTIAL_FAILED";jdbc.update("UPDATE org_performance_import_batches SET total_rows=?,success_rows=?,failed_rows=?,warning_rows=?,status=? WHERE id=?",total,success,failed,warnings,status,id);}
 public List<Map<String,Object>> batches(){return jdbc.queryForList("SELECT * FROM org_performance_import_batches ORDER BY id DESC");}
 public Map<String,Object> batch(long id){return one("SELECT * FROM org_performance_import_batches WHERE id=?",id);}
 public Map<String,Object> orgByCode(String code){return one("SELECT * FROM org_units WHERE unit_code=? COLLATE NOCASE",code);}
 public void insertRaw(long batch,int row,long org,String code,String sourceName,int year,String grade,Double score,String rating,String remarks,String raw,String warning){jdbc.update("INSERT INTO org_performance_raw_records(batch_id,source_row_number,org_unit_id,org_code,source_org_name,evaluation_year,performance_grade,key_work_score,leadership_rating,remarks,raw_json,warning_message)VALUES(?,?,?,?,?,?,?,?,?,?,?,?)",batch,row,org,code,sourceName,year,grade,score,rating,remarks,raw,warning);}
 public void insertError(long batch,int row,String code,String raw,String error){jdbc.update("INSERT INTO org_performance_import_errors(batch_id,source_row_number,org_code,raw_json,error_message)VALUES(?,?,?,?,?)",batch,row,code,raw,error);}
 public List<Map<String,Object>> batchRecords(long id){return jdbc.queryForList("SELECT r.*,o.unit_name FROM org_performance_raw_records r JOIN org_units o ON o.id=r.org_unit_id WHERE batch_id=? ORDER BY source_row_number",id);}
 public List<Map<String,Object>> errors(long id){return jdbc.queryForList("SELECT * FROM org_performance_import_errors WHERE batch_id=? ORDER BY source_row_number",id);}
 public List<Map<String,Object>> effective(Integer year){
  String filter=year==null?"":" AND latest.evaluation_year="+year;
  return jdbc.queryForList("""
   WITH ranked AS(
    SELECT r.*,row_number() OVER(PARTITION BY org_unit_id,evaluation_year ORDER BY batch_id DESC,id DESC) rn
    FROM org_performance_raw_records r
   ),latest AS(SELECT * FROM ranked WHERE rn=1)
   SELECT latest.*,o.unit_name,
    c.id correction_id,c.status correction_status,c.correction_scope,c.correction_reason,
    COALESCE(c.corrected_grade,latest.performance_grade) effective_grade,
    COALESCE(c.corrected_key_work_score,latest.key_work_score) effective_key_work_score,
    COALESCE(c.corrected_leadership_rating,latest.leadership_rating) effective_leadership_rating
   FROM latest JOIN org_units o ON o.id=latest.org_unit_id
   LEFT JOIN org_performance_corrections c ON c.id=(
    SELECT x.id FROM org_performance_corrections x
    WHERE x.raw_record_id=latest.id AND x.status='CONFIRMED' ORDER BY x.id DESC LIMIT 1)
   WHERE 1=1
   """+filter+" ORDER BY latest.evaluation_year DESC,o.unit_name");
 }
 public Map<String,Object> raw(long id){return one("SELECT r.*,o.unit_name FROM org_performance_raw_records r JOIN org_units o ON o.id=r.org_unit_id WHERE r.id=?",id);}
 public long insertCorrection(long raw,String scope,String original,String grade,Double score,String rating,String reason,long user){return insert("INSERT INTO org_performance_corrections(raw_record_id,correction_scope,original_values_json,corrected_grade,corrected_key_work_score,corrected_leadership_rating,correction_reason,created_by,updated_by)VALUES(?,?,?,?,?,?,?,?,?)",raw,scope,original,grade,score,rating,reason,user,user);}
 public Map<String,Object> correction(long id){return one("SELECT c.*,r.org_code,r.evaluation_year,o.unit_name,r.performance_grade,r.key_work_score,r.leadership_rating FROM org_performance_corrections c JOIN org_performance_raw_records r ON r.id=c.raw_record_id JOIN org_units o ON o.id=r.org_unit_id WHERE c.id=?",id);}
 public List<Map<String,Object>> corrections(){return jdbc.queryForList("SELECT c.*,r.org_code,r.evaluation_year,o.unit_name,r.performance_grade,r.key_work_score,r.leadership_rating FROM org_performance_corrections c JOIN org_performance_raw_records r ON r.id=c.raw_record_id JOIN org_units o ON o.id=r.org_unit_id ORDER BY c.id DESC");}
 public int updateCorrection(long id,String scope,String grade,Double score,String rating,String reason,int rv,long user){return jdbc.update("UPDATE org_performance_corrections SET correction_scope=?,corrected_grade=?,corrected_key_work_score=?,corrected_leadership_rating=?,correction_reason=?,status='DRAFT',review_opinion=NULL,reviewed_by=NULL,reviewed_at=NULL,updated_by=?,updated_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),row_version=row_version+1 WHERE id=? AND row_version=? AND status IN('DRAFT','REJECTED')",scope,grade,score,rating,reason,user,id,rv);}
 public int correctionStatus(long id,String from,String to,String opinion,int rv,long user){int count;if("SUBMITTED".equals(to))count=jdbc.update("UPDATE org_performance_corrections SET status=?,submitted_by=?,submitted_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),updated_by=?,row_version=row_version+1 WHERE id=? AND row_version=? AND status=?",to,user,user,id,rv,from);else count=jdbc.update("UPDATE org_performance_corrections SET status=?,reviewed_by=?,reviewed_at=strftime('%Y-%m-%dT%H:%M:%SZ','now'),review_opinion=?,updated_by=?,row_version=row_version+1 WHERE id=? AND row_version=? AND status=?",to,user,opinion,user,id,rv,from);if(count==1)history(id,from,to,opinion,user);return count;}
 public void history(long id,String from,String to,String opinion,long user){jdbc.update("INSERT INTO org_performance_correction_history(correction_id,from_status,to_status,opinion,operator_id)VALUES(?,?,?,?,?)",id,from,to,opinion,user);}
 public List<Map<String,Object>> history(long id){return jdbc.queryForList("SELECT h.*,u.display_name operator_name FROM org_performance_correction_history h LEFT JOIN sys_users u ON u.id=h.operator_id WHERE correction_id=? ORDER BY h.id DESC",id);}
 public long insertAttachment(long correction,String original,String stored,String path,String type,String ext,long size,String sha,String remarks,long user){long aid=insert("INSERT INTO sys_attachments(business_type,business_id,original_name,stored_name,storage_path,content_type,extension,file_size,sha256,uploaded_by)VALUES('ORG_PERFORMANCE_CORRECTION',?,?,?,?,?,?,?,?,?)",correction,original,stored,path,type,ext,size,sha,user);insert("INSERT INTO org_performance_correction_materials(correction_id,attachment_id,remarks,created_by)VALUES(?,?,?,?)",correction,aid,remarks,user);return aid;}
 public List<Map<String,Object>> materials(long id){return jdbc.queryForList("SELECT m.*,a.original_name,a.storage_path,a.content_type,a.extension,a.file_size FROM org_performance_correction_materials m JOIN sys_attachments a ON a.id=m.attachment_id WHERE m.correction_id=? AND a.status='ACTIVE' ORDER BY a.id DESC",id);}
 public Map<String,Object> attachment(long id){return one("SELECT a.* FROM sys_attachments a JOIN org_performance_correction_materials m ON m.attachment_id=a.id WHERE a.id=? AND a.status='ACTIVE'",id);}
 public Map<String,Object> one(String sql,Object...args){List<Map<String,Object>> rows=jdbc.queryForList(sql,args);return rows.isEmpty()?null:rows.get(0);}
 private long insert(String sql,Object...args){KeyHolder k=new GeneratedKeyHolder();jdbc.update(c->{PreparedStatement s=c.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);for(int i=0;i<args.length;i++)s.setObject(i+1,args[i]);return s;},k);return k.getKey().longValue();}
}
