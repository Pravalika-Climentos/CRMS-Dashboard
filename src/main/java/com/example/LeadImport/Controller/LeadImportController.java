package com.example.LeadImport.Controller;
import com.example.Common.DTO.Response.PageResponse;
import com.example.LeadImport.DTO.*;
import com.example.LeadImport.Service.LeadImportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController @RequestMapping("/api/lead-imports") @RequiredArgsConstructor
@PreAuthorize("hasRole('MANAGER')")
public class LeadImportController {
 private final LeadImportService service;
 @PostMapping(value="/upload",consumes="multipart/form-data") @ResponseStatus(HttpStatus.CREATED)
 public LeadImportResponse upload(@RequestPart("file") MultipartFile file){return service.upload(file);}
 @GetMapping public PageResponse<LeadImportResponse> list(@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="20")int size){return service.list(page,size);}
 @GetMapping("/{id}") public LeadImportResponse get(@PathVariable Long id){return service.get(id);}
 @GetMapping("/{id}/preview") public PageResponse<LeadImportRowResponse> preview(@PathVariable Long id,@RequestParam(defaultValue="0")int page,@RequestParam(defaultValue="50")int size){return service.preview(id,page,size);}
 @PatchMapping("/{id}/rows/{rowId}") public LeadImportResponse updateRow(@PathVariable Long id,@PathVariable Long rowId,@Valid @RequestBody UpdateLeadImportRowRequest request){return service.updateRow(id,rowId,request.values());}
 @DeleteMapping("/{id}/rows/{rowId}") public LeadImportResponse deleteRow(@PathVariable Long id,@PathVariable Long rowId){return service.deleteRow(id,rowId);}
 @GetMapping("/{id}/progress") public LeadImportProgressResponse progress(@PathVariable Long id){return service.progress(id);}
 @PostMapping("/{id}/mapping") public LeadImportResponse mapping(@PathVariable Long id,@Valid @RequestBody ColumnMappingRequest request){return service.saveMapping(id,request.fieldMappings());}
 @PostMapping("/{id}/validate") public LeadImportResponse validate(@PathVariable Long id){return service.validate(id);}
 @PostMapping("/{id}/execute") public LeadImportResponse execute(@PathVariable Long id,@Valid @RequestBody ExecuteLeadImportRequest request){return service.executeWithTargets(id,request.salesExecutiveIds(),request.managerIds());}
 @DeleteMapping("/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void delete(@PathVariable Long id){service.delete(id);}
}
