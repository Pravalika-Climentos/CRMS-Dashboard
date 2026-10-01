package com.example.LeadImport.Parser;
import java.util.List;
import java.util.Map;
public record ParsedLeadFile(List<String> columns,List<Map<String,String>> rows){}
