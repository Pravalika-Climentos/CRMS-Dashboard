package com.example.Email.DTO.Response;
public record EmailFolderCountsResponse(Long inbox, Long unread, Long sent, Long drafts,
    Long starred, Long important, Long archive, Long spam, Long trash) {
    public EmailFolderCountsResponse {
        inbox=n(inbox); unread=n(unread); sent=n(sent); drafts=n(drafts); starred=n(starred);
        important=n(important); archive=n(archive); spam=n(spam); trash=n(trash);
    }
    private static long n(Long value) { return value == null ? 0L : value; }
}
