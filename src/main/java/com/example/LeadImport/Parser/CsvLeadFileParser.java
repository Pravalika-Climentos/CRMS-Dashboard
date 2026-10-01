package com.example.LeadImport.Parser;
import com.example.LeadImport.Entity.LeadImportFileType;
import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
@Component
public class CsvLeadFileParser implements LeadFileParser{
 public boolean supports(LeadImportFileType type){return type==LeadImportFileType.CSV;}
 public ParsedLeadFile parse(MultipartFile file)throws IOException{
  try(Reader reader=new InputStreamReader(file.getInputStream(),StandardCharsets.UTF_8);
      CSVParser csv=CSVFormat.DEFAULT.builder().setHeader().setSkipHeaderRecord(true).setIgnoreEmptyLines(true).setTrim(true).get().parse(reader)){
   List<String> columns=new ArrayList<>(csv.getHeaderMap().keySet()); List<Map<String,String>> rows=new ArrayList<>();
   csv.forEach(record->{Map<String,String> row=new LinkedHashMap<>();columns.forEach(c->row.put(c,record.isMapped(c)?record.get(c):""));rows.add(row);});
   return new ParsedLeadFile(columns,rows);
  }
 }
}
