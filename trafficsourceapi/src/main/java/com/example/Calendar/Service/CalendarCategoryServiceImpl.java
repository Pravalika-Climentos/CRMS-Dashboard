package com.example.Calendar.Service;

import com.example.Calendar.DTO.Request.CreateCalendarCategoryRequest;
import com.example.Calendar.DTO.Request.UpdateCalendarCategoryRequest;
import com.example.Calendar.DTO.Response.CalendarCategoryResponse;
import com.example.Calendar.Entity.CalendarCategory;
import com.example.Calendar.Repository.CalendarCategoryRepository;
import com.example.Common.DTO.Response.PageResponse;
import com.example.Common.Exception.ConflictException;
import com.example.Common.Exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarCategoryServiceImpl
        implements CalendarCategoryService {

    private final CalendarCategoryRepository
            categoryRepository;

//     private final UserRepository userRepository;

//     private final CurrentUserService currentUserService;

    @Override
    @Transactional
    public CalendarCategoryResponse create(
            CreateCalendarCategoryRequest request
    ) {
        // Long currentUserId =
        //         currentUserService.getCurrentUserId();

        // User owner =
        //         userRepository.findById(currentUserId)
        //                 .filter(user ->
        //                     Boolean.TRUE.equals(
        //                         user.getActive()
        //                     )
        //                 )
        //                 .orElseThrow(
        //                     () ->
        //                         new ResourceNotFoundException(
        //                             "Current user was not found or is inactive."
        //                         )
        //                 );

        String name =
                normalizeName(request.name());

        if (categoryRepository
                .existsByNameIgnoreCase(
                        name
                )) {
            throw new ConflictException(
                    "CATEGORY_NAME_ALREADY_EXISTS",
                    "You already have a category with this name."
            );
        }

        CalendarCategory category =
                new CalendarCategory();

       // category.setOwner(owner);
        category.setName(name);
        category.setColor(
                normalizeColor(request.color())
        );
        category.setActive(true);

        category =
                categoryRepository.saveAndFlush(
                        category
                );

        return toResponse(category);
    }

    @Override
    public PageResponse<CalendarCategoryResponse>
    getCategories(
            String search,
            boolean includeInactive,
            int page,
            int size
    ) {
        validatePagination(page, size);

        // Long currentUserId =
        //         currentUserService.getCurrentUserId();

        String normalizedSearch =
                search == null
                        ? ""
                        : search.trim();

        Pageable pageable =
                PageRequest.of(page, size);

        Page<CalendarCategory> result =
                includeInactive
                        ? categoryRepository
                         .findByNameContainingIgnoreCase(
                          normalizedSearch,
                          pageable
                        )
                        : categoryRepository
                         .findByActiveTrueAndNameContainingIgnoreCase(
                           normalizedSearch,
                           pageable
                        );

        List<CalendarCategoryResponse> items =
                result.getContent()
                        .stream()
                        .map(this::toResponse)
                        .toList();

        return new PageResponse<>(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages(),
                result.hasNext()
        );
    }

    @Override
    @Transactional
    public CalendarCategoryResponse update(
            Long categoryId,
            UpdateCalendarCategoryRequest request
    ) {
        // Long currentUserId =
        //         currentUserService.getCurrentUserId();

        CalendarCategory category =
                categoryRepository
                        .findByIdForUpdate(categoryId)
                        .orElseThrow(
                            () ->
                                new ResourceNotFoundException(
                                    "Calendar category not found."
                                )
                        );

        // if (!category.getOwner()
        //         .getUserId()
        //         .equals(currentUserId)) {
        //     throw new ForbiddenOperationException(
        //             "Only the category owner can update it."
        //     );
        // }

        if (!category.getVersion()
                .equals(request.getExpectedVersion())) {
            throw new ConflictException(
                    "CATEGORY_VERSION_CONFLICT",
                    "The category was updated elsewhere. "
                    + "Refresh and try again."
            );
        }

        if (!request.isNameProvided()
                && !request.isColorProvided()
                && !request.isActiveProvided()) {
            throw new IllegalArgumentException(
                    "At least one category field must be supplied."
            );
        }

        if (request.isNameProvided()) {
            String name =
                    normalizeName(request.getName());

            if (categoryRepository
                 .existsByNameIgnoreCaseAndCategoryIdNot(
                      name,
                      categoryId
                )) {
                throw new ConflictException(
                        "CATEGORY_NAME_ALREADY_EXISTS",
                        "You already have a category with this name."
                );
            }

            category.setName(name);
        }

        if (request.isColorProvided()) {
            if (request.getColor() == null) {
                throw new IllegalArgumentException(
                        "Category color cannot be null."
                );
            }

            category.setColor(
                    normalizeColor(
                        request.getColor()
                    )
            );
        }

        if (request.isActiveProvided()) {
            if (request.getActive() == null) {
                throw new IllegalArgumentException(
                        "Category active status cannot be null."
                );
            }

            category.setActive(
                    request.getActive()
            );
        }

        category =
                categoryRepository.saveAndFlush(
                        category
                );

        return toResponse(category);
    }

    private CalendarCategoryResponse toResponse(
            CalendarCategory category
    ) {
        return new CalendarCategoryResponse(
                String.valueOf(
                    category.getCategoryId()
                ),
                category.getName(),
                category.getColor(),
                Boolean.TRUE.equals(
                    category.getActive()
                ),
                category.getVersion(),
                category.getCreatedAt(),
                category.getUpdatedAt()
        );
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(
                    "Category name is required."
            );
        }

        return value.trim();
    }

    private String normalizeColor(String value) {
        if (value == null
                || !value.matches(
                    "^#[0-9A-Fa-f]{6}$"
                )) {
            throw new IllegalArgumentException(
                    "Color must use the format #RRGGBB."
            );
        }

        return value.toUpperCase(Locale.ROOT);
    }

    private void validatePagination(
            int page,
            int size
    ) {
        if (page < 0 || size < 1 || size > 100) {
            throw new IllegalArgumentException(
                    "Invalid pagination values."
            );
        }
    }
}