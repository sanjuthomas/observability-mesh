package com.srecatalog.authz.service;

import com.srecatalog.auth.SubjectExtractor;
import com.srecatalog.authz.web.dto.SubjectPayload;
import com.srecatalog.common.model.Subject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class EvaluateSubjectResolver {

    private final SubjectExtractor subjectExtractor;

    public EvaluateSubjectResolver(SubjectExtractor subjectExtractor) {
        this.subjectExtractor = subjectExtractor;
    }

    public Subject resolve(Subject serviceCaller, String onBehalfOfToken, SubjectPayload inlineSubject) {
        if (onBehalfOfToken != null && !onBehalfOfToken.isBlank()) {
            Subject user = subjectExtractor.fromOnBehalfOfToken(stripBearer(onBehalfOfToken));
            return new Subject(
                    user.userId(),
                    user.givenName(),
                    user.familyName(),
                    user.title(),
                    user.lob(),
                    user.roles(),
                    user.groups(),
                    user.supervisorId(),
                    user.coveringLobs(),
                    serviceCaller.userId(),
                    serviceCaller.roles());
        }
        if (inlineSubject == null) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "subject is required when X-On-Behalf-Of is not provided");
        }
        return inlineSubject.toSubject();
    }

    private static String stripBearer(String token) {
        return token.startsWith("Bearer ") ? token.substring(7) : token;
    }
}
