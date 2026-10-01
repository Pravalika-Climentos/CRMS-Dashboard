package com.example.LeadImport.Parser;
import com.example.LeadImport.Entity.LeadImportFileType;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
@Component @RequiredArgsConstructor
public class JsonLeadFileParser implements LeadFileParser{
 private final ObjectMapper mapper;
 public boolean supports(LeadImportFileType type){return type==LeadImportFileType.JSON;}
 public ParsedLeadFile parse(MultipartFile file)throws IOException{
  List<Map<String,Object>> input=mapper.readValue(file.getInputStream(),new TypeReference<>(){});LinkedHashSet<String> columns=new LinkedHashSet<>();input.forEach(r->columns.addAll(r.keySet()));List<Map<String,String>> rows=input.stream().map(r->{Map<String,String> out=new LinkedHashMap<>();columns.forEach(c->out.put(c,r.get(c)==null?"":String.valueOf(r.get(c))));return out;}).toList();return new ParsedLeadFile(List.copyOf(columns),rows);
 }
}
