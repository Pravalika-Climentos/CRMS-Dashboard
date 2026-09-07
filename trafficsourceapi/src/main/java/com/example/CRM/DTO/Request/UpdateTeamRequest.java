package com.example.CRM.DTO.Request;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonSetter;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public class UpdateTeamRequest {

    @NotNull(message = "Expected version is required.")
    @PositiveOrZero(
        message = "Expected version cannot be negative."
    )
    private Long expectedVersion;

    @Size(
        max = 150,
        message = "Team name cannot exceed 150 characters."
    )
    private String name;

    @Size(
        max = 1000,
        message = "Description cannot exceed 1000 characters."
    )
    private String description;

    /*
     * These flags distinguish:
     *
     * Field omitted:
     *     leave the current value unchanged.
     *
     * "description": null:
     *     clear the current description.
     */
    @JsonIgnore
    private boolean nameProvided;

    @JsonIgnore
    private boolean descriptionProvided;

    public Long getExpectedVersion() {
        return expectedVersion;
    }

    public void setExpectedVersion(
            Long expectedVersion
    ) {
        this.expectedVersion = expectedVersion;
    }

    public String getName() {
        return name;
    }

    @JsonSetter("name")
    public void setName(String name) {
        this.name = name;
        this.nameProvided = true;
    }

    public String getDescription() {
        return description;
    }

    @JsonSetter("description")
    public void setDescription(
            String description
    ) {
        this.description = description;
        this.descriptionProvided = true;
    }

    @JsonIgnore
    public boolean isNameProvided() {
        return nameProvided;
    }

    @JsonIgnore
    public boolean isDescriptionProvided() {
        return descriptionProvided;
    }
}
