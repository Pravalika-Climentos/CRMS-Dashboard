package com.example.Dashboard_Data.Controller;

import com.example.Dashboard_Data.DTO.DashboardDataAdminDtos.*;
import com.example.Dashboard_Data.Service.DashboardDataAdminService;
import com.example.Dashboard_Data.Service.DashboardDataOtpService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/dashboard-data")
@RequiredArgsConstructor
public class DashboardDataAdminController {
    private final DashboardDataAdminService service;
    private final DashboardDataOtpService otp;
    @PostMapping("/otp/send") public DashboardDataOtpService.OtpSent sendOtp(@RequestBody OtpRequest r){return otp.send(r.email());}
    @PostMapping("/otp/verify") public DashboardDataOtpService.OtpVerified verifyOtp(@RequestBody OtpVerifyRequest r){return otp.verify(r.email(),r.code());}
    @GetMapping("/lookups") public Lookups lookups(@RequestHeader("X-Dashboard-Verification") String token){verified(token);return service.lookups();}
    @GetMapping("/sales-targets") public List<TargetRow> targets(@RequestHeader("X-Dashboard-Verification") String token){verified(token);return service.targetRows();}
    @PostMapping("/sales-targets") public TargetRow createTarget(@RequestHeader("X-Dashboard-Verification") String token,@Valid @RequestBody TargetRequest r){verified(token);return service.saveTarget(null,r);}
    @PutMapping("/sales-targets/{id}") public TargetRow updateTarget(@RequestHeader("X-Dashboard-Verification") String token,@PathVariable Long id,@Valid @RequestBody TargetRequest r){verified(token);return service.saveTarget(id,r);}
    @DeleteMapping("/sales-targets/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteTarget(@RequestHeader("X-Dashboard-Verification") String token,@PathVariable Long id){verified(token);service.deleteTarget(id);}
    @GetMapping("/transactions") public List<TransactionRow> transactions(@RequestHeader("X-Dashboard-Verification") String token){verified(token);return service.transactionRows();}
    @PostMapping("/transactions") public TransactionRow createTransaction(@RequestHeader("X-Dashboard-Verification") String token,@Valid @RequestBody TransactionRequest r){verified(token);return service.saveTransaction(null,r);}
    @PutMapping("/transactions/{id}") public TransactionRow updateTransaction(@RequestHeader("X-Dashboard-Verification") String token,@PathVariable Long id,@Valid @RequestBody TransactionRequest r){verified(token);return service.saveTransaction(id,r);}
    @DeleteMapping("/transactions/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteTransaction(@RequestHeader("X-Dashboard-Verification") String token,@PathVariable Long id){verified(token);service.deleteTransaction(id);}
    @GetMapping("/deals") public List<DealRow> deals(@RequestHeader("X-Dashboard-Verification") String token){verified(token);return service.dealRows();}
    @PostMapping("/deals") public DealRow createDeal(@RequestHeader("X-Dashboard-Verification") String token,@Valid @RequestBody DealRequest r){verified(token);return service.saveDeal(null,r);}
    @PutMapping("/deals/{id}") public DealRow updateDeal(@RequestHeader("X-Dashboard-Verification") String token,@PathVariable Long id,@Valid @RequestBody DealRequest r){verified(token);return service.saveDeal(id,r);}
    @DeleteMapping("/deals/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteDeal(@RequestHeader("X-Dashboard-Verification") String token,@PathVariable Long id){verified(token);service.deleteDeal(id);}
    @GetMapping("/leads") public List<LeadRow> leads(@RequestHeader("X-Dashboard-Verification") String token){verified(token);return service.leadRows();}
    @PostMapping("/leads") public LeadRow createLead(@RequestHeader("X-Dashboard-Verification") String token,@Valid @RequestBody LeadRequest r){verified(token);return service.saveLead(null,r);}
    @PutMapping("/leads/{id}") public LeadRow updateLead(@RequestHeader("X-Dashboard-Verification") String token,@PathVariable Long id,@Valid @RequestBody LeadRequest r){verified(token);return service.saveLead(id,r);}
    @DeleteMapping("/leads/{id}") @ResponseStatus(HttpStatus.NO_CONTENT) public void deleteLead(@RequestHeader("X-Dashboard-Verification") String token,@PathVariable Long id){verified(token);service.deleteLead(id);}

    private void verified(String token){otp.requireVerified(token);}
    public record OtpRequest(String email){}
    public record OtpVerifyRequest(String email,String code){}

    @ExceptionHandler(DataIntegrityViolationException.class)
    ResponseEntity<Map<String,String>> constraint(DataIntegrityViolationException ignored){return ResponseEntity.status(HttpStatus.CONFLICT).body(Map.of("message","This record is used by other CRM data and cannot be deleted."));}
    @ExceptionHandler(ResponseStatusException.class)
    ResponseEntity<Map<String,String>> status(ResponseStatusException error){return ResponseEntity.status(error.getStatusCode()).body(Map.of("message",error.getReason()==null?"Request could not be completed.":error.getReason()));}
}
