package com.example.Dashboard_Data.DTO;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class DashboardDataAdminDtos {
    private DashboardDataAdminDtos() {}

    public record Option(Long id, String label) {}
    public record Lookups(List<Option> users, List<Option> companies, List<Option> contacts,
                          List<Option> products, List<Option> stages, List<Option> sources) {}

    public record TargetRequest(@NotNull LocalDate targetMonth,
                                @NotNull @DecimalMin("0.00") BigDecimal targetAmount,
                                @Pattern(regexp = "[A-Z]{3}") String currencyCode) {}
    public record TargetRow(Long id, LocalDate targetMonth, BigDecimal targetAmount, String currencyCode) {}

    public record TransactionRequest(Long dealId, @NotNull Long productId, @NotNull Long salesUserId,
                                     @NotNull LocalDate transactionDate, @NotNull @Min(1) Integer quantity,
                                     @NotNull @DecimalMin("0.00") BigDecimal unitPrice,
                                     @NotNull @DecimalMin("0.00") BigDecimal costAmount,
                                     @NotBlank String paymentStatus) {}
    public record TransactionRow(Long id, Long dealId, String deal, Long productId, String product,
                                 Long salesUserId, String salesUser, LocalDate transactionDate, Integer quantity,
                                 BigDecimal unitPrice, BigDecimal totalAmount, BigDecimal costAmount, String paymentStatus) {}

    public record DealRequest(Long leadId, @NotNull Long companyId, Long contactId, @NotNull Long stageId,
                              @NotNull Long ownerUserId, @NotBlank @Size(max=180) String dealName,
                              @NotNull @DecimalMin("0.00") BigDecimal dealValue,
                              @NotNull @DecimalMin("0.00") @DecimalMax("100.00") BigDecimal probability,
                              LocalDate expectedCloseDate, @NotBlank String status, LocalDate wonDate,
                              @Size(max=255) String lostReason) {}
    public record DealRow(Long id, String name, Long companyId, String company, Long contactId, String contact,
                          Long stageId, String stage, Long ownerUserId, String owner, BigDecimal value,
                          BigDecimal probability, LocalDate expectedCloseDate, String status, LocalDate wonDate,
                          String lostReason, Long leadId) {}

    public record LeadRequest(Long companyId, Long contactId, Long sourceId, Long assignedUserId,
                              @NotBlank @Size(max=150) String leadName, @Email String email,
                              @Size(max=30) String phone, @NotBlank String status,
                              @NotNull @DecimalMin("0.00") BigDecimal estimatedValue, @NotNull Boolean converted) {}
    public record LeadRow(Long id, String name, Long companyId, String company, Long contactId, String contact,
                          Long sourceId, String source, Long assignedUserId, String assignedUser, String email,
                          String phone, String status, BigDecimal estimatedValue, Boolean converted) {}
}
