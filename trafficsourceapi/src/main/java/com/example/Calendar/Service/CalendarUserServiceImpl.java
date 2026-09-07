package com.example.Calendar.Service;

import com.example.CRM.Entity.User;
import com.example.CRM.Repository.UserRepository;
import com.example.Calendar.DTO.Response.CalendarUserResponse;
import com.example.Common.DTO.Response.PageResponse;
import com.example.Common.Service.CurrentUserService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarUserServiceImpl
        implements CalendarUserService {

    private final UserRepository userRepository;

    private final CurrentUserService currentUserService;

    @Override
    public PageResponse<CalendarUserResponse> searchUsers(
            String search,
            int page,
            int size
    ) {
        if (page < 0) {
            throw new IllegalArgumentException(
                    "Page cannot be negative."
            );
        }

        if (size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Page size must be between 1 and 100."
            );
        }

        String normalizedSearch =
                search == null
                        ? ""
                        : search.trim();

        if (normalizedSearch.length() > 150) {
            throw new IllegalArgumentException(
                    "Search cannot exceed 150 characters."
            );
        }

        Long currentUserId =
                currentUserService.getCurrentUserId();

        Pageable pageable =
                PageRequest.of(
                        page,
                        size
                );

        Page<User> result =
                userRepository
                        .searchActiveCalendarUsers(
                                normalizedSearch,
                                currentUserId,
                                pageable
                        );

        List<CalendarUserResponse> responses =
                result.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return new PageResponse<>(
                responses,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    private CalendarUserResponse toResponse(
            User user
    ) {
        return new CalendarUserResponse(
                String.valueOf(user.getUserId()),
                user.getFullName(),
                user.getEmail(),
                user.getDesignation(),
                user.getRole(),
                user.getAvatar()
        );
    }
}