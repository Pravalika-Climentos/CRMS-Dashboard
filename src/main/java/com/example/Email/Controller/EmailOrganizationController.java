package com.example.Email.Controller;
import com.example.Email.DTO.Request.*;import com.example.Email.DTO.Response.EmailOrganizerResponse;import com.example.Email.Service.EmailOrganizationService;
import jakarta.validation.Valid;import lombok.RequiredArgsConstructor;import org.springframework.http.*;import org.springframework.web.bind.annotation.*;import java.util.List;
@RestController @RequestMapping("/api/emails") @RequiredArgsConstructor
public class EmailOrganizationController {
 private final EmailOrganizationService service;
 @GetMapping("/labels") public List<EmailOrganizerResponse> labels(){return service.labels();}
 @PostMapping("/labels") public ResponseEntity<EmailOrganizerResponse> createLabel(@Valid @RequestBody EmailOrganizerRequest r){return ResponseEntity.status(201).body(service.createLabel(r));}
 @DeleteMapping("/labels/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteLabel(@PathVariable Long id){service.deleteLabel(id);}
 @PutMapping("/{messageId}/labels/{labelId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void addLabel(@PathVariable Long messageId,@PathVariable Long labelId){service.addLabel(messageId,labelId);}
 @DeleteMapping("/{messageId}/labels/{labelId}") @ResponseStatus(HttpStatus.NO_CONTENT) public void removeLabel(@PathVariable Long messageId,@PathVariable Long labelId){service.removeLabel(messageId,labelId);}
 @GetMapping("/custom-folders") public List<EmailOrganizerResponse> folders(){return service.folders();}
 @PostMapping("/custom-folders") public ResponseEntity<EmailOrganizerResponse> createFolder(@Valid @RequestBody EmailOrganizerRequest r){return ResponseEntity.status(201).body(service.createFolder(r));}
 @DeleteMapping("/custom-folders/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteFolder(@PathVariable Long id){service.deleteFolder(id);}
 @PatchMapping("/{messageId}/custom-folder") @ResponseStatus(HttpStatus.NO_CONTENT) public void move(@PathVariable Long messageId,@Valid @RequestBody MoveEmailRequest r){service.move(messageId,r);}
}
