package com.example.Dashboard_Data.Service;

import com.example.CRM.Entity.*;
import com.example.CRM.Repository.*;
import com.example.Dashboard_Data.DTO.DashboardDataAdminDtos.*;
import com.example.Dashboard_Data.Entity.SalesTarget;
import com.example.Dashboard_Data.Repository.SalesTargetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class DashboardDataAdminService {
    private final SalesTargetRepository targets;
    private final SalesTransactionRepository transactions;
    private final DealRepository deals;
    private final LeadRepository leads;
    private final ProductRepository products;
    private final UserRepository users;
    private final CompanyRepository companies;
    private final ContactRepository contacts;
    private final DealStageRepository stages;
    private final LeadSourceRepository sources;

    @Transactional(readOnly = true)
    public Lookups lookups() {
        return new Lookups(
                users.findAll(Sort.by("fullName")).stream().filter(u -> Boolean.TRUE.equals(u.getActive())).map(u -> new Option(u.getUserId(), u.getFullName())).toList(),
                companies.findAll(Sort.by("companyName")).stream().map(c -> new Option(c.getCompanyId(), c.getCompanyName())).toList(),
                contacts.findAll(Sort.by("firstName", "lastName")).stream().map(c -> new Option(c.getContactId(), c.getFirstName()+" "+c.getLastName())).toList(),
                products.findAll(Sort.by("productName")).stream().filter(p -> Boolean.TRUE.equals(p.getActive())).map(p -> new Option(p.getProductId(), p.getProductName())).toList(),
                stages.findAll(Sort.by("stageOrder")).stream().filter(s -> Boolean.TRUE.equals(s.getActive())).map(s -> new Option(s.getStageId(), s.getStageName())).toList(),
                sources.findAll(Sort.by("sourceName")).stream().filter(s -> Boolean.TRUE.equals(s.getActive())).map(s -> new Option(s.getSourceId(), s.getSourceName())).toList());
    }

    @Transactional(readOnly = true)
    public List<TargetRow> targetRows() { return targets.findAllByOrderByTargetMonthDesc().stream().map(this::targetRow).toList(); }
    public TargetRow saveTarget(Long id, TargetRequest request) {
        LocalDate month = request.targetMonth().withDayOfMonth(1);
        SalesTarget target = id == null ? targets.findByTargetMonth(month).orElseGet(SalesTarget::new) : required(targets.findById(id), "Sales target");
        targets.findByTargetMonth(month).filter(found -> !found.getTargetId().equals(target.getTargetId()))
                .ifPresent(found -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "A target already exists for this month."); });
        target.setTargetMonth(month); target.setTargetAmount(request.targetAmount());
        target.setCurrencyCode(request.currencyCode() == null ? "INR" : request.currencyCode().toUpperCase());
        return targetRow(targets.save(target));
    }
    public void deleteTarget(Long id) { targets.delete(required(targets.findById(id), "Sales target")); }

    @Transactional(readOnly = true)
    public List<TransactionRow> transactionRows() { return transactions.findAll(Sort.by(Sort.Direction.DESC,"transactionDate","transactionId")).stream().map(this::transactionRow).toList(); }
    public TransactionRow saveTransaction(Long id, TransactionRequest r) {
        SalesTransaction x = id == null ? new SalesTransaction() : required(transactions.findById(id), "Transaction");
        x.setDeal(r.dealId()==null?null:required(deals.findById(r.dealId()),"Deal"));
        x.setProduct(required(products.findById(r.productId()),"Product"));
        x.setSalesUser(required(users.findById(r.salesUserId()),"Sales executive"));
        x.setTransactionDate(r.transactionDate()); x.setQuantity(r.quantity()); x.setUnitPrice(r.unitPrice());
        x.setCostAmount(r.costAmount()); x.setPaymentStatus(normalize(r.paymentStatus()));
        return transactionRow(transactions.saveAndFlush(x));
    }
    public void deleteTransaction(Long id) { transactions.delete(required(transactions.findById(id),"Transaction")); }

    @Transactional(readOnly = true)
    public List<DealRow> dealRows() { return deals.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream().map(this::dealRow).toList(); }
    public DealRow saveDeal(Long id, DealRequest r) {
        Deal x=id==null?new Deal():required(deals.findById(id),"Deal");
        Company company=required(companies.findById(r.companyId()),"Company");
        Contact contact=r.contactId()==null?null:required(contacts.findById(r.contactId()),"Contact");
        if(contact!=null&&!contact.getCompany().getCompanyId().equals(company.getCompanyId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The selected contact does not belong to the selected company.");
        String status=normalize(r.status());
        if("WON".equals(status)&&r.wonDate()==null) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Won date is required when a deal is marked WON.");
        if("LOST".equals(status)&&(r.lostReason()==null||r.lostReason().isBlank())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"Lost reason is required when a deal is marked LOST.");
        x.setLead(r.leadId()==null?null:required(leads.findById(r.leadId()),"Lead"));
        x.setCompany(company);
        x.setContact(contact);
        x.setStage(required(stages.findById(r.stageId()),"Deal stage"));
        x.setOwner(required(users.findById(r.ownerUserId()),"Owner"));
        x.setDealName(r.dealName().trim()); x.setDealValue(r.dealValue()); x.setProbability(r.probability());
        x.setExpectedCloseDate(r.expectedCloseDate()); x.setStatus(status);
        x.setWonDate("WON".equals(x.getStatus())?r.wonDate():null); x.setLostReason("LOST".equals(x.getStatus())?r.lostReason():null);
        return dealRow(deals.save(x));
    }
    public void deleteDeal(Long id) {
        Deal deal=required(deals.findById(id),"Deal");
        if(!transactions.findByDeal_DealId(id).isEmpty()) throw new ResponseStatusException(HttpStatus.CONFLICT,"This deal cannot be deleted because it has sales transactions.");
        deals.delete(deal);
    }

    @Transactional(readOnly = true)
    public List<LeadRow> leadRows() { return leads.findAll(Sort.by(Sort.Direction.DESC,"createdAt")).stream().map(this::leadRow).toList(); }
    public LeadRow saveLead(Long id, LeadRequest r) {
        Lead x=id==null?new Lead():required(leads.findById(id),"Lead");
        Company company=r.companyId()==null?null:required(companies.findById(r.companyId()),"Company");
        Contact contact=r.contactId()==null?null:required(contacts.findById(r.contactId()),"Contact");
        if(contact!=null&&company!=null&&!contact.getCompany().getCompanyId().equals(company.getCompanyId())) throw new ResponseStatusException(HttpStatus.BAD_REQUEST,"The selected contact does not belong to the selected company.");
        if(contact!=null&&company==null) company=contact.getCompany();
        x.setCompany(company);
        x.setContact(contact);
        x.setSource(r.sourceId()==null?null:required(sources.findById(r.sourceId()),"Lead source"));
        x.setAssignedUser(r.assignedUserId()==null?null:required(users.findById(r.assignedUserId()),"Assigned user"));
        x.setLeadName(r.leadName().trim()); x.setEmail(blankToNull(r.email())); x.setPhone(blankToNull(r.phone()));
        x.setStatus(normalize(r.status())); x.setEstimatedValue(r.estimatedValue()); x.setConverted(r.converted());
        return leadRow(leads.save(x));
    }
    public void deleteLead(Long id) { leads.delete(required(leads.findById(id),"Lead")); }

    private TargetRow targetRow(SalesTarget x){return new TargetRow(x.getTargetId(),x.getTargetMonth(),x.getTargetAmount(),x.getCurrencyCode());}
    private TransactionRow transactionRow(SalesTransaction x){return new TransactionRow(x.getTransactionId(),id(x.getDeal()),x.getDeal()==null?null:x.getDeal().getDealName(),x.getProduct().getProductId(),x.getProduct().getProductName(),x.getSalesUser().getUserId(),x.getSalesUser().getFullName(),x.getTransactionDate(),x.getQuantity(),x.getUnitPrice(),x.getTotalAmount(),x.getCostAmount(),x.getPaymentStatus());}
    private DealRow dealRow(Deal x){return new DealRow(x.getDealId(),x.getDealName(),x.getCompany().getCompanyId(),x.getCompany().getCompanyName(),id(x.getContact()),x.getContact()==null?null:x.getContact().getFirstName()+" "+x.getContact().getLastName(),x.getStage().getStageId(),x.getStage().getStageName(),x.getOwner().getUserId(),x.getOwner().getFullName(),x.getDealValue(),x.getProbability(),x.getExpectedCloseDate(),x.getStatus(),x.getWonDate(),x.getLostReason(),id(x.getLead()));}
    private LeadRow leadRow(Lead x){return new LeadRow(x.getLeadId(),x.getLeadName(),id(x.getCompany()),x.getCompany()==null?null:x.getCompany().getCompanyName(),id(x.getContact()),x.getContact()==null?null:x.getContact().getFirstName()+" "+x.getContact().getLastName(),id(x.getSource()),x.getSource()==null?null:x.getSource().getSourceName(),id(x.getAssignedUser()),x.getAssignedUser()==null?null:x.getAssignedUser().getFullName(),x.getEmail(),x.getPhone(),x.getStatus(),x.getEstimatedValue(),x.getConverted());}
    private static Long id(Object x){if(x instanceof Deal v)return v.getDealId();if(x instanceof Contact v)return v.getContactId();if(x instanceof Lead v)return v.getLeadId();if(x instanceof Company v)return v.getCompanyId();if(x instanceof LeadSource v)return v.getSourceId();if(x instanceof User v)return v.getUserId();return null;}
    private static <T>T required(java.util.Optional<T> value,String label){return value.orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,label+" was not found."));}
    private static String normalize(String value){return value.trim().toUpperCase();}
    private static String blankToNull(String value){return value==null||value.isBlank()?null:value.trim();}
}
