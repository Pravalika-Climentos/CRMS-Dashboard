package com.example.LeadImport.Parser;
import com.example.LeadImport.Entity.LeadImportFileType;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
import java.util.*;
@Component
public class ExcelLeadFileParser implements LeadFileParser{
 public boolean supports(LeadImportFileType type){return type==LeadImportFileType.XLS||type==LeadImportFileType.XLSX;}
 public ParsedLeadFile parse(MultipartFile file)throws IOException{
  try(Workbook workbook=WorkbookFactory.create(file.getInputStream())){
   Sheet sheet=workbook.getSheetAt(0); DataFormatter fmt=new DataFormatter(); Row header=sheet.getRow(sheet.getFirstRowNum());
   if(header==null)throw new IllegalArgumentException("The spreadsheet has no header row.");
   List<String> columns=new ArrayList<>(); for(int c=0;c<header.getLastCellNum();c++){String name=fmt.formatCellValue(header.getCell(c)).trim();columns.add(name.isBlank()?"Column "+(c+1):name);}
   List<Map<String,String>> rows=new ArrayList<>();
   for(int r=header.getRowNum()+1;r<=sheet.getLastRowNum();r++){Row excelRow=sheet.getRow(r);if(excelRow==null)continue;Map<String,String> row=new LinkedHashMap<>();boolean any=false;for(int c=0;c<columns.size();c++){String value=fmt.formatCellValue(excelRow.getCell(c)).trim();row.put(columns.get(c),value);any|=!value.isBlank();}if(any)rows.add(row);}
   return new ParsedLeadFile(columns,rows);
  }
 }
}
