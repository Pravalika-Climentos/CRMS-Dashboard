package com.example.Email.Service;
import java.nio.file.Path;
public record EmailAttachmentDownload(Path path, String filename, String contentType) {}
