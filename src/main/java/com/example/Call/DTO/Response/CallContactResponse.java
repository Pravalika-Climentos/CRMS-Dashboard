package com.example.Call.DTO.Response;

public record CallContactResponse(Long id, String email, String phone, String name,
                                  String avatar, String preferred, String preview,
                                  String time, String status) {}
