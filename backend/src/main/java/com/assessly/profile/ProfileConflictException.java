package com.assessly.profile;

import com.assessly.shared.ApiException;
import org.springframework.http.HttpStatus;

public class ProfileConflictException extends ApiException {

    public ProfileConflictException(String code, String message) {
        super(HttpStatus.CONFLICT, code, message);
    }
}
