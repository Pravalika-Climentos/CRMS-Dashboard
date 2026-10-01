package com.example.LeadImport.Parser;
import com.example.LeadImport.Entity.LeadImportFileType;
import org.springframework.web.multipart.MultipartFile;
import java.io.IOException;
public interface LeadFileParser { boolean supports(LeadImportFileType type); ParsedLeadFile parse(MultipartFile file) throws IOException; }
